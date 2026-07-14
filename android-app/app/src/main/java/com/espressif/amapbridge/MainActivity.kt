package com.espressif.amapbridge

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.espressif.amapbridge.ble.BleProjectionService
import com.espressif.amapbridge.navigation.AmapNavigationParser
import com.espressif.amapbridge.notification.AmapNotificationListenerService
import com.espressif.amapbridge.runtime.AppRuntime
import com.espressif.amapbridge.runtime.AppUiState
import com.espressif.amapbridge.runtime.BleDeviceItem

class MainActivity : ComponentActivity() {
    private var permissionVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7FAF8)) {
                    AmapBridgeScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionVersion++
    }

    @Composable
    private fun AmapBridgeScreen() {
        val state by AppRuntime.state.collectAsState()
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            permissionVersion++
            if (hasRuntimePermissions()) startProjection()
        }

        LaunchedEffect(Unit) { AppRuntime.log("Amap Bridge 已启动") }
        val notificationAccess = hasNotificationAccess(permissionVersion)
        val runtimePermissions = hasRuntimePermissions(permissionVersion)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("Amap Bridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("高德导航通知 → BLE → ESP32-S3", color = Color.DarkGray)
            }

            item {
                SectionCard("准备状态") {
                    StatusRow("高德通知访问", notificationAccess)
                    StatusRow("附近设备权限", runtimePermissions)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!notificationAccess) {
                            OutlinedButton(onClick = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) { Text("授权通知访问") }
                        }
                        if (!runtimePermissions) {
                            OutlinedButton(onClick = { permissionLauncher.launch(requiredPermissions()) }) {
                                Text("授予设备权限")
                            }
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
                            Button(onClick = {
                                if (hasRuntimePermissions()) startProjection()
                                else permissionLauncher.launch(requiredPermissions())
                            }) { Text("启动投影") }
                        } else {
                            OutlinedButton(onClick = { sendServiceAction(BleProjectionService.ACTION_SCAN) }) {
                                Text(if (state.scanning) "扫描中…" else "重新扫描")
                            }
                            Button(onClick = { sendServiceAction(BleProjectionService.ACTION_STOP) }) {
                                Text("停止")
                            }
                        }
                    }
                }
            }

            if (state.devices.isNotEmpty()) {
                item { Text("发现的设备", fontWeight = FontWeight.SemiBold) }
                items(state.devices, key = BleDeviceItem::address) { device ->
                    DeviceCard(device, state.selectedAddress == device.address) {
                        sendServiceAction(BleProjectionService.ACTION_CONNECT, device.address)
                    }
                }
            }

            item {
                NavigationCard(state)
            }

            item {
                SectionCard("模拟导航") {
                    Text("不打开高德也可以验证解析和 BLE 发送链路。", color = Color.DarkGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { simulate("前方300米右转，进入人民路") }) { Text("右转 300m") }
                        OutlinedButton(onClick = { simulate("前方1.2公里进入环岛") }) { Text("环岛 1.2km") }
                    }
                }
            }

            item {
                SectionCard("诊断日志") {
                    if (state.logs.isEmpty()) Text("暂无日志", color = Color.Gray)
                    state.logs.take(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    @Composable
    private fun NavigationCard(state: AppUiState) {
        SectionCard("当前导航") {
            val nav = state.latestNavigation
            if (nav == null) {
                Text("等待高德导航通知", color = Color.Gray)
            } else {
                Text(nav.maneuver.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                nav.distanceMeters?.let { Text(formatDistance(it), style = MaterialTheme.typography.titleLarge) }
                nav.road?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                Text(if (nav.parsed) "已解析" else "未识别动作，已保留原文", color = if (nav.parsed) Color(0xFF0B7D5C) else Color(0xFF9A5B00))
                Text(nav.raw, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
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

    @Composable
    private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
            Text(if (enabled) "已授权" else "未授权", color = if (enabled) Color(0xFF0B7D5C) else Color(0xFFB3261E))
        }
    }

    private fun startProjection() {
        val intent = Intent(this, BleProjectionService::class.java).setAction(BleProjectionService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendServiceAction(action: String, address: String? = null) {
        val intent = Intent(this, BleProjectionService::class.java).setAction(action)
        address?.let { intent.putExtra(BleProjectionService.EXTRA_ADDRESS, it) }
        startService(intent)
    }

    private fun simulate(text: String) {
        AmapNavigationParser().parse(listOf(text))?.let { AppRuntime.publishNavigation(it, "模拟导航") }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun hasRuntimePermissions(refresh: Int = 0): Boolean {
        @Suppress("UNUSED_VARIABLE") val ignored = refresh
        return requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun hasNotificationAccess(refresh: Int = 0): Boolean {
        @Suppress("UNUSED_VARIABLE") val ignored = refresh
        val component = ComponentName(this, AmapNotificationListenerService::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.contains(component.flattenToString()) == true
        }
    }

    private fun formatDistance(meters: Int): String =
        if (meters >= 1_000) "%.1f km".format(meters / 1_000.0) else "$meters m"
}
