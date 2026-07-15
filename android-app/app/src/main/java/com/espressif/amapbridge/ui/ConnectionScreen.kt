package com.espressif.amapbridge.ui

import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.espressif.amapbridge.runtime.AppUiState
import com.espressif.amapbridge.runtime.BleDeviceItem

@Composable
fun ConnectionScreen(
    state: AppUiState,
    notificationAccess: Boolean,
    runtimePermissions: Boolean,
    onNotificationAccess: () -> Unit,
    onRuntimePermissions: () -> Unit,
    onStart: () -> Unit,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onConnect: (String) -> Unit,
    onClearLogs: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("连接调试", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("连接 AmapBridge-ESP32 并查看实时传输状态。")
        }
        item {
            SectionCard("准备状态") {
                StatusRow("高德通知访问", notificationAccess)
                StatusRow("附近设备权限", runtimePermissions)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!notificationAccess) {
                        OutlinedButton(onClick = onNotificationAccess) { Text("授权通知访问") }
                    }
                    if (!runtimePermissions) {
                        OutlinedButton(onClick = onRuntimePermissions) { Text("授予设备权限") }
                    }
                }
            }
        }
        item {
            SectionCard("导航投影") {
                Text("状态：${state.connectionStatus}")
                Text("传输：${state.transmissionStatus}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.projectionActive) {
                        Button(onClick = onStart) { Text("启动投影") }
                    } else {
                        OutlinedButton(onClick = onScan) { Text(if (state.scanning) "扫描中…" else "重新扫描") }
                        Button(onClick = onStop) { Text("停止") }
                    }
                }
            }
        }
        if (state.devices.isNotEmpty()) {
            item { Text("发现的设备", fontWeight = FontWeight.SemiBold) }
            items(state.devices, key = BleDeviceItem::address) { device ->
                DeviceCard(device, state.selectedAddress == device.address) { onConnect(device.address) }
            }
        }
        item { NavigationDetailsCard(state) }
        item {
            SectionCard("诊断日志") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("显示 ${minOf(state.logs.size, 20)} / ${state.logs.size} 条", color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = state.logs.isNotEmpty(),
                            onClick = {
                                clipboardManager.setText(AnnotatedString(state.logs.joinToString("\n")))
                                Toast.makeText(context, "已复制 ${state.logs.size} 条日志", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("复制全部")
                        }
                        OutlinedButton(enabled = state.logs.isNotEmpty(), onClick = onClearLogs) {
                            Text("清空")
                        }
                    }
                }
                if (state.logs.isEmpty()) {
                    Text("暂无日志", color = Color.Gray)
                } else {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.logs.take(20).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun NavigationDetailsCard(state: AppUiState) {
    SectionCard("当前导航") {
        val nav = state.latestNavigation
        if (nav == null) {
            Text("等待高德通知或模拟数据", color = Color.Gray)
        } else {
            Text(nav.maneuver.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("下一动作：${formatDistance(nav.distanceMeters)}")
            Text("道路：${nav.road ?: "--"}")
            Text("剩余：${formatDistance(nav.remainingDistanceMeters)} · ${formatDuration(nav.remainingDurationSeconds)}")
            Text("ETA：${nav.eta ?: "--:--"} · 速度：${nav.currentSpeedKph ?: "--"} km/h · 限速：${nav.speedLimitKph ?: "--"}")
            Text(
                if (nav.parsed) "已解析" else "未识别动作，已保留原文",
                color = if (nav.parsed) Color(0xFF0B7D5C) else Color(0xFF9A5B00),
            )
            Text(nav.raw, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            if (enabled) "已授权" else "未授权",
            color = if (enabled) Color(0xFF0B7D5C) else Color(0xFFB3261E),
        )
    }
}

@Composable
private fun DeviceCard(device: BleDeviceItem, selected: Boolean, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFDDF3EA) else Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onConnect) { Text(if (selected) "重连" else "连接") }
        }
    }
}
