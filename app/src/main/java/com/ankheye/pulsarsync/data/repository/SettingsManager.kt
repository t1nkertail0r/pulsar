package com.ankheye.pulsarsync.data.repository

import android.content.Context
import com.ankheye.pulsarsync.data.model.AppSettings
import com.ankheye.pulsarsync.data.model.FavoriteActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsManager(private val context: Context) {
    private val fileName = "settings.json"

    fun loadSettings(): AppSettings {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return AppSettings()

        return try {
            val jsonStr = file.readText()
            val jsonObject = JSONObject(jsonStr)
            
            val favoriteActivities = mutableListOf<FavoriteActivity>()
            if (jsonObject.has("favoriteActivities")) {
                val jsonArray = jsonObject.getJSONArray("favoriteActivities")
                for (i in 0 until jsonArray.length()) {
                    val activityObj = jsonArray.getJSONObject(i)
                    favoriteActivities.add(
                        FavoriteActivity(
                            id = activityObj.getLong("id"),
                            name = activityObj.getString("name")
                        )
                    )
                }
            }
            
            AppSettings(favoriteActivities)
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            val jsonObject = JSONObject()
            
            val jsonArray = JSONArray()
            settings.favoriteActivities.forEach {
                val activityObj = JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                }
                jsonArray.put(activityObj)
            }
            jsonObject.put("favoriteActivities", jsonArray)
            
            val file = File(context.filesDir, fileName)
            file.writeText(jsonObject.toString(4))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
