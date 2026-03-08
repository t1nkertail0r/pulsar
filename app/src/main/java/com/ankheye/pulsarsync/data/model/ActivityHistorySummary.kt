package com.ankheye.pulsarsync.data.model

import java.time.LocalDate

data class ActivityHistorySummary(
    val activityId: Long,
    val date: LocalDate,
    val calories: Int,
    val steps: Int,
    val durationMs: Long,
    val heartRateZones: List<HeartRateZoneInfo>
)

data class HeartRateZoneInfo(
    val name: String,
    val min: Int,
    val max: Int,
    val minutes: Int,
    val caloriesOut: Double
)
