package com.espressif.amapbridge.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.SystemClock
import com.espressif.amapbridge.navigation.AmapNavigationParser
import com.espressif.amapbridge.navigation.NotificationDeduplicator
import com.espressif.amapbridge.runtime.AppRuntime

class AmapNotificationListenerService : NotificationListenerService() {
    private val parser = AmapNavigationParser()
    private val deduplicator = NotificationDeduplicator()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != AMAP_PACKAGE) return

        val extras = sbn.notification.extras
        val parts = mutableListOf<String>().apply {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let { add(it) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { add(it) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { add(it) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString)
                ?.let { addAll(it) }
        }
        val info = parser.parse(parts) ?: return
        if (!deduplicator.shouldAccept(info.raw, SystemClock.elapsedRealtime())) return
        AppRuntime.publishNavigation(info, "高德通知")
    }

    override fun onListenerConnected() {
        AppRuntime.log("高德通知监听已连接")
    }

    override fun onListenerDisconnected() {
        AppRuntime.log("高德通知监听已断开")
    }

    companion object {
        const val AMAP_PACKAGE = "com.autonavi.minimap"
    }
}
