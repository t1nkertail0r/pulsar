package com.ankheye.pulsarsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ankheye.pulsarsync.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailsScreen(
    activityId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activityName = uiState.favoriteActivities.find { it.id == activityId }?.name ?: "Activity Details"

    LaunchedEffect(activityId) {
        viewModel.loadActivityHistory(activityId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activityName) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Recent History", style = MaterialTheme.typography.titleMedium)
            
            if (uiState.isLoadingHistory) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (uiState.activityHistory.isEmpty()) {
                Text("No recent history found for this activity.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.activityHistory) { history ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(history.date.toString(), style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text("Calories: ${history.calories}", style = MaterialTheme.typography.bodyMedium)
                                }
                                
                                if (history.heartRateZones.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Heart Rate Zones", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    history.heartRateZones.forEach { zone ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${zone.name} (${zone.min}-${zone.max})", style = MaterialTheme.typography.bodySmall)
                                            Text("${zone.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
