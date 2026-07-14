#include <assert.h>
#include <inttypes.h>
#include <string.h>

#include "cJSON.h"
#include "esp_err.h"
#include "esp_log.h"
#include "nvs_flash.h"

#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "os/os_mbuf.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#define DEVICE_NAME "AmapBridge-ESP32"
#define PROTOCOL_VERSION 1
#define MAX_PAYLOAD_BYTES 244

static const char *TAG = "amap_bridge";
static uint8_t own_addr_type;
static uint16_t connection_handle = BLE_HS_CONN_HANDLE_NONE;
static uint16_t status_value_handle;

static const ble_uuid128_t service_uuid = BLE_UUID128_INIT(
    0x10, 0x4a, 0x2f, 0x0b, 0x9e, 0x3b, 0x2e, 0x9c,
    0x5a, 0x4f, 0x8d, 0x6c, 0x01, 0x00, 0x5a, 0x7a
);
static const ble_uuid128_t navigation_rx_uuid = BLE_UUID128_INIT(
    0x10, 0x4a, 0x2f, 0x0b, 0x9e, 0x3b, 0x2e, 0x9c,
    0x5a, 0x4f, 0x8d, 0x6c, 0x02, 0x00, 0x5a, 0x7a
);
static const ble_uuid128_t status_tx_uuid = BLE_UUID128_INIT(
    0x10, 0x4a, 0x2f, 0x0b, 0x9e, 0x3b, 0x2e, 0x9c,
    0x5a, 0x4f, 0x8d, 0x6c, 0x03, 0x00, 0x5a, 0x7a
);

static void start_advertising(void);

static void send_ack(int64_t sequence, bool ok, const char *error)
{
    if (connection_handle == BLE_HS_CONN_HANDLE_NONE) {
        return;
    }

    cJSON *root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "v", PROTOCOL_VERSION);
    cJSON_AddNumberToObject(root, "ack", (double)sequence);
    cJSON_AddBoolToObject(root, "ok", ok);
    if (!ok && error != NULL) {
        cJSON_AddStringToObject(root, "err", error);
    }

    char *payload = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (payload == NULL) {
        ESP_LOGE(TAG, "Failed to allocate ACK payload");
        return;
    }

    struct os_mbuf *om = ble_hs_mbuf_from_flat(payload, strlen(payload));
    int rc = om == NULL ? BLE_HS_ENOMEM : ble_gatts_notify_custom(connection_handle, status_value_handle, om);
    if (rc != 0) {
        ESP_LOGW(TAG, "Failed to notify ACK, rc=%d", rc);
    }
    cJSON_free(payload);
}

static bool maneuver_is_valid(const char *maneuver)
{
    static const char *valid[] = {
        "straight", "left", "right", "slight_left", "slight_right",
        "sharp_left", "sharp_right", "u_turn", "roundabout", "arrive",
        "merge", "exit", "unknown",
    };
    for (size_t i = 0; i < sizeof(valid) / sizeof(valid[0]); ++i) {
        if (strcmp(maneuver, valid[i]) == 0) {
            return true;
        }
    }
    return false;
}

static void handle_navigation_json(const char *payload, size_t length)
{
    cJSON *root = cJSON_ParseWithLength(payload, length);
    if (root == NULL) {
        send_ack(0, false, "bad_json");
        ESP_LOGW(TAG, "Rejected malformed JSON");
        return;
    }

    cJSON *version = cJSON_GetObjectItemCaseSensitive(root, "v");
    cJSON *sequence = cJSON_GetObjectItemCaseSensitive(root, "seq");
    cJSON *state = cJSON_GetObjectItemCaseSensitive(root, "state");
    cJSON *maneuver = cJSON_GetObjectItemCaseSensitive(root, "man");
    cJSON *distance = cJSON_GetObjectItemCaseSensitive(root, "dist");
    cJSON *road = cJSON_GetObjectItemCaseSensitive(root, "road");
    cJSON *raw = cJSON_GetObjectItemCaseSensitive(root, "raw");
    int64_t seq = cJSON_IsNumber(sequence) ? (int64_t)sequence->valuedouble : 0;
    const char *error = NULL;

    if (!cJSON_IsNumber(version) || version->valueint != PROTOCOL_VERSION) {
        error = "bad_version";
    } else if (!cJSON_IsNumber(sequence) || sequence->valuedouble < 0) {
        error = "bad_seq";
    } else if (!cJSON_IsString(state) || strcmp(state->valuestring, "active") != 0) {
        error = "bad_state";
    } else if (!cJSON_IsString(maneuver) || !maneuver_is_valid(maneuver->valuestring)) {
        error = "bad_maneuver";
    }

    if (error != NULL) {
        send_ack(seq, false, error);
        ESP_LOGW(TAG, "Rejected message #%" PRId64 ": %s", seq, error);
        cJSON_Delete(root);
        return;
    }

    ESP_LOGI(
        TAG,
        "NAV #%" PRId64 " maneuver=%s distance=%d road=%s raw=%s",
        seq,
        maneuver->valuestring,
        cJSON_IsNumber(distance) ? distance->valueint : -1,
        cJSON_IsString(road) ? road->valuestring : "-",
        cJSON_IsString(raw) ? raw->valuestring : "-"
    );
    send_ack(seq, true, NULL);
    cJSON_Delete(root);
}

static int gatt_access_callback(
    uint16_t conn_handle,
    uint16_t attr_handle,
    struct ble_gatt_access_ctxt *ctxt,
    void *arg
)
{
    (void)conn_handle;
    (void)attr_handle;
    (void)arg;

    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) {
        return 0;
    }

    uint16_t length = OS_MBUF_PKTLEN(ctxt->om);
    if (length > MAX_PAYLOAD_BYTES) {
        send_ack(0, false, "too_long");
        return 0;
    }

    char payload[MAX_PAYLOAD_BYTES + 1];
    int rc = os_mbuf_copydata(ctxt->om, 0, length, payload);
    if (rc != 0) {
        send_ack(0, false, "bad_json");
        return 0;
    }
    payload[length] = '\0';
    handle_navigation_json(payload, length);
    return 0;
}

static const struct ble_gatt_svc_def gatt_services[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &service_uuid.u,
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid = &navigation_rx_uuid.u,
                .access_cb = gatt_access_callback,
                .flags = BLE_GATT_CHR_F_WRITE,
            },
            {
                .uuid = &status_tx_uuid.u,
                .access_cb = gatt_access_callback,
                .val_handle = &status_value_handle,
                .flags = BLE_GATT_CHR_F_NOTIFY,
            },
            { 0 },
        },
    },
    { 0 },
};

static int gap_event_callback(struct ble_gap_event *event, void *arg)
{
    (void)arg;
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0) {
            connection_handle = event->connect.conn_handle;
            ESP_LOGI(TAG, "Android connected, handle=%u", connection_handle);
        } else {
            ESP_LOGW(TAG, "Connection failed, status=%d", event->connect.status);
            start_advertising();
        }
        return 0;

    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI(TAG, "Disconnected, reason=%d", event->disconnect.reason);
        connection_handle = BLE_HS_CONN_HANDLE_NONE;
        start_advertising();
        return 0;

    case BLE_GAP_EVENT_ADV_COMPLETE:
        start_advertising();
        return 0;

    case BLE_GAP_EVENT_MTU:
        ESP_LOGI(TAG, "MTU updated to %u", event->mtu.value);
        return 0;

    default:
        return 0;
    }
}

static void start_advertising(void)
{
    struct ble_hs_adv_fields fields = { 0 };
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = (ble_uuid128_t *)&service_uuid;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "Failed to set advertising fields, rc=%d", rc);
        return;
    }

    struct ble_hs_adv_fields response = { 0 };
    response.name = (uint8_t *)DEVICE_NAME;
    response.name_len = strlen(DEVICE_NAME);
    response.name_is_complete = 1;
    rc = ble_gap_adv_rsp_set_fields(&response);
    if (rc != 0) {
        ESP_LOGE(TAG, "Failed to set scan response, rc=%d", rc);
        return;
    }

    struct ble_gap_adv_params params = { 0 };
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(own_addr_type, NULL, BLE_HS_FOREVER, &params, gap_event_callback, NULL);
    if (rc != 0) {
        ESP_LOGE(TAG, "Failed to start advertising, rc=%d", rc);
    } else {
        ESP_LOGI(TAG, "Advertising as %s", DEVICE_NAME);
    }
}

static void on_stack_reset(int reason)
{
    ESP_LOGW(TAG, "NimBLE stack reset, reason=%d", reason);
}

static void on_stack_sync(void)
{
    int rc = ble_hs_id_infer_auto(0, &own_addr_type);
    assert(rc == 0);
    start_advertising();
}

static void host_task(void *param)
{
    (void)param;
    ESP_LOGI(TAG, "NimBLE host task started");
    nimble_port_run();
    nimble_port_freertos_deinit();
}

void app_main(void)
{
    esp_err_t nvs_result = nvs_flash_init();
    if (nvs_result == ESP_ERR_NVS_NO_FREE_PAGES || nvs_result == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        nvs_result = nvs_flash_init();
    }
    ESP_ERROR_CHECK(nvs_result);
    ESP_ERROR_CHECK(nimble_port_init());

    ble_hs_cfg.reset_cb = on_stack_reset;
    ble_hs_cfg.sync_cb = on_stack_sync;
    ble_hs_cfg.store_status_cb = ble_store_util_status_rr;

    ble_svc_gap_init();
    ble_svc_gatt_init();
    ESP_ERROR_CHECK(ble_svc_gap_device_name_set(DEVICE_NAME));
    ESP_ERROR_CHECK(ble_gatts_count_cfg(gatt_services));
    ESP_ERROR_CHECK(ble_gatts_add_svcs(gatt_services));

    nimble_port_freertos_init(host_task);
}
