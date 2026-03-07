package com.ankheye.pulsarsync.data.model

data class AppSettings(
    val favoriteActivities: List<FavoriteActivity> = emptyList()
)

data class FavoriteActivity(
    val id: Long,
    val name: String
)
