package com.ankheye.pulsarsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ankheye.pulsarsync.ui.MainViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onConnectFitbit: () -> Unit,
    onConnectMicrosoft: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Date Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var isForceSync by remember { mutableStateOf(false) }
    
    // Activities State
    var showActivitiesSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Sync") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Authentication Section
            Text("Accounts", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConnectFitbit,
                        enabled = !uiState.isFitbitConnected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isFitbitConnected) "Fitbit Connected" else "Connect Fitbit")
                    }

                    Button(
                        onClick = onConnectMicrosoft,
                        enabled = !uiState.isMicrosoftConnected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isMicrosoftConnected) "Microsoft Connected" else "Connect Microsoft (OneDrive)")
                    }
                }
            }

            Divider()

            // Favorite Activities Section
            Text("Favorite Activities", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.favoriteActivities.isEmpty()) {
                        Text("No favorite activities selected.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        uiState.favoriteActivities.forEach { activity ->
                            Text("• ${activity.name}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            viewModel.fetchAvailableActivities()
                            showActivitiesSheet = true 
                        },
                        enabled = uiState.isFitbitConnected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Favorites (${uiState.favoriteActivities.size}/5)")
                    }
                }
            }

            if (showActivitiesSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showActivitiesSheet = false },
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            "Select up to 5 Favorites", 
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(uiState.availableActivities) { activity ->
                                    val isSelected = uiState.favoriteActivities.any { it.id == activity.id }
                                    // Disable checkbox if we are at 5 and this item IS NOT selected
                                    val isReachedLimit = uiState.favoriteActivities.size >= 5
                                    val isEnabled = isSelected || !isReachedLimit
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleFavoriteActivity(activity) },
                                            enabled = isEnabled
                                        )
                                        Text(
                                            text = activity.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(start = 8.dp),
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Button(
                            onClick = { showActivitiesSheet = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text("Done")
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            Divider()

            // Manual Sync Section
            Text("Manual Sync", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "No date selected",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("Select Date")
                }
            }

            // Force Sync Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isForceSync,
                    onCheckedChange = { isForceSync = it }
                )
                Text(
                    text = "Force Sync (Overwrite existing)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Button(
                onClick = { 
                    selectedDate?.let { viewModel.syncData(it, isForceSync) } 
                },
                enabled = selectedDate != null && uiState.isFitbitConnected && uiState.isMicrosoftConnected && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Sync Selected Date")
                }
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            }
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Divider()

            // Status Log & History
            Text("Sync History", style = MaterialTheme.typography.titleMedium)
            
            if (uiState.syncedDates.isEmpty()) {
                Text("No dates have been synced yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(uiState.syncedDates) { date ->
                        ListItem(
                            headlineContent = { Text(date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))) },
                            supportingContent = { Text("Synced to OneDrive") }
                        )
                    }
                }
            }

            // Keep the raw log box at the very bottom for debugging
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = uiState.statusLog,
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
