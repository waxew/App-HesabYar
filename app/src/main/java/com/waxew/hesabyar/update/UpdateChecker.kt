package com.waxew.hesabyar.update

import com.waxew.hesabyar.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String
)

object UpdateChecker {
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/waxew/App-HesabYar/main/distribution/latest.json"

    fun check(): UpdateInfo? = runCatching {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        if (connection.responseCode !in 200..299) return null
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val remoteCode = root.getInt("versionCode")
        if (remoteCode <= BuildConfig.VERSION_CODE) return null
        UpdateInfo(
            versionCode = remoteCode,
            versionName = root.getString("versionName"),
            downloadUrl = root.getString("downloadUrl"),
            notes = root.optString("notes", "نسخه جدید حسابیار آماده است.")
        )
    }.getOrNull()
}
