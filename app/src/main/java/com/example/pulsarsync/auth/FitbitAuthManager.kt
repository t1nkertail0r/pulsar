package com.example.pulsarsync.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest

class FitbitAuthManager(private val context: Context) {

    private val authService = AuthorizationService(context)

    companion object {
        const val CLIENT_ID = "23V3Z5"
        const val REDIRECT_URI = "pulsarsync://callback"
        const val AUTH_ENDPOINT = "https://www.fitbit.com/oauth2/authorize"
        const val TOKEN_ENDPOINT = "https://api.fitbit.com/oauth2/token"
        const val SCOPE = "activity profile"
    }

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(AUTH_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT)
    )

    fun getAuthorizationRequestIntent(): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
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
