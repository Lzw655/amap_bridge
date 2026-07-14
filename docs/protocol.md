# Amap Bridge BLE Protocol v1

## GATT layout

| Item | Value |
| --- | --- |
| Advertised name | `AmapBridge-ESP32` |
| Service | `7A5A0001-6C8D-4F5A-9C2E-3B9E0B2F4A10` |
| Navigation RX | `7A5A0002-6C8D-4F5A-9C2E-3B9E0B2F4A10` (`Write`) |
| Status TX | `7A5A0003-6C8D-4F5A-9C2E-3B9E0B2F4A10` (`Notify`) |

Android 是 BLE Central/GATT Client，ESP32 是 BLE Peripheral/GATT Server。Android 请求 MTU 247，单个 UTF-8 JSON 消息不得超过 244 字节，不进行 GATT 层分片。

## Navigation message

```json
{"v":1,"seq":42,"state":"active","man":"right","dist":300,"remain":8600,"duration":1080,"eta":"14:30","speed":36,"limit":60,"road":"人民路"}
```

- `v`：协议版本，固定为 `1`。
- `seq`：会话内递增的非负整数，用于 ACK 对应。
- `state`：当前固定为 `active`，为后续暂停/结束状态预留。
- `man`：`straight`、`left`、`right`、`slight_left`、`slight_right`、`sharp_left`、`sharp_right`、`u_turn`、`roundabout`、`arrive`、`merge`、`exit` 或 `unknown`。
- `dist`：可选，非负整数，单位米。
- `remain`：可选，总剩余距离，单位米。
- `duration`：可选，剩余时间，单位秒。
- `eta`：可选，手机本地预计到达时间，格式 `HH:mm`。
- `speed`：可选，当前速度，单位 km/h。
- `limit`：可选，限速，单位 km/h。
- `road`：可选，道路名称。
- `raw`：仅动作无法识别时发送，内容为裁剪后的原始高德通知。

发送端按照 UTF-8 字节边界裁剪 `raw`，再裁剪 `road`，保证消息不超过 244 字节。

## Status message

成功：`{"v":1,"ack":42,"ok":true}`

失败：`{"v":1,"ack":42,"ok":false,"err":"bad_version"}`

错误码包括 `too_long`、`bad_json`、`bad_version`、`bad_seq`、`bad_state` 和 `bad_maneuver`。GATT Write 成功只代表链路写入成功，应用层必须以 Status TX 的 ACK/NACK 为准。
