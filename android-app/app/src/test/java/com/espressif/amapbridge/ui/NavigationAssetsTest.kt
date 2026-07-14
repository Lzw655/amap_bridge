package com.espressif.amapbridge.ui

import com.espressif.amapbridge.R
import com.espressif.amapbridge.navigation.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAssetsTest {
    @Test
    fun everyManeuverHasUniqueWireValueAndDrawable() {
        val wireValues = Maneuver.entries.map(Maneuver::wireValue)
        assertEquals(wireValues.size, wireValues.toSet().size)
        Maneuver.entries.forEach { assertTrue("Missing drawable for $it", it.iconResource() != 0) }
        assertTrue(R.drawable.ic_nav_route != 0)
    }
}
