package com.ankheye.pulsarsync.data.repository

import android.content.Context
import com.ankheye.pulsarsync.data.model.SyncTracker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SyncTrackerManager(private val context: Context) {
    private val fileName = "syncTracker.json"
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun loadTracker(): SyncTracker {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return SyncTracker()

        return try {
            val jsonStr = file.readText()
            val jsonObject = JSONObject(jsonStr)
            
            val lastSyncDateStr = jsonObject.optString("lastSyncDate", "")
            val lastSyncDate = if (lastSyncDateStr.isNotEmpty()) {
                LocalDate.parse(lastSyncDateStr, dateFormatter)
            } else {
                null
            }
            
            val syncedDates = mutableSetOf<LocalDate>()
            if (jsonObject.has("syncedDates")) {
                val jsonArray = jsonObject.getJSONArray("syncedDates")
                for (i in 0 until jsonArray.length()) {
                    syncedDates.add(LocalDate.parse(jsonArray.getString(i), dateFormatter))
                }
            }
            
            SyncTracker(lastSyncDate, syncedDates)
        } catch (e: Exception) {
            e.printStackTrace()
            SyncTracker()
        }
    }

    fun recordSuccessfulSync(date: LocalDate) {
        val tracker = loadTracker()
        tracker.syncedDates.add(date)
        val updatedTracker = tracker.copy(lastSyncDate = LocalDate.now())
        
        try {
            val jsonObject = JSONObject()
            updatedTracker.lastSyncDate?.let {
                jsonObject.put("lastSyncDate", it.format(dateFormatter))
            }
            
            val jsonArray = JSONArray()
            updatedTracker.syncedDates.forEach {
                jsonArray.put(it.format(dateFormatter))
            }
            jsonObject.put("syncedDates", jsonArray)
            
            val file = File(context.filesDir, fileName)
            file.writeText(jsonObject.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
