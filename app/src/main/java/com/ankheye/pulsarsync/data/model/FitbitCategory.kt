package com.ankheye.pulsarsync.data.model

data class FitbitActivityCategoryResponse(
    val categories: List<FitbitCategory>
)

data class FitbitCategory(
    val id: Long,
    val name: String,
    val activities: List<FitbitActivityDef>?,
    val subCategories: List<FitbitCategory>?
)

data class FitbitActivityDef(
    val id: Long,
    val name: String,
    val hasSpeed: Boolean?,
    val activityLevel: List<FitbitActivityLevel>?
)

data class FitbitActivityLevel(
    val id: Long,
    val name: String,
    val minSpeedMPH: Double?,
    val maxSpeedMPH: Double?
)
