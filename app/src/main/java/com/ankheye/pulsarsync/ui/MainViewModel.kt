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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fitbitRepository = FitbitRepository()
    private val oneDriveRepository = OneDriveRepository()
    private val syncTrackerManager = SyncTrackerManager(application)
    private val settingsManager = SettingsManager(application)

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
                    val jsonContent = Gson().toJson(newSettings)
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

    fun syncData(dateToSync: LocalDate) {
        val fitbitToken = _uiState.value.fitbitAccessToken
        val msToken = _uiState.value.microsoftAccessToken

        if (fitbitToken == null || msToken == null) {
            logStatus("Error: Both Fitbit and Microsoft must be connected to sync.")
            return
        }
        
        // Check if already synced
        val tracker = syncTrackerManager.loadTracker()
        if (tracker.syncedDates.contains(dateToSync)) {
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

                if (csvResult.isSuccess && jsonResult.isSuccess) {
                    syncTrackerManager.recordSuccessfulSync(dateToSync)
                    refreshTrackerState()
                    logStatus("Sync completed successfully for $dateStr!")
                } else {
                    csvResult.exceptionOrNull()?.let { logStatus("CSV Upload Error: ${it.message}") }
                    jsonResult.exceptionOrNull()?.let { logStatus("JSON Upload Error: ${it.message}") }
                }

            } catch (e: Exception) {
                logStatus("Sync failed: ${e.message}")
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
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
    val availableActivities: List<FavoriteActivity> = emptyList()
)
