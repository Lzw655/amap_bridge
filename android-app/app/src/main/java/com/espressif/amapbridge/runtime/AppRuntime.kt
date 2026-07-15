package com.espressif.amapbridge.runtime

import com.espressif.amapbridge.navigation.NavigationInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BleDeviceItem(val name: String, val address: String, val rssi: Int)

data class AppUiState(
    val projectionActive: Boolean = false,
    val scanning: Boolean = false,
    val devices: List<BleDeviceItem> = emptyList(),
    val selectedAddress: String? = null,
    val connectionStatus: String = "未连接",
    val latestNavigation: NavigationInfo? = null,
    val transmissionStatus: String = "等待导航",
    val logs: List<String> = emptyList(),
)

object AppRuntime {
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()

    private val navigationEvents = MutableSharedFlow<NavigationInfo>(extraBufferCapacity = 32)
    val navigation = navigationEvents.asSharedFlow()

    fun publishNavigation(info: NavigationInfo, source: String) {
        mutableState.update { it.copy(latestNavigation = info) }
        navigationEvents.tryEmit(info)
        log("$source：${info.maneuver.displayName}${info.distanceMeters?.let { d -> " ${d}m" } ?: ""}")
    }

    fun update(transform: (AppUiState) -> AppUiState) = mutableState.update(transform)

    fun log(message: String) {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        mutableState.update { current ->
            current.copy(logs = (listOf("$timestamp  $message") + current.logs).take(60))
        }
    }

    fun clearLogs() {
        mutableState.update { it.copy(logs = emptyList()) }
    }

    fun resetSession() {
        mutableState.update {
            it.copy(
                projectionActive = false,
                scanning = false,
                selectedAddress = null,
                connectionStatus = "未连接",
                transmissionStatus = "等待导航",
            )
        }
    }
}
