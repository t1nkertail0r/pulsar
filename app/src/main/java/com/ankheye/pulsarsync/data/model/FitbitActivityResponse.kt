package com.ankheye.pulsarsync.data.model

data class FitbitActivityResponse(
    val activities: List<FitbitActivity>,
    val goals: ActivityGoals,
    val summary: ActivitySummary
)

data class FitbitActivity(
    val activityId: Long,
    val activityParentId: Long,
    val activityParentName: String?,
    val calories: Int,
    val description: String?,
    val distance: Double,
    val duration: Long,
    val hasActiveZoneMinutes: Boolean,
    val hasStartTime: Boolean,
    val isFavorite: Boolean,
    val lastModified: String?,
    val logId: Long,
    val name: String,
    val startDate: String?,
    val startTime: String,
    val steps: Int
)

data class ActivityGoals(
    val activeMinutes: Int,
    val caloriesOut: Int,
    val distance: Double,
    val steps: Int
)

data class ActivitySummary(
    val activeScore: Int,
    val activityCalories: Int,
    val caloriesBMR: Int,
    val caloriesOut: Int,
    val distances: List<ActivityDistance>,
    val fairlyActiveMinutes: Int,
    val lightlyActiveMinutes: Int,
    val marginalCalories: Int,
    val sedentaryMinutes: Int,
    val steps: Int,
    val veryActiveMinutes: Int
)

data class ActivityDistance(
    val activity: String,
    val distance: Double
)

data class FitbitActivityLog(
    val logId: Long,
    val activityTypeId: Long,
    val activityName: String,
    val calories: Int,
    val steps: Int?, // Optional as some activities don't have steps
    val duration: Long,
    val startTime: String // ISO-8601 string containing date and time e.g "2026-02-28T11:10:09.000-08:00"
)

data class FitbitActivityListResponse(
    val activities: List<FitbitActivityLog>,
    val pagination: PaginationMetadata
)

data class PaginationMetadata(
    val afterDate: String,
    val limit: Int,
    val next: String,
    val offset: Int,
    val previous: String,
    val sort: String
)
