package com.ankheye.pulsarsync.data.repository

import com.ankheye.pulsarsync.data.api.FitbitApiService
import com.ankheye.pulsarsync.data.model.FitbitActivityCategoryResponse
import com.ankheye.pulsarsync.data.model.FitbitActivityResponse
import com.ankheye.pulsarsync.data.model.FitbitCategory
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FitbitRepository {

    private val apiService: FitbitApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.fitbit.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        apiService = retrofit.create(FitbitApiService::class.java)
    }

    suspend fun fetchDailySummary(accessToken: String, date: String): FitbitActivityResponse {
        return apiService.getDailyActivitySummary("Bearer $accessToken", date)
    }

    suspend fun fetchAllActivities(accessToken: String): List<FitbitCategory> {
        return apiService.getActivitiesTree("Bearer $accessToken").categories
    }

    suspend fun fetchActivityDetails(accessToken: String, logId: Long): String {
        return apiService.getActivityDetails("Bearer $accessToken", logId).string()
    }

    suspend fun fetchIntradayHeartRate(accessToken: String, date: String, startTime: String, endTime: String): String {
        return apiService.getIntradayHeartRate("Bearer $accessToken", date, startTime, endTime).string()
    }
}
