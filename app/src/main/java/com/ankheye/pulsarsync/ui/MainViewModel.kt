package com.ankheye.pulsarsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ankheye.pulsarsync.data.format.DataFormatter
import com.ankheye.pulsarsync.data.repository.FitbitRepository
import com.ankheye.pulsarsync.data.repository.OneDriveRepository
import com.ankheye.pulsarsync.data.repository.SyncTrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fitbitRepository = FitbitRepository()
    private val oneDriveRepository = OneDriveRepository()
    private val syncTrackerManager = SyncTrackerManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        refreshTrackerState()
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
    val syncedDates: List<LocalDate> = emptyList()
)
