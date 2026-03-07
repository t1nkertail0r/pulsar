package com.ankheye.pulsarsync.data.api

import com.ankheye.pulsarsync.data.model.FitbitActivityCategoryResponse
import com.ankheye.pulsarsync.data.model.FitbitActivityResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface FitbitApiService {
    @GET("1/user/-/activities/date/{date}.json")
    suspend fun getDailyActivitySummary(
        @Header("Authorization") authHeader: String,
        @Path("date") date: String // format yyyy-MM-dd
    ): FitbitActivityResponse
    @GET("1/activities.json")
    suspend fun getActivitiesTree(
        @Header("Authorization") authHeader: String
    ): FitbitActivityCategoryResponse
}
