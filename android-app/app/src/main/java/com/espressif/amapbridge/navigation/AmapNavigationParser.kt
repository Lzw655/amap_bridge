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
            remainingDistanceMeters = parseRemainingDistance(raw),
            remainingDurationSeconds = parseRemainingDuration(raw),
            eta = parseEta(raw),
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
        val summaryRanges = SUMMARY_DISTANCE.findAll(text).map(MatchResult::range).toSet()
        val match = NEXT_DISTANCE.find(text)
            ?: DISTANCE_BEFORE_MANEUVER.find(text)
            ?: DISTANCE.findAll(text).firstOrNull { candidate ->
                candidate.range !in summaryRanges && !hasRemainingPrefix(text, candidate.range.first)
            }
            ?: return null
        return distanceToMeters(match)
    }

    private fun parseRemainingDistance(text: String): Int? {
        val match = REMAINING_DISTANCE.find(text) ?: SUMMARY_DISTANCE.find(text) ?: return null
        return distanceToMeters(match)
    }

    private fun distanceToMeters(match: MatchResult): Int? {
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val meters = if (unit in setOf("公里", "千米", "km")) value * 1000 else value
        return meters.roundToInt().coerceAtLeast(0)
    }

    private fun hasRemainingPrefix(text: String, matchStart: Int): Boolean {
        val prefix = text.substring((matchStart - REMAINING_PREFIX_LOOKBEHIND).coerceAtLeast(0), matchStart)
        return REMAINING_PREFIX.containsMatchIn(prefix)
    }

    private fun parseRemainingDuration(text: String): Int? {
        HOURS_AND_MINUTES.find(text)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: return@let
            val minutes = match.groupValues[2].takeIf(String::isNotEmpty)?.toLongOrNull() ?: 0L
            return durationSeconds(hours * 60L + minutes)
        }
        val minutes = MINUTES.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return durationSeconds(minutes)
    }

    private fun durationSeconds(minutes: Long): Int? {
        if (minutes < 0 || minutes > Int.MAX_VALUE / 60L) return null
        return (minutes * 60L).toInt()
    }

    private fun parseEta(text: String): String? {
        val match = ETA_PREFIX.find(text) ?: ETA_SUFFIX.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(Locale.ROOT, hour, minute)
    }

    private fun parseRoad(text: String): String? {
        for (pattern in ROAD_PATTERNS) {
            val candidate = pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrEmpty()) return candidate.take(40)
        }
        return null
    }

    companion object {
        private const val DISTANCE_VALUE_PATTERN = "(\\d+(?:\\.\\d+)?)\\s*(公里|千米|km|米|m)"
        private const val REMAINING_MARKER_PATTERN =
            "(?:全程(?:剩余)?(?:距离)?|剩余(?:距离)?|还剩|距(?:离)?目的地|到目的地(?:还有|还剩)?)"
        private const val REMAINING_PREFIX_LOOKBEHIND = 12
        private val NEXT_DISTANCE = Regex(
            "(?:前方|再行驶|继续行驶|行驶|距离(?:路口|出口|转弯处)?)\\s*$DISTANCE_VALUE_PATTERN",
            RegexOption.IGNORE_CASE,
        )
        private val DISTANCE_BEFORE_MANEUVER = Regex(
            "$DISTANCE_VALUE_PATTERN\\s*(?:后|处)?\\s*" +
                "(?:向?[左右]转|掉头|调头|进入环岛|驶出|汇入|并入)",
            RegexOption.IGNORE_CASE,
        )
        private val DISTANCE = Regex(DISTANCE_VALUE_PATTERN, RegexOption.IGNORE_CASE)
        private val REMAINING_DISTANCE = Regex(
            "$REMAINING_MARKER_PATTERN\\s*$DISTANCE_VALUE_PATTERN",
            RegexOption.IGNORE_CASE,
        )
        private val SUMMARY_DISTANCE = Regex(
            "$DISTANCE_VALUE_PATTERN(?=\\s*(?:[·，,]?\\s*)?(?:剩余|预计|约|还需|用时)?\\s*" +
                "(?:\\d+\\s*(?:小时|时|h)\\s*)?\\d+\\s*(?:分钟|分|min))",
            RegexOption.IGNORE_CASE,
        )
        private val REMAINING_PREFIX = Regex(
            "$REMAINING_MARKER_PATTERN\\s*$",
            RegexOption.IGNORE_CASE,
        )
        private val HOURS_AND_MINUTES = Regex(
            "(\\d+)\\s*(?:小时|时|h)\\s*(?:(\\d+)\\s*(?:分钟|分|min))?",
            RegexOption.IGNORE_CASE,
        )
        private val MINUTES = Regex("(\\d+)\\s*(?:分钟|分|min)", RegexOption.IGNORE_CASE)
        private val ETA_PREFIX = Regex(
            "(?:预计(?:到达)?(?:时间)?|eta)\\s*[:：]?\\s*([0-2]?\\d):([0-5]\\d)",
            RegexOption.IGNORE_CASE,
        )
        private val ETA_SUFFIX = Regex(
            "([0-2]?\\d):([0-5]\\d)\\s*(?:到达|抵达)",
            RegexOption.IGNORE_CASE,
        )
        private val ROAD_PATTERNS = listOf(
            Regex("(?:进入|驶入|转入)([^，。·]+)"),
            Regex("沿([^，。·]+?)(?:行驶|直行|向前)"),
        )
    }
}
