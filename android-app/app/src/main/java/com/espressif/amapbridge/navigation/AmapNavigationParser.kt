package com.espressif.amapbridge.navigation

import java.util.Locale
import kotlin.math.roundToInt

class AmapNavigationParser {
    fun parse(parts: List<String>): NavigationInfo? {
        val raw = parts
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" · ")
        if (raw.isEmpty()) return null

        return NavigationInfo(
            maneuver = parseManeuver(raw),
            distanceMeters = parseDistance(raw),
            road = parseRoad(raw),
            raw = raw,
        )
    }

    private fun parseManeuver(text: String): Maneuver {
        val normalized = text.lowercase(Locale.ROOT)
        return when {
            listOf("到达目的地", "已到达", "导航结束").any(normalized::contains) -> Maneuver.ARRIVE
            listOf("向左后方", "左后方", "左急转", "sharp left").any(normalized::contains) -> Maneuver.SHARP_LEFT
            listOf("向右后方", "右后方", "右急转", "sharp right").any(normalized::contains) -> Maneuver.SHARP_RIGHT
            listOf("向左前方", "左前方", "稍向左", "slight left").any(normalized::contains) -> Maneuver.SLIGHT_LEFT
            listOf("向右前方", "右前方", "稍向右", "slight right").any(normalized::contains) -> Maneuver.SLIGHT_RIGHT
            listOf("掉头", "调头", "u-turn", "uturn").any(normalized::contains) -> Maneuver.U_TURN
            listOf("环岛", "roundabout").any(normalized::contains) -> Maneuver.ROUNDABOUT
            listOf("汇入", "并入", "merge").any(normalized::contains) -> Maneuver.MERGE
            listOf("出口", "驶出", "下匝道", "exit").any(normalized::contains) -> Maneuver.EXIT
            listOf("左转", "向左转", "turn left").any(normalized::contains) -> Maneuver.LEFT
            listOf("右转", "向右转", "turn right").any(normalized::contains) -> Maneuver.RIGHT
            listOf("直行", "继续行驶", "straight").any(normalized::contains) -> Maneuver.STRAIGHT
            else -> Maneuver.UNKNOWN
        }
    }

    private fun parseDistance(text: String): Int? {
        val match = DISTANCE.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val meters = if (unit in setOf("公里", "千米", "km")) value * 1000 else value
        return meters.roundToInt().coerceAtLeast(0)
    }

    private fun parseRoad(text: String): String? {
        for (pattern in ROAD_PATTERNS) {
            val candidate = pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrEmpty()) return candidate.take(40)
        }
        return null
    }

    companion object {
        private val DISTANCE = Regex("(?:前方|行驶|距离)?\\s*(\\d+(?:\\.\\d+)?)\\s*(公里|千米|km|米|m)", RegexOption.IGNORE_CASE)
        private val ROAD_PATTERNS = listOf(
            Regex("(?:进入|驶入|转入)([^，。·]+)"),
            Regex("沿([^，。·]+?)(?:行驶|直行|向前)"),
        )
    }
}
