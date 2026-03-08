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
import java.time.LocalTime
import java.time.Duration
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

    private fun refreshTrackerState() {
        val tracker = syncTrackerManager.loadTracker()
        _uiState.value = _uiState.value.copy(
            lastSyncDate = tracker.lastSyncDate,
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

    fun syncData(dateToSync: LocalDate, isForceSync: Boolean = false) {
        val fitbitToken = _uiState.value.fitbitAccessToken
        val msToken = _uiState.value.microsoftAccessToken

        if (fitbitToken == null || msToken == null) {
            logStatus("Error: Both Fitbit and Microsoft must be connected to sync.")
            return
        }
        
        // Check if already synced
        val tracker = syncTrackerManager.loadTracker()
        if (!isForceSync && tracker.syncedDates.contains(dateToSync)) {
            logStatus("Data for ${dateToSync.format(DateTimeFormatter.ISO_LOCAL_DATE)} has already been synced. Skipping.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val dateStr = dateToSync.format(DateTimeFormatter.ISO_LOCAL_DATE) // yyyy-MM-dd
                val folderPath = "${dateToSync.year}/${String.format("%02d", dateToSync.monthValue)}/${String.format("%02d", dateToSync.dayOfMonth)}"
                
                logStatus("Fetching Fitbit data for $dateStr...")
                val response = fitbitRepository.fetchDailySummary(fitbitToken, dateStr)
                
                logStatus("Formatting data...")
                val csvContent = DataFormatter.toCsv(response, dateStr)
                val jsonContent = DataFormatter.toJsonMetadata(response, dateStr)
                
                logStatus("Uploading to OneDrive...")
                // Upload CSV
                val csvResult = oneDriveRepository.uploadFile(
                    accessToken = msToken,
                    folderPath = folderPath,
                    fileName = "fitness_data_$dateStr.csv",
                    fileContent = csvContent,
                    contentType = "text/csv"
                )
                
                // Upload JSON
                val jsonResult = oneDriveRepository.uploadFile(
                    accessToken = msToken,
                    folderPath = folderPath,
                    fileName = "fitness_metadata_$dateStr.json",
                    fileContent = jsonContent,
                    contentType = "application/json"
                )

                var allDetailedSyncsSuccessful = true
                val favoriteIds = _uiState.value.favoriteActivities.map { it.id }
                
                for (activity in response.activities) {
                    if (favoriteIds.contains(activity.activityId)) { // note: Fitbit returns activityParentId or activityId. Using activityId from Favorites matching the global Activity leaf.
                        logStatus("Fetching detailed data for favorite activity: ${activity.name} (${activity.logId})...")
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
                                logStatus("Failed to upload detailed JSON for ${activity.logId}: ${detailResult.exceptionOrNull()?.message}")
                            } else {
                                logStatus("Safely uploaded ${activity.logId}.json to OneDrive")
                            }

                            // Also fetch and upload intraday heart rate data
                            val startTimeStr = activity.startTime // HH:mm
                            val durationMs = activity.duration
                            val hrZonesList = mutableListOf<HeartRateZoneInfo>()
                            
                            if (startTimeStr.isNotEmpty() && durationMs > 0) {
                                try {
                                    val startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"))
                                    val endTime = startTime.plus(Duration.ofMillis(durationMs))
                                    val endTimeStr = endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                                    
                                    logStatus("Fetching 1min Intraday Heart Rate ($startTimeStr - $endTimeStr)...")
                                    val hrJson = fitbitRepository.fetchIntradayHeartRate(
                                        accessToken = fitbitToken,
                                        date = activity.startDate,
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
                                        logStatus("Failed to upload Heart Rate JSON for ${activity.logId}: ${hrResult.exceptionOrNull()?.message}")
                                    } else {
                                        logStatus("Safely uploaded ${activity.logId}_heartrate.json to OneDrive")
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
                                    logStatus("Error fetching Intraday HR for ${activity.logId}: ${hrException.message}")
                                }
                            }

                            // Create a history record for the Master Cache
                            val newHistoryRecord = ActivityHistorySummary(
                                activityId = activity.activityId, // E.g., Soccer = 15605
                                date = dateToSync,
                                calories = activity.calories,
                                steps = activity.steps,
                                durationMs = activity.duration,
                                heartRateZones = hrZonesList
                            )

                            // Download Master Cache for this activityId, update it, and re-upload!
                            val cacheFileName = "recent_history_${activity.activityId}.json"
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
                                    logStatus("Error parsing existing cache for ${activity.name}")
                                }
                            }
                            
                            // Remove any existing entry for this exact date to prevent duplicates on force-sync
                            cacheList.removeAll { it.date == dateToSync }
                            // Add new record and sort descending by date
                            cacheList.add(newHistoryRecord)
                            cacheList.sortByDescending { it.date }
                            
                            // Keep only the 10 most recent
                            val truncatedCache = cacheList.take(10)
                            val updatedCacheJson = gson.toJson(truncatedCache)
                            
                            oneDriveRepository.uploadFile(
                                accessToken = msToken,
                                folderPath = "",
                                fileName = cacheFileName,
                                fileContent = updatedCacheJson,
                                contentType = "application/json"
                            )
                            
                            logStatus("Updated root Master Cache for Favorite: ${activity.name}")
                        } catch (e: Exception) {
                            allDetailedSyncsSuccessful = false
                            logStatus("Error fetching detailed activity for ${activity.logId}: ${e.message}")
                        }
                    }
                }

                if (csvResult.isSuccess && jsonResult.isSuccess && allDetailedSyncsSuccessful) {
                    syncTrackerManager.recordSuccessfulSync(dateToSync)
                    refreshTrackerState()
                    logStatus("Sync completed successfully for $dateStr!")
                } else {
                    csvResult.exceptionOrNull()?.let { logStatus("CSV Upload Error: ${it.message}") }
                    jsonResult.exceptionOrNull()?.let { logStatus("JSON Upload Error: ${it.message}") }
                    if (!allDetailedSyncsSuccessful) {
                        logStatus("One or more detailed activity JSONs failed to upload.")
                    }
                }

            } catch (e: Exception) {
                logStatus("Sync failed: ${e.message}")
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
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
    val statusLog: String = "",
    val lastSyncDate: LocalDate? = null,
    val syncedDates: List<LocalDate> = emptyList(),
    val favoriteActivities: List<FavoriteActivity> = emptyList(),
    val availableActivities: List<FavoriteActivity> = emptyList(),
    val activityHistory: List<ActivityHistorySummary> = emptyList(),
    val isLoadingHistory: Boolean = false
)
