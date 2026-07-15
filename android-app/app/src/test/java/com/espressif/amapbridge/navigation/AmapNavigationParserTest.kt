package com.espressif.amapbridge.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapNavigationParserTest {
    private val parser = AmapNavigationParser()

    @Test
    fun parsesDistanceRoadAndRightTurn() {
        val result = parser.parse(listOf("导航中", "前方300米右转，进入人民路"))!!
        assertEquals(Maneuver.RIGHT, result.maneuver)
        assertEquals(300, result.distanceMeters)
        assertEquals("人民路", result.road)
        assertTrue(result.parsed)
    }

    @Test
    fun convertsKilometersToMeters() {
        val result = parser.parse(listOf("前方1.2公里进入环岛"))!!
        assertEquals(Maneuver.ROUNDABOUT, result.maneuver)
        assertEquals(1_200, result.distanceMeters)
    }

    @Test
    fun parsesRouteSummaryWithoutConfusingNextManeuverDistance() {
        val result = parser.parse(
            listOf(
                "导航中",
                "剩余8.6公里，18分钟，预计14:30到达",
                "前方300米右转，进入人民路",
            ),
        )!!

        assertEquals(Maneuver.RIGHT, result.maneuver)
        assertEquals(300, result.distanceMeters)
        assertEquals(8_600, result.remainingDistanceMeters)
        assertEquals(1_080, result.remainingDurationSeconds)
        assertEquals("14:30", result.eta)
    }

    @Test
    fun parsesCompactSummaryWithHoursAndArrivalSuffix() {
        val result = parser.parse(listOf("126公里 1小时25分钟 9:05到达", "继续直行"))!!

        assertEquals(Maneuver.STRAIGHT, result.maneuver)
        assertNull(result.distanceMeters)
        assertEquals(126_000, result.remainingDistanceMeters)
        assertEquals(5_100, result.remainingDurationSeconds)
        assertEquals("09:05", result.eta)
    }

    @Test
    fun leavesUnavailableRouteSummaryFieldsEmpty() {
        val result = parser.parse(listOf("前方300米右转，进入人民路"))!!

        assertNull(result.remainingDistanceMeters)
        assertNull(result.remainingDurationSeconds)
        assertNull(result.eta)
    }

    @Test
    fun recognizesEverySupportedManeuver() {
        val cases = mapOf(
            "继续直行" to Maneuver.STRAIGHT,
            "前方左转" to Maneuver.LEFT,
            "前方右转" to Maneuver.RIGHT,
            "向左前方行驶" to Maneuver.SLIGHT_LEFT,
            "向右前方行驶" to Maneuver.SLIGHT_RIGHT,
            "向左后方行驶" to Maneuver.SHARP_LEFT,
            "向右后方行驶" to Maneuver.SHARP_RIGHT,
            "前方掉头" to Maneuver.U_TURN,
            "进入环岛" to Maneuver.ROUNDABOUT,
            "已到达目的地" to Maneuver.ARRIVE,
            "汇入主路" to Maneuver.MERGE,
            "从出口驶出" to Maneuver.EXIT,
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, parser.parse(listOf(text))!!.maneuver) }
    }

    @Test
    fun preservesUnknownTextWithoutInventingFields() {
        val result = parser.parse(listOf("导航正在运行"))!!
        assertEquals(Maneuver.UNKNOWN, result.maneuver)
        assertNull(result.distanceMeters)
        assertNull(result.road)
        assertFalse(result.parsed)
        assertEquals("导航正在运行", result.raw)
    }

    @Test
    fun ignoresEmptyNotification() {
        assertNull(parser.parse(listOf(" ", "")))
    }

    @Test
    fun deduplicatesWithinWindow() {
        val deduplicator = NotificationDeduplicator(1_500)
        assertTrue(deduplicator.shouldAccept("same", 10_000))
        assertFalse(deduplicator.shouldAccept("same", 10_500))
        assertTrue(deduplicator.shouldAccept("same", 12_001))
        assertTrue(deduplicator.shouldAccept("different", 12_100))
    }
}
