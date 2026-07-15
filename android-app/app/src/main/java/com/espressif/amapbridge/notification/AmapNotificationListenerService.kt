package com.espressif.amapbridge.notification

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.espressif.amapbridge.navigation.AmapNavigationParser
import com.espressif.amapbridge.navigation.NotificationDeduplicator
import com.espressif.amapbridge.runtime.AppRuntime

class AmapNotificationListenerService : NotificationListenerService() {
    private val parser = AmapNavigationParser()
    private val deduplicator = NotificationDeduplicator()
    private var lastTextKeySignature: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != AMAP_PACKAGE) return

        val extras = sbn.notification.extras
        val parts = mutableListOf<String>()
        val textKeys = mutableListOf<String>()
        val availableKeys = extras.keySet()
        val orderedKeys = buildList {
            STANDARD_TEXT_KEYS.filterTo(this) { it in availableKeys }
            availableKeys.filterNotTo(this) { it in STANDARD_TEXT_KEYS || it in IGNORED_TEXT_KEYS }.sort()
        }
        orderedKeys.forEach { key ->
            val sizeBefore = parts.size
            appendTextValues(extras[key], parts)
            if (parts.size > sizeBefore) textKeys += key
        }
        val textKeySignature = textKeys.joinToString()
        if (textKeySignature != lastTextKeySignature) {
            lastTextKeySignature = textKeySignature
            AppRuntime.log("高德通知文本字段：${textKeySignature.ifEmpty { "无" }}")
        }
        val info = parser.parse(parts) ?: return
        if (!deduplicator.shouldAccept(info.raw, SystemClock.elapsedRealtime())) return
        AppRuntime.publishNavigation(info, "高德通知")
    }

    private fun appendTextValues(value: Any?, output: MutableList<String>) {
        when (value) {
            is CharSequence -> output += value.toString()
            is Array<*> -> value.forEach { appendTextValues(it, output) }
            is Iterable<*> -> value.forEach { appendTextValues(it, output) }
        }
    }

    override fun onListenerConnected() {
        AppRuntime.log("高德通知监听已连接")
    }

    override fun onListenerDisconnected() {
        AppRuntime.log("高德通知监听已断开")
    }

    companion object {
        const val AMAP_PACKAGE = "com.autonavi.minimap"
        private val STANDARD_TEXT_KEYS = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TEXT_LINES,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_TITLE_BIG,
        )
        private val IGNORED_TEXT_KEYS = setOf(Notification.EXTRA_TEMPLATE)
    }
}
