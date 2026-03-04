package com.example.fitbitsync.data.format

import com.example.fitbitsync.data.model.FitbitActivityResponse
import com.google.gson.GsonBuilder

object DataFormatter {

    fun toCsv(response: FitbitActivityResponse, date: String): String {
        val sb = java.lang.StringBuilder()
        sb.append("Date,ActivityName,StartTime,Duration_ms,Calories,Distance,Steps\n")
        
        for (activity in response.activities) {
            sb.append(date).append(",")
            sb.append(activity.name.replace(",", "")).append(",")
            sb.append(activity.startTime).append(",")
            sb.append(activity.duration).append(",")
            sb.append(activity.calories).append(",")
            sb.append(activity.distance).append(",")
            sb.append(activity.steps).append("\n")
        }
        return sb.toString()
    }

    fun toJsonMetadata(response: FitbitActivityResponse, date: String): String {
        val metadata = mapOf(
            "date" to date,
            "goals" to response.goals,
            "summary" to response.summary
        )
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(metadata)
    }
}
