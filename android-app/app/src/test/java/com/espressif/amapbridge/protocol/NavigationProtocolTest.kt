package com.espressif.amapbridge.protocol

import com.espressif.amapbridge.navigation.Maneuver
import com.espressif.amapbridge.navigation.NavigationInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationProtocolTest {
    @Test
    fun encodesKnownNavigation() {
        val bytes = NavigationProtocol.encode(
            NavigationInfo(Maneuver.RIGHT, 300, "人民路", "前方300米右转"),
            sequence = 42,
        )
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(1, json.getInt("v"))
        assertEquals(42, json.getLong("seq"))
        assertEquals("right", json.getString("man"))
        assertEquals(300, json.getInt("dist"))
        assertEquals("人民路", json.getString("road"))
        assertFalse(json.has("raw"))
    }

    @Test
    fun trimsUnknownUtf8PayloadAtCodePointBoundary() {
        val bytes = NavigationProtocol.encode(
            NavigationInfo(Maneuver.UNKNOWN, raw = "未知道路".repeat(200)),
            sequence = 9,
        )
        assertTrue(bytes.size <= NavigationProtocol.MAX_PAYLOAD_BYTES)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        assertNotNull(json.getString("raw"))
        assertEquals("unknown", json.getString("man"))
    }

    @Test
    fun parsesAckAndNack() {
        val ack = NavigationProtocol.parseAck("{\"v\":1,\"ack\":7,\"ok\":true}".toByteArray())!!
        assertEquals(7, ack.sequence)
        assertTrue(ack.ok)

        val nack = NavigationProtocol.parseAck("{\"v\":1,\"ack\":8,\"ok\":false,\"err\":\"bad_version\"}".toByteArray())!!
        assertFalse(nack.ok)
        assertEquals("bad_version", nack.error)
    }
}
