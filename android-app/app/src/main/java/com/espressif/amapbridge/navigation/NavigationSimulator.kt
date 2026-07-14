package com.espressif.amapbridge.navigation

data class NavigationSimulationForm(
    val maneuver: Maneuver = Maneuver.RIGHT,
    val road: String = "人民路",
    val distanceMeters: String = "300",
    val remainingDistanceMeters: String = "8600",
    val remainingMinutes: String = "18",
    val eta: String = "14:30",
    val currentSpeedKph: String = "36",
    val speedLimitKph: String = "60",
    val raw: String = "",
) {
    fun validate(): SimulationValidation {
        val errors = linkedMapOf<String, String>()
        val distance = parseOptionalInt("distance", distanceMeters, 0..1_000_000, errors)
        val remaining = parseOptionalInt("remaining", remainingDistanceMeters, 0..10_000_000, errors)
        val minutes = parseOptionalInt("duration", remainingMinutes, 0..10_080, errors)
        val speed = parseOptionalInt("speed", currentSpeedKph, 0..300, errors)
        val limit = parseOptionalInt("limit", speedLimitKph, 0..300, errors)
        val normalizedEta = eta.trim().takeIf(String::isNotEmpty)
        if (normalizedEta != null && !ETA_PATTERN.matches(normalizedEta)) {
            errors["eta"] = "时间格式应为 HH:mm"
        }
        if (road.length > 80) errors["road"] = "道路名不能超过 80 个字符"

        if (errors.isNotEmpty()) return SimulationValidation(errors = errors)
        val normalizedRaw = raw.trim().ifEmpty {
            buildString {
                distance?.let { append("前方${it}米") }
                append(maneuver.displayName)
                road.trim().takeIf(String::isNotEmpty)?.let { append("，进入$it") }
            }
        }
        return SimulationValidation(
            navigation = NavigationInfo(
                maneuver = maneuver,
                distanceMeters = distance,
                road = road.trim().takeIf(String::isNotEmpty),
                raw = normalizedRaw,
                remainingDistanceMeters = remaining,
                remainingDurationSeconds = minutes?.times(60),
                eta = normalizedEta,
                currentSpeedKph = speed,
                speedLimitKph = limit,
            ),
        )
    }

    companion object {
        private val ETA_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

        fun preset(maneuver: Maneuver): NavigationSimulationForm {
            val index = Maneuver.entries.indexOf(maneuver).coerceAtLeast(0)
            return NavigationSimulationForm(
                maneuver = maneuver,
                road = PRESET_ROADS[index % PRESET_ROADS.size],
                distanceMeters = if (maneuver == Maneuver.ARRIVE) "0" else (120 + index * 40).toString(),
                remainingDistanceMeters = if (maneuver == Maneuver.ARRIVE) "0" else (8_600 - index * 420).coerceAtLeast(500).toString(),
                remainingMinutes = if (maneuver == Maneuver.ARRIVE) "0" else (18 - index).coerceAtLeast(2).toString(),
                eta = "14:${(30 + index).coerceAtMost(59).toString().padStart(2, '0')}",
                currentSpeedKph = if (maneuver == Maneuver.ARRIVE) "0" else (32 + index).toString(),
                speedLimitKph = if (maneuver in listOf(Maneuver.ROUNDABOUT, Maneuver.ARRIVE)) "40" else "60",
                raw = if (maneuver == Maneuver.UNKNOWN) "前方道路情况未知" else "",
            )
        }

        private val PRESET_ROADS = listOf("人民路", "中山大道", "解放路", "环城高速", "滨江大道")
    }
}

data class SimulationValidation(
    val navigation: NavigationInfo? = null,
    val errors: Map<String, String> = emptyMap(),
) {
    val isValid: Boolean get() = navigation != null && errors.isEmpty()
}

private fun parseOptionalInt(
    key: String,
    value: String,
    range: IntRange,
    errors: MutableMap<String, String>,
): Int? {
    val normalized = value.trim()
    if (normalized.isEmpty()) return null
    val number = normalized.toIntOrNull()
    if (number == null || number !in range) {
        errors[key] = "请输入 ${range.first}–${range.last} 的整数"
    }
    return number?.takeIf { it in range }
}
