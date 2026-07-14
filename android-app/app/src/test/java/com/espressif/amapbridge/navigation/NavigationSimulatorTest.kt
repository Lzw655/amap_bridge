package com.espressif.amapbridge.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSimulatorTest {
    @Test
    fun everyManeuverHasAValidPreset() {
        Maneuver.entries.forEach { maneuver ->
            val result = NavigationSimulationForm.preset(maneuver).validate()
            assertTrue("Preset failed for $maneuver: ${result.errors}", result.isValid)
            assertEquals(maneuver, result.navigation?.maneuver)
        }
    }

    @Test
    fun convertsMinutesAndAllExtendedFields() {
        val result = NavigationSimulationForm().validate()
        assertTrue(result.isValid)
        val navigation = result.navigation
        assertNotNull(navigation)
        assertEquals(1_080, navigation?.remainingDurationSeconds)
        assertEquals(8_600, navigation?.remainingDistanceMeters)
        assertEquals("14:30", navigation?.eta)
        assertEquals(36, navigation?.currentSpeedKph)
        assertEquals(60, navigation?.speedLimitKph)
    }

    @Test
    fun rejectsInvalidTimeAndNumericRanges() {
        val result = NavigationSimulationForm(
            distanceMeters = "-1",
            remainingDistanceMeters = "not-a-number",
            remainingMinutes = "10081",
            eta = "24:60",
            currentSpeedKph = "301",
            speedLimitKph = "-10",
        ).validate()
        assertFalse(result.isValid)
        assertEquals(setOf("distance", "remaining", "duration", "eta", "speed", "limit"), result.errors.keys)
    }
}
