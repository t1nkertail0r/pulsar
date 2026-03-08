package com.ankheye.pulsarsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ankheye.pulsarsync.data.format.DataFormatter
import com.ankheye.pulsarsync.data.model.FavoriteActivity
import com.ankheye.pulsarsync.data.repository.FitbitRepository
import com.ankheye.pulsarsync.data.repository.OneDriveRepository
import com.ankheye.pulsarsync.data.repository.SettingsManager
import com.ankheye.pulsarsync.data.repository.SyncTrackerManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.time.ZonedDateTime
import com.ankheye.pulsarsync.data.model.ActivityHistorySummary
import com.ankheye.pulsarsync.data.model.HeartRateZoneInfo
import com.google.gson.*
import java.lang.reflect.Type

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fitbitRepository = FitbitRepository()
    private val oneDriveRepository = OneDriveRepository()
    private val syncTrackerManager = SyncTrackerManager(application)
    private val settingsManager = SettingsManager(application)
    
    // Custom Gson instance to properly serialize java.time.LocalDate
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, object : JsonSerializer<LocalDate> {
            override fun serialize(src: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                return JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
        })
        .registerTypeAdapter(LocalDate::class.java, object : JsonDeserializer<LocalDate> {
            override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDate {
                return LocalDate.parse(json?.asString, DateTimeFormatter.ISO_LOCAL_DATE)
            }
        })
        .create()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            syncEndDate = LocalDate.now(),
            syncStartDate = LocalDate.now().minusDays(7)
        )
        refreshTrackerState()
        refreshSettingsState()
    }

    private fun refreshSettingsState() {
        val settings = settingsManager.loadSettings()
        _uiState.value = _uiState.value.copy(
            favoriteActivities = settings.favoriteActivities
        )
    }

    fun fetchAvailableActivities() {
        val fitbitToken = _uiState.value.fitbitAccessToken ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val categories = fitbitRepository.fetchAllActivities(fitbitToken)
                
                // Flatten the category tree down to a list of FavoriteActivity objects
                val flatActivities = mutableListOf<FavoriteActivity>()
                
                fun extractActivities(category: com.ankheye.pulsarsync.data.model.FitbitCategory) {
                    category.activities?.forEach { activity ->
                        flatActivities.add(FavoriteActivity(id = activity.id, name = activity.name))
                    }
                    category.subCategories?.forEach { subCat ->
                        extractActivities(subCat)
                    }
                }
                
                categories.forEach { extractActivities(it) }
                
                // Sort alphabetically
                flatActivities.sortBy { it.name }
                
                _uiState.value = _uiState.value.copy(
                    availableActivities = flatActivities,
                    isLoading = false
                )
            } catch (e: Exception) {
                logStatus("Failed to fetch available activities: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleFavoriteActivity(activity: FavoriteActivity) {
        val currentFavorites = _uiState.value.favoriteActivities.toMutableList()
        
        if (currentFavorites.any { it.id == activity.id }) {
            // Remove if already favorited
            currentFavorites.removeAll { it.id == activity.id }
        } else {
            // Add if less than 5
            if (currentFavorites.size >= 5) {
                logStatus("Cannot add more than 5 favorite activities.")
                return
            }
            currentFavorites.add(activity)
        }
        
        val newSettings = com.ankheye.pulsarsync.data.model.AppSettings(favoriteActivities = currentFavorites)
        settingsManager.saveSettings(newSettings)
        refreshSettingsState()
        
        // Asynchronously upload to OneDrive
        val msToken = _uiState.value.microsoftAccessToken
        if (msToken != null) {
            viewModelScope.launch {
                try {
                    val jsonContent = gson.toJson(newSettings)
                    val result = oneDriveRepository.uploadFile(
                        accessToken = msToken,
                        folderPath = "",
                        fileName = "settings.json",
                        fileContent = jsonContent,
                        contentType = "application/json"
                    )
                    if (result.isSuccess) {
                        logStatus("Settings automatically synced to OneDrive root.")
                    } else {
                        logStatus("Failed to sync settings: ${result.exceptionOrNull()?.message}")
                    }
                } catch(e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateSyncDates(start: LocalDate, end: LocalDate) {
        _uiState.value = _uiState.value.copy(
            syncStartDate = start,
            syncEndDate = end
        )
    }

    private fun refreshTrackerState() {
        val tracker = syncTrackerManager.loadTracker()
        _uiState.value = _uiState.value.copy(
            syncedDates = tracker.syncedDates.toList().sortedDescending()
        )
    }

    fun setFitbitToken(token: String) {
        _uiState.value = _uiState.value.copy(fitbitAccessToken = token, isFitbitConnected = true)
        logStatus("Fitbit Connected.")
    }

    fun setMicrosoftToken(token: String) {
        _uiState.value = _uiState.value.copy(microsoftAccessToken = token, isMicrosoftConnected = true)
        logStatus("Microsoft Connected.")
    }

    fun logStatus(message: String) {
        val currentLog = _uiState.value.statusLog
        _uiState.value = _uiState.value.copy(statusLog = "$message\n$currentLog")
    }

    fun syncData(startDate: LocalDate, endDate: LocalDate, isForceSync: Boolean = false) {
        val fitbitToken = _uiState.value.fitbitAccessToken
        val msToken = _uiState.value.microsoftAccessToken

        if (fitbitToken == null || msToken == null) {
            logStatus("Error: Both Fitbit and Microsoft must be connected to sync.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, syncProgress = null)
            try {
                val startStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val endStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val searchAfterDate = startDate.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                logStatus("Fetching recent activities from Fitbit ($startStr to $endStr)...")
                
                // Fetch paginated list
                val listResponse = fitbitRepository.fetchActivityLogList(
                    accessToken = fitbitToken,
                    afterDate = searchAfterDate,
                    sort = "asc",
                    limit = 100
                )
                
                val favoriteIds = _uiState.value.favoriteActivities.map { it.id }
                
                // Filter down to the date range and matched favorite activities
                val targetActivities = listResponse.activities.filter { activity -> 
                    try {
                        val zdt = ZonedDateTime.parse(activity.startTime)
                        val actDate = zdt.toLocalDate()
                        !actDate.isAfter(endDate) && favoriteIds.contains(activity.activityTypeId)
                    } catch (e: Exception) { false }
                }
                
                if (targetActivities.isEmpty()) {
                    logStatus("No new Favorite Activities found in this date range.")
                    return@launch
                }

                logStatus("Found ${targetActivities.size} Favorite Activities to sync! Processing...")
                var allDetailedSyncsSuccessful = true
                val totalSyncs = targetActivities.size
                var currentSync = 0

                for (activity in targetActivities) {
                    currentSync++
                    _uiState.value = _uiState.value.copy(syncProgress = "$currentSync/$totalSyncs")
                    
                    val zdt = try {
                        ZonedDateTime.parse(activity.startTime)
                    } catch (e: Exception) { continue }
                    val actLocalDate = zdt.toLocalDate()
                    val actDateStr = actLocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val folderPath = "${actLocalDate.year}/${String.format("%02d", actLocalDate.monthValue)}/${String.format("%02d", actLocalDate.dayOfMonth)}"
                    
                    logStatus("Syncing ${activity.activityName} on $actDateStr (Log ID: ${activity.logId})...")

                    try {
                        val detailJson = fitbitRepository.fetchActivityDetails(fitbitToken, activity.logId)
                        val detailResult = oneDriveRepository.uploadFile(
                            accessToken = msToken,
                            folderPath = folderPath,
                            fileName = "${activity.logId}.json",
                            fileContent = detailJson,
                            contentType = "application/json"
                        )
                        if (detailResult.isFailure) {
                            allDetailedSyncsSuccessful = false
                            logStatus("Failed to upload JSON for ${activity.logId}")
                        }

                        // Also fetch and upload intraday heart rate data
                        val startTimeStr = zdt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) // HH:mm
                        val durationMs = activity.duration
                        val hrZonesList = mutableListOf<HeartRateZoneInfo>()
                        
                        if (startTimeStr.isNotEmpty() && durationMs > 0) {
                            try {
                                val startTime = zdt.toLocalTime()
                                val endTime = startTime.plus(Duration.ofMillis(durationMs))
                                val endTimeStr = endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                                
                                val hrJson = fitbitRepository.fetchIntradayHeartRate(
                                    accessToken = fitbitToken,
                                    date = actDateStr,
                                    startTime = startTimeStr,
                                    endTime = endTimeStr
                                )
                                val hrResult = oneDriveRepository.uploadFile(
                                    accessToken = msToken,
                                    folderPath = folderPath,
                                    fileName = "${activity.logId}_heartrate.json",
                                    fileContent = hrJson,
                                    contentType = "application/json"
                                )
                                if (hrResult.isFailure) {
                                    allDetailedSyncsSuccessful = false
                                    logStatus("Failed to upload HR JSON for ${activity.logId}")
                                }
                                
                                // Parse hrJson for heartRateZones!
                                val hrObj = Gson().fromJson(hrJson, com.google.gson.JsonObject::class.java)
                                if (hrObj.has("activities-heart")) {
                                    val heartArray = hrObj.getAsJsonArray("activities-heart")
                                    if (heartArray.size() > 0) {
                                        val dayObj = heartArray[0].asJsonObject
                                        if (dayObj.has("heartRateZones")) {
                                            val zonesArray = dayObj.getAsJsonArray("heartRateZones")
                                            for (j in 0 until zonesArray.size()) {
                                                val zoneObj = zonesArray[j].asJsonObject
                                                hrZonesList.add(
                                                    HeartRateZoneInfo(
                                                        name = zoneObj.get("name").asString,
                                                        min = zoneObj.get("min").asInt,
                                                        max = zoneObj.get("max").asInt,
                                                        minutes = zoneObj.get("minutes").asInt,
                                                        caloriesOut = if (zoneObj.has("caloriesOut")) zoneObj.get("caloriesOut").asDouble else 0.0
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (hrException: Exception) {
                                allDetailedSyncsSuccessful = false
                                logStatus("Error fetching HR for ${activity.logId}: ${hrException.message}")
                            }
                        }

                        // Update Master Cache file
                        val newHistoryRecord = ActivityHistorySummary(
                            activityId = activity.activityTypeId,
                            date = actLocalDate,
                            calories = activity.calories,
                            steps = activity.steps ?: 0,
                            durationMs = activity.duration,
                            heartRateZones = hrZonesList
                        )

                        val cacheFileName = "recent_history_${activity.activityTypeId}.json"
                        val existingCacheResult = oneDriveRepository.downloadFile(
                            accessToken = msToken,
                            folderPath = "",
                            fileName = cacheFileName
                        )
                        
                        val cacheList = mutableListOf<ActivityHistorySummary>()
                        if (existingCacheResult.isSuccess) {
                            val existingJsonStr = existingCacheResult.getOrNull() ?: "[]"
                            try {
                                val arr = gson.fromJson(existingJsonStr, Array<ActivityHistorySummary>::class.java)
                                if (arr != null) {
                                    cacheList.addAll(arr)
                                }
                            } catch (e: Exception) {
                                logStatus("Error parsing cache for ${activity.activityName}")
                            }
                        }
                        
                        // Overwrite exact existing duplicate if force sync is replacing old data
                        cacheList.removeAll { it.date == actLocalDate && it.durationMs == activity.duration }
                        cacheList.add(newHistoryRecord)
                        cacheList.sortByDescending { it.date }
                        
                        val updatedCacheJson = gson.toJson(cacheList.take(10))
                        
                        oneDriveRepository.uploadFile(
                            accessToken = msToken,
                            folderPath = "",
                            fileName = cacheFileName,
                            fileContent = updatedCacheJson,
                            contentType = "application/json"
                        )
                    } catch (e: Exception) {
                        allDetailedSyncsSuccessful = false
                        logStatus("Error with detailed sync for ${activity.logId}: ${e.message}")
                    }
                }

                if (allDetailedSyncsSuccessful) {
                    logStatus("Sync completed successfully for date range!")
                } else {
                    logStatus("One or more detailed activity JSONs failed to upload.")
                }

            } catch (e: Exception) {
                logStatus("Sync failed: ${e.message}")
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, syncProgress = null)
            }
        }
    }

    fun loadActivityHistory(activityId: Long) {
        val msToken = _uiState.value.microsoftAccessToken
        if (msToken == null) {
            logStatus("Cannot load history without Microsoft account connected.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistory = true, activityHistory = emptyList())
            
            val cacheFileName = "recent_history_${activityId}.json"
            val cacheResult = oneDriveRepository.downloadFile(
                accessToken = msToken,
                folderPath = "",
                fileName = cacheFileName
            )
            
            val historyList = mutableListOf<ActivityHistorySummary>()
            if (cacheResult.isSuccess) {
                val cacheJsonStr = cacheResult.getOrNull() ?: "[]"
                try {
                    val arr = gson.fromJson(cacheJsonStr, Array<ActivityHistorySummary>::class.java)
                    if (arr != null) {
                        historyList.addAll(arr)
                    }
                } catch (e: Exception) {
                    logStatus("Error parsing root Master Cache: ${e.message}")
                }
            } else {
                logStatus("No existing history cache found for $activityId.")
            }
            
            _uiState.value = _uiState.value.copy(
                isLoadingHistory = false,
                activityHistory = historyList
            )
        }
    }
}

data class UiState(
    val fitbitAccessToken: String? = null,
    val microsoftAccessToken: String? = null,
    val isFitbitConnected: Boolean = false,
    val isMicrosoftConnected: Boolean = false,
    val isLoading: Boolean = false,
    val syncProgress: String? = null,
    val statusLog: String = "",
    val syncStartDate: LocalDate? = null,
    val syncEndDate: LocalDate? = null,
    val syncedDates: List<LocalDate> = emptyList(),
    val favoriteActivities: List<FavoriteActivity> = emptyList(),
    val availableActivities: List<FavoriteActivity> = emptyList(),
    val activityHistory: List<ActivityHistorySummary> = emptyList(),
    val isLoadingHistory: Boolean = false
)
