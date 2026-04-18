package com.ankheye.pulsarsync.data.repository

import com.ankheye.pulsarsync.data.api.GoogleHealthApiService
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GoogleHealthRepository {

    private val apiService: GoogleHealthApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://health.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        apiService = retrofit.create(GoogleHealthApiService::class.java)
    }

    suspend fun fetchProfile(accessToken: String): String {
        return apiService.getProfile("Bearer $accessToken").string()
    }

    suspend fun fetchDataPoints(accessToken: String, dataType: String, filter: String? = null): String {
        return apiService.getDataPoints("Bearer $accessToken", dataType, filter).string()
    }
}
