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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.espressif.amapbridge.ble.BleProjectionService
import com.espressif.amapbridge.notification.AmapNotificationListenerService
import com.espressif.amapbridge.runtime.AppRuntime
import com.espressif.amapbridge.ui.ConnectionScreen
import com.espressif.amapbridge.ui.EspPreviewScreen
import com.espressif.amapbridge.ui.NavigationDetailsCard
import com.espressif.amapbridge.ui.NavigationSimulatorScreen

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
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            permissionVersion++
            if (hasRuntimePermissions()) startProjection()
        }
        LaunchedEffect(Unit) { AppRuntime.log("Amap Bridge 已启动") }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Amap Bridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("高德导航通知 → BLE → ESP32-S3", color = Color.DarkGray)
            }
            val tabs = listOf("连接调试", "导航模拟", "ESP 预览")
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                0 -> ConnectionScreen(
                    state = state,
                    notificationAccess = hasNotificationAccess(permissionVersion),
                    runtimePermissions = hasRuntimePermissions(permissionVersion),
                    onNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onRuntimePermissions = { permissionLauncher.launch(requiredPermissions()) },
                    onStart = {
                        if (hasRuntimePermissions()) startProjection()
                        else permissionLauncher.launch(requiredPermissions())
                    },
                    onScan = { sendServiceAction(BleProjectionService.ACTION_SCAN) },
                    onStop = { sendServiceAction(BleProjectionService.ACTION_STOP) },
                    onConnect = { sendServiceAction(BleProjectionService.ACTION_CONNECT, it) },
                )
                1 -> NavigationSimulatorScreen { AppRuntime.publishNavigation(it, "模拟导航") }
                else -> Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("ESP 320×240 预览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("按真实像素比例缩放，缺失字段显示 --。")
                    EspPreviewScreen(state.latestNavigation, state.connectionStatus)
                    NavigationDetailsCard(state)
                }
            }
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
}
