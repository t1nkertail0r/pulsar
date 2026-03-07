package com.ankheye.pulsarsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ankheye.pulsarsync.auth.FitbitAuthManager
import com.ankheye.pulsarsync.auth.MicrosoftAuthManager
import com.ankheye.pulsarsync.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var fitbitAuthManager: FitbitAuthManager
    private lateinit var microsoftAuthManager: MicrosoftAuthManager

    // Register a standard activity result launcher for AppAuth
    private val fitbitAuthLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            fitbitAuthManager.handleAuthorizationResponse(
                result.data!!,
                onSuccess = { accessToken, _ ->
                    viewModel.setFitbitToken(accessToken)
                },
                onError = {
                    viewModel.logStatus("Fitbit Auth Error: ${it.message}")
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fitbitAuthManager = FitbitAuthManager(this)
        microsoftAuthManager = MicrosoftAuthManager(
            context = this,
            onTokenAcquired = { token ->
                viewModel.setMicrosoftToken(token)
            },
            onAuthError = { e ->
                // Don't show an error to the user just because they aren't logged in yet
                if (e.message != "No active account found") {
                    viewModel.logStatus("Microsoft Silent Auth Error: ${e.message}")
                }
            }
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen(
                        viewModel = viewModel,
                        onConnectFitbit = {
                            val intent = fitbitAuthManager.getAuthorizationRequestIntent()
                            fitbitAuthLauncher.launch(intent)
                        },
                        onConnectMicrosoft = {
                            microsoftAuthManager.signIn(
                                activity = this,
                                onSuccess = { token ->
                                    viewModel.setMicrosoftToken(token)
                                },
                                onError = {
                                    viewModel.logStatus("Microsoft Auth Error: ${it.message}")
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fitbitAuthManager.isInitialized) {
            fitbitAuthManager.dispose()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    viewModel: MainViewModel,
    onConnectFitbit: () -> Unit,
    onConnectMicrosoft: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fitbit to OneDrive Sync") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Connect your accounts to start syncing.", style = MaterialTheme.typography.bodyLarge)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onConnectFitbit,
                    enabled = !uiState.isFitbitConnected
                ) {
                    Text(if (uiState.isFitbitConnected) "Fitbit Connected" else "Connect Fitbit")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onConnectMicrosoft,
                    enabled = !uiState.isMicrosoftConnected
                ) {
                    Text(if (uiState.isMicrosoftConnected) "Microsoft Connected" else "Connect Microsoft (OneDrive)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.syncYesterdayData() },
                enabled = uiState.isFitbitConnected && uiState.isMicrosoftConnected && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Sync Yesterday's Data")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Log:", style = MaterialTheme.typography.titleMedium)
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
