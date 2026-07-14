package com.espressif.amapbridge.navigation

class NotificationDeduplicator(private val windowMs: Long = 1_500) {
    private var previousValue: String? = null
    private var previousAt: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldAccept(value: String, nowMs: Long): Boolean {
        val duplicate = value == previousValue && nowMs - previousAt in 0 until windowMs
        previousValue = value
        previousAt = nowMs
        return !duplicate
    }
}
