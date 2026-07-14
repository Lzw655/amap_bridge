package com.espressif.amapbridge.navigation

enum class Maneuver(val wireValue: String, val displayName: String) {
    STRAIGHT("straight", "直行"),
    LEFT("left", "左转"),
    RIGHT("right", "右转"),
    SLIGHT_LEFT("slight_left", "向左前方"),
    SLIGHT_RIGHT("slight_right", "向右前方"),
    SHARP_LEFT("sharp_left", "向左后方"),
    SHARP_RIGHT("sharp_right", "向右后方"),
    U_TURN("u_turn", "掉头"),
    ROUNDABOUT("roundabout", "进入环岛"),
    ARRIVE("arrive", "到达目的地"),
    MERGE("merge", "汇入道路"),
    EXIT("exit", "驶出道路"),
    UNKNOWN("unknown", "未知动作"),
}

data class NavigationInfo(
    val maneuver: Maneuver,
    val distanceMeters: Int? = null,
    val road: String? = null,
    val raw: String,
    val parsed: Boolean = maneuver != Maneuver.UNKNOWN,
)
