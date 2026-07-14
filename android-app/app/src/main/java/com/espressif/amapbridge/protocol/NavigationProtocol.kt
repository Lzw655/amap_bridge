package com.espressif.amapbridge.protocol

import com.espressif.amapbridge.navigation.Maneuver
import com.espressif.amapbridge.navigation.NavigationInfo
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

object NavigationProtocol {
    const val DEVICE_NAME = "AmapBridge-ESP32"
    const val VERSION = 1
    const val REQUESTED_MTU = 247
    const val MAX_PAYLOAD_BYTES = 244

    val SERVICE_UUID: UUID = UUID.fromString("7a5a0001-6c8d-4f5a-9c2e-3b9e0b2f4a10")
    val NAVIGATION_RX_UUID: UUID = UUID.fromString("7a5a0002-6c8d-4f5a-9c2e-3b9e0b2f4a10")
    val STATUS_TX_UUID: UUID = UUID.fromString("7a5a0003-6c8d-4f5a-9c2e-3b9e0b2f4a10")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun encode(info: NavigationInfo, sequence: Long, state: String = "active"): ByteArray {
        var road = info.road
        var raw = info.raw.takeIf { info.maneuver == Maneuver.UNKNOWN }

        while (true) {
            val json = JSONObject().apply {
                put("v", VERSION)
                put("seq", sequence)
                put("state", state)
                put("man", info.maneuver.wireValue)
                info.distanceMeters?.let { put("dist", it) }
                info.remainingDistanceMeters?.let { put("remain", it) }
                info.remainingDurationSeconds?.let { put("duration", it) }
                info.eta?.let { put("eta", it) }
                info.currentSpeedKph?.let { put("speed", it) }
                info.speedLimitKph?.let { put("limit", it) }
                road?.let { value -> if (value.isNotEmpty()) put("road", value) }
                raw?.let { value -> if (value.isNotEmpty()) put("raw", value) }
            }.toString()
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size <= MAX_PAYLOAD_BYTES) return bytes

            when {
                !raw.isNullOrEmpty() -> raw = raw?.dropLastCodePoint()
                !road.isNullOrEmpty() -> road = road?.dropLastCodePoint()
                else -> error("Navigation payload cannot fit in $MAX_PAYLOAD_BYTES bytes")
            }
        }
    }

    fun parseAck(value: ByteArray): Ack? = runCatching {
        val json = JSONObject(value.toString(StandardCharsets.UTF_8))
        if (json.optInt("v", -1) != VERSION || !json.has("ack")) return@runCatching null
        Ack(
            sequence = json.getLong("ack"),
            ok = json.optBoolean("ok", false),
            error = json.optString("err").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    data class Ack(val sequence: Long, val ok: Boolean, val error: String?)

    private fun String.dropLastCodePoint(): String {
        if (isEmpty()) return this
        val count = Character.charCount(codePointBefore(length))
        return dropLast(count)
    }
}
