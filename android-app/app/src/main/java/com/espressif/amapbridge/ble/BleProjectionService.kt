package com.espressif.amapbridge.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.espressif.amapbridge.MainActivity
import com.espressif.amapbridge.R
import com.espressif.amapbridge.navigation.NavigationInfo
import com.espressif.amapbridge.protocol.NavigationProtocol
import com.espressif.amapbridge.runtime.AppRuntime
import com.espressif.amapbridge.runtime.BleDeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("MissingPermission")
class BleProjectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val sequence = AtomicLong(0)
    private val pendingWrites = ArrayDeque<Pair<Long, ByteArray>>()
    private val foundDevices = linkedMapOf<String, BleDeviceItem>()

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val adapter get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null
    private var navigationRx: BluetoothGattCharacteristic? = null
    private var statusTx: BluetoothGattCharacteristic? = null
    private var writeInProgress = false
    private var sessionActive = false
    private var selectedAddress: String? = null
    private var reconnectAttempt = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scope.launch {
            AppRuntime.navigation.collectLatest(::queueNavigation)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_ADDRESS)?.let(::connect)
            ACTION_SCAN -> startScan()
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        closeGatt()
        scope.cancel()
        AppRuntime.resetSession()
        super.onDestroy()
    }

    private fun startSession() {
        if (!hasBluetoothPermissions()) {
            AppRuntime.log("缺少附近设备权限，无法启动 BLE")
            stopSelf()
            return
        }
        sessionActive = true
        startForeground(NOTIFICATION_ID, buildNotification("正在扫描 ESP32"))
        AppRuntime.update { it.copy(projectionActive = true) }
        AppRuntime.log("导航投影已启动")
        startScan()
    }

    private fun stopSession() {
        AppRuntime.log("导航投影已停止")
        sessionActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startScan() {
        if (!sessionActive || !hasBluetoothPermissions()) return
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            AppRuntime.update { it.copy(scanning = false, connectionStatus = "蓝牙不可用") }
            AppRuntime.log("请打开手机蓝牙")
            return
        }

        foundDevices.clear()
        AppRuntime.update { it.copy(scanning = true, devices = emptyList(), connectionStatus = "正在扫描") }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(NavigationProtocol.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        handler.removeCallbacks(stopScanRunnable)
        handler.postDelayed(stopScanRunnable, SCAN_DURATION_MS)
        AppRuntime.log("开始扫描 ${NavigationProtocol.DEVICE_NAME}")
    }

    private fun stopScan() {
        handler.removeCallbacks(stopScanRunnable)
        if (hasBluetoothPermissions()) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
        AppRuntime.update { it.copy(scanning = false) }
    }

    private val stopScanRunnable = Runnable {
        stopScan()
        if (foundDevices.isEmpty()) {
            AppRuntime.update { it.copy(connectionStatus = "未发现设备") }
            AppRuntime.log("扫描结束，未发现 ESP32")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: NavigationProtocol.DEVICE_NAME
            foundDevices[result.device.address] = BleDeviceItem(name, result.device.address, result.rssi)
            AppRuntime.update { it.copy(devices = foundDevices.values.sortedByDescending(BleDeviceItem::rssi)) }
        }

        override fun onScanFailed(errorCode: Int) {
            AppRuntime.update { it.copy(scanning = false, connectionStatus = "扫描失败 ($errorCode)") }
            AppRuntime.log("BLE 扫描失败：$errorCode")
        }
    }

    private fun connect(address: String) {
        if (!sessionActive || !hasBluetoothPermissions()) return
        stopScan()
        selectedAddress = address
        reconnectAttempt = 0
        connectSelected()
    }

    private fun connectSelected() {
        val address = selectedAddress ?: return
        closeGatt()
        navigationRx = null
        statusTx = null
        AppRuntime.update {
            it.copy(selectedAddress = address, connectionStatus = "正在连接", transmissionStatus = "等待连接")
        }
        AppRuntime.log("连接 $address")
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            AppRuntime.log("无效的 BLE 地址：$address")
            return
        }
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempt = 0
                    AppRuntime.update { it.copy(connectionStatus = "已连接，发现服务") }
                    AppRuntime.log("BLE 已连接")
                    if (!gatt.requestMtu(NavigationProtocol.REQUESTED_MTU)) gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    navigationRx = null
                    statusTx = null
                    writeInProgress = false
                    pendingWrites.clear()
                    AppRuntime.update { it.copy(connectionStatus = "连接已断开", transmissionStatus = "等待重连") }
                    AppRuntime.log("BLE 已断开，状态码 $status")
                    if (sessionActive) scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            AppRuntime.log("协商 MTU=$mtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService? = gatt.getService(NavigationProtocol.SERVICE_UUID)
            navigationRx = service?.getCharacteristic(NavigationProtocol.NAVIGATION_RX_UUID)
            statusTx = service?.getCharacteristic(NavigationProtocol.STATUS_TX_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || navigationRx == null || statusTx == null) {
                AppRuntime.update { it.copy(connectionStatus = "协议不兼容") }
                AppRuntime.log("未找到 Amap Bridge GATT 服务或特征")
                gatt.disconnect()
                return
            }
            if (!enableStatusNotifications(gatt, statusTx!!)) {
                AppRuntime.log("无法订阅 ESP32 ACK 特征")
                gatt.disconnect()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != NavigationProtocol.CCCD_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markProtocolReady(gatt)
            } else {
                AppRuntime.log("ACK 订阅失败：$status")
                gatt.disconnect()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val sent = synchronized(pendingWrites) { pendingWrites.removeFirstOrNull() }
            writeInProgress = false
            if (status == BluetoothGatt.GATT_SUCCESS && sent != null) {
                AppRuntime.update { it.copy(transmissionStatus = "已发送 #${sent.first}，等待 ACK") }
            } else {
                AppRuntime.update { it.copy(transmissionStatus = "发送失败 ($status)") }
                AppRuntime.log("GATT 写入失败：$status")
            }
            writeNext()
        }

        @Deprecated("Used on Android 12 and earlier")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleStatus(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleStatus(value)
        }
    }

    private fun enableStatusNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) return false
        val descriptor = characteristic.getDescriptor(NavigationProtocol.CCCD_UUID) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun markProtocolReady(gatt: BluetoothGatt) {
        AppRuntime.update { it.copy(connectionStatus = "已连接", transmissionStatus = "等待导航") }
        AppRuntime.log("Amap Bridge 协议 v1 已就绪")
        updateForegroundNotification("已连接 ${gatt.device.address}")
        writeNext()
    }

    private fun queueNavigation(info: NavigationInfo) {
        if (!sessionActive) return
        val seq = sequence.incrementAndGet()
        val payload = NavigationProtocol.encode(info, seq)
        synchronized(pendingWrites) {
            if (pendingWrites.size >= MAX_WRITE_QUEUE) pendingWrites.removeFirst()
            pendingWrites.addLast(seq to payload)
        }
        AppRuntime.update { it.copy(transmissionStatus = "排队发送 #$seq") }
        handler.post(::writeNext)
    }

    private fun writeNext() {
        if (writeInProgress) return
        val characteristic = navigationRx ?: return
        val payload = synchronized(pendingWrites) { pendingWrites.firstOrNull()?.second } ?: return
        val currentGatt = gatt ?: return
        writeInProgress = true
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            writeInProgress = false
            AppRuntime.update { it.copy(transmissionStatus = "无法启动发送") }
        }
    }

    private fun handleStatus(value: ByteArray) {
        val ack = NavigationProtocol.parseAck(value)
        if (ack == null) {
            AppRuntime.log("收到无法解析的 ESP32 状态")
            return
        }
        if (ack.ok) {
            AppRuntime.update { it.copy(transmissionStatus = "ESP32 已确认 #${ack.sequence}") }
            AppRuntime.log("ESP32 ACK #${ack.sequence}")
        } else {
            AppRuntime.update { it.copy(transmissionStatus = "ESP32 拒绝 #${ack.sequence}: ${ack.error}") }
            AppRuntime.log("ESP32 NACK #${ack.sequence}: ${ack.error}")
        }
    }

    private fun scheduleReconnect() {
        val delays = longArrayOf(1, 2, 4, 8, 16, 30)
        val seconds = delays[reconnectAttempt.coerceAtMost(delays.lastIndex)]
        reconnectAttempt++
        AppRuntime.update { it.copy(connectionStatus = "${seconds} 秒后重连") }
        handler.postDelayed({ if (sessionActive) connectSelected() }, seconds * 1_000)
    }

    private fun closeGatt() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.projection_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.projection_notification_title))
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(content))
    }

    companion object {
        const val ACTION_START = "com.espressif.amapbridge.START"
        const val ACTION_CONNECT = "com.espressif.amapbridge.CONNECT"
        const val ACTION_SCAN = "com.espressif.amapbridge.SCAN"
        const val ACTION_STOP = "com.espressif.amapbridge.STOP"
        const val EXTRA_ADDRESS = "address"

        private const val CHANNEL_ID = "navigation_projection"
        private const val NOTIFICATION_ID = 1001
        private const val SCAN_DURATION_MS = 15_000L
        private const val MAX_WRITE_QUEUE = 8
    }
}
