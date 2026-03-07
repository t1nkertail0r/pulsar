package com.ankheye.pulsarsync.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import java.io.File
import java.io.FileOutputStream

class MicrosoftAuthManager(
    private val context: Context,
    private val onTokenAcquired: ((String) -> Unit)? = null,
    private val onAuthError: ((Exception) -> Unit)? = null
) {

    private var msalApp: IPublicClientApplication? = null
    private val scopes = arrayOf("Files.ReadWrite.AppFolder", "User.Read")
    private val TAG = "MicrosoftAuthManager"

    init {
        // Create a temporary configuration file for MSAL initialization
        // Normally this is in res/raw/auth_config_single_account.json
        val configFile = File(context.cacheDir, "msal_config.json")
        val configJson = """
        {
          "client_id": "5ca54ee8-677a-49ce-806c-7c2d72f5ea77",
          "authorization_user_agent": "DEFAULT",
          "redirect_uri": "msauth://com.ankheye.pulsarsync/hpu8za%2BPiOjX9nNtLCV6XlXHlJI%3D",
          "account_mode": "SINGLE",
          "authorities": [
            {
              "type": "AAD",
              "audience": {
                "type": "AzureADandPersonalMicrosoftAccount"
              }
            }
          ]
        }
        """.trimIndent()
        FileOutputStream(configFile).use { it.write(configJson.toByteArray()) }

        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            configFile,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: com.microsoft.identity.client.ISingleAccountPublicClientApplication) {
                    msalApp = application
                    
                    // Attempt to load the signed-in account silently on startup
                    acquireTokenSilent(
                        onSuccess = { token -> onTokenAcquired?.invoke(token) },
                        onError = { exception -> onAuthError?.invoke(exception) }
                    )
                }

                override fun onError(exception: MsalException) {
                    Log.e(TAG, "Error initializing MSAL App: ${exception.message}", exception)
                    exception.printStackTrace()
                }
            }
        )
    }

    fun signIn(activity: Activity, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        if (msalApp == null) {
            Log.e(TAG, "Cannot sign in, msalApp is null")
            onError(Exception("MSAL app not initialized"))
            return
        }
        
        val singleAccountApp = msalApp as? com.microsoft.identity.client.ISingleAccountPublicClientApplication
        if (singleAccountApp == null) {
            Log.e(TAG, "msalApp is not an ISingleAccountPublicClientApplication")
            onError(Exception("Invalid MSAL app type"))
            return
        }

        singleAccountApp.signIn(activity, null, scopes, object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                onSuccess(authenticationResult.accessToken)
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "Interactive token error: ${exception.message}", exception)
                onError(exception)
            }

            override fun onCancel() {
                onError(Exception("User cancelled sign in"))
            }
        })
    }

    fun acquireTokenSilent(onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val singleAccountApp = msalApp as? com.microsoft.identity.client.ISingleAccountPublicClientApplication
        if (singleAccountApp == null) {
            onError(Exception("MSAL app not initialized"))
            return
        }

        singleAccountApp.getCurrentAccountAsync(object : com.microsoft.identity.client.ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: com.microsoft.identity.client.IAccount?) {
                if (activeAccount != null) {
                    singleAccountApp.acquireTokenSilentAsync(
                        scopes,
                        activeAccount.authority,
                        object : com.microsoft.identity.client.SilentAuthenticationCallback {
                            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                onSuccess(authenticationResult.accessToken)
                            }
                            override fun onError(exception: MsalException) {
                                onError(exception)
                            }
                        }
                    )
                } else {
                    onError(Exception("No active account found"))
                }
            }
            override fun onAccountChanged(priorAccount: com.microsoft.identity.client.IAccount?, currentAccount: com.microsoft.identity.client.IAccount?) {}
            override fun onError(exception: MsalException) {
                onError(exception)
            }
        })
    }
}
