package com.waxew.hesabyar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A small local history store. SharedPreferences keeps v1.0 lightweight and survives app updates. */
data class HistoryEntry(
    val id: Long,
    val title: String,
    val details: String,
    val result: String,
    val createdAt: Long = System.currentTimeMillis()
)

class HistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("hesabyar_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    HistoryEntry(
                        id = item.getLong("id"),
                        title = item.getString("title"),
                        details = item.getString("details"),
                        result = item.getString("result"),
                        createdAt = item.getLong("createdAt")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(items: List<HistoryEntry>) {
        val array = JSONArray()
        items.take(100).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("details", entry.details)
                    .put("result", entry.result)
                    .put("createdAt", entry.createdAt)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
