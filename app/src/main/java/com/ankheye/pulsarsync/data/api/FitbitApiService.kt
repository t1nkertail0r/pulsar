package com.ankheye.pulsarsync.data.api

import com.ankheye.pulsarsync.data.model.FitbitActivityCategoryResponse
import com.ankheye.pulsarsync.data.model.FitbitActivityResponse
import com.ankheye.pulsarsync.data.model.FitbitActivityListResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.Path
import okhttp3.ResponseBody

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
    @GET("1/user/-/activities/{logId}.json")
    suspend fun getActivityDetails(
        @Header("Authorization") authHeader: String,
        @Path("logId") logId: Long
    ): ResponseBody
    @GET("1/user/-/activities/heart/date/{date}/1d/1min/time/{start}/{end}.json")
    suspend fun getIntradayHeartRate(
        @Header("Authorization") authHeader: String,
        @Path("date") date: String,
        @Path("start") startTime: String, // HH:mm format
        @Path("end") endTime: String    // HH:mm format
    ): ResponseBody

    @GET("1/user/-/activities/list.json")
    suspend fun getActivityLogList(
        @Header("Authorization") authHeader: String,
        @Query("afterDate") afterDate: String,
        @Query("sort") sort: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): FitbitActivityListResponse
}
