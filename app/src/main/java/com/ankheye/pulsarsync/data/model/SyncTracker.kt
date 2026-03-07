package com.ankheye.pulsarsync.data.model

import java.time.LocalDate

data class SyncTracker(
    val lastSyncDate: LocalDate? = null,
    val syncedDates: MutableSet<LocalDate> = mutableSetOf()
)
