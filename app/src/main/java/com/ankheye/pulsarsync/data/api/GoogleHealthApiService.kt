package com.ankheye.pulsarsync.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import okhttp3.ResponseBody

interface GoogleHealthApiService {
    @GET("v4/users/me/profile")
    suspend fun getProfile(
        @Header("Authorization") authHeader: String
    ): ResponseBody

    @GET("v4/users/me/dataTypes/{dataType}/dataPoints")
    suspend fun getDataPoints(
        @Header("Authorization") authHeader: String,
        @Path("dataType") dataType: String,
        @retrofit2.http.Query("filter") filter: String? = null
    ): ResponseBody
}
