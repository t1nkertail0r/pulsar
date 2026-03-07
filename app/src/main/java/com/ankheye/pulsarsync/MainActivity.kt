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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ankheye.pulsarsync.auth.FitbitAuthManager
import com.ankheye.pulsarsync.auth.MicrosoftAuthManager
import com.ankheye.pulsarsync.ui.MainViewModel
import com.ankheye.pulsarsync.ui.screens.MainScreen
import com.ankheye.pulsarsync.ui.screens.SettingsScreen

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
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onConnectFitbit = {
                                    val intent = fitbitAuthManager.getAuthorizationRequestIntent()
                                    fitbitAuthLauncher.launch(intent)
                                },
                                onConnectMicrosoft = {
                                    microsoftAuthManager.signIn(
                                        activity = this@MainActivity,
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fitbitAuthManager.isInitialized) {
            fitbitAuthManager.dispose()
        }
    }
}


