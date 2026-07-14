package com.espressif.amapbridge.ui

import androidx.annotation.DrawableRes
import com.espressif.amapbridge.R
import com.espressif.amapbridge.navigation.Maneuver

@DrawableRes
fun Maneuver.iconResource(): Int = when (this) {
    Maneuver.STRAIGHT -> R.drawable.ic_nav_straight
    Maneuver.LEFT -> R.drawable.ic_nav_left
    Maneuver.RIGHT -> R.drawable.ic_nav_right
    Maneuver.SLIGHT_LEFT -> R.drawable.ic_nav_slight_left
    Maneuver.SLIGHT_RIGHT -> R.drawable.ic_nav_slight_right
    Maneuver.SHARP_LEFT -> R.drawable.ic_nav_sharp_left
    Maneuver.SHARP_RIGHT -> R.drawable.ic_nav_sharp_right
    Maneuver.U_TURN -> R.drawable.ic_nav_u_turn
    Maneuver.ROUNDABOUT -> R.drawable.ic_nav_roundabout
    Maneuver.ARRIVE -> R.drawable.ic_nav_arrive
    Maneuver.MERGE -> R.drawable.ic_nav_merge
    Maneuver.EXIT -> R.drawable.ic_nav_exit
    Maneuver.UNKNOWN -> R.drawable.ic_nav_unknown
}
