package com.ankheye.pulsarsync.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OneDriveRepository(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun uploadFile(
        accessToken: String,
        folderPath: String,
        fileName: String,
        fileContent: String,
        contentType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = if (folderPath.isNotEmpty() && folderPath != "Apps/PulsarSync") {
                "${folderPath.trimEnd('/')}/$fileName"
            } else {
                fileName
            }
            val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/${path.replace(" ", "%20")}:/content"
            
            val requestBody = fileContent.toRequestBody(contentType.toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string()
                    return@withContext Result.failure(IOException("Unexpected code $response, body: $responseBody"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
