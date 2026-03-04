package com.example.fitbitsync.data.api

import com.example.fitbitsync.data.model.FitbitActivityResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface FitbitApiService {
    @GET("1/user/-/activities/date/{date}.json")
    suspend fun getDailyActivitySummary(
        @Header("Authorization") authHeader: String,
        @Path("date") date: String // format yyyy-MM-dd
    ): FitbitActivityResponse
}
