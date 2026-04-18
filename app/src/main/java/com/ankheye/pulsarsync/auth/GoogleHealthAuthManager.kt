package com.ankheye.pulsarsync.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import com.ankheye.pulsarsync.BuildConfig

class GoogleHealthAuthManager(private val context: Context) {

    private val authService = AuthorizationService(context)

    companion object {
        const val REDIRECT_URI = "com.ankheye.pulsarsync:/oauth2redirect"
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val SCOPE = "https://www.googleapis.com/auth/googlehealth.activity_and_fitness"
    }

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(AUTH_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT)
    )

    fun getAuthorizationRequestIntent(): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.GOOGLE_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        ).setScope(SCOPE).build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    fun handleAuthorizationResponse(
        intent: Intent,
        onSuccess: (String, String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val response = net.openid.appauth.AuthorizationResponse.fromIntent(intent)
        val ex = net.openid.appauth.AuthorizationException.fromIntent(intent)

        if (response != null) {
            // Google does not require clientSecret for native Android apps, only the ClientId is verified
            val tokenRequest: TokenRequest = response.createTokenExchangeRequest()
            
            authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken ?: ""
                    val refreshToken = tokenResponse.refreshToken ?: ""
                    onSuccess(accessToken, refreshToken)
                } else {
                    onError(exception ?: Exception("Unknown Token Error"))
                }
            }
        } else if (ex != null) {
            onError(ex)
        }
    }
    
    fun dispose() {
        authService.dispose()
    }
}
