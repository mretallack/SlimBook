package com.slimbook.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class FilterManager(private val context: Context) {

    companion object {
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/mretallack/SlimBook/main/filter.js"
        private const val TIMEOUT_MS = 5000
    }

    private var cachedJs: String? = null

    suspend fun getFilterJs(): String {
        cachedJs?.let { return it }
        val js = fetchRemote() ?: loadBundled()
        cachedJs = js
        return js
    }

    private fun fetchRemote(): String? {
        return try {
            val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBundled(): String {
        return context.assets.open("filter.js").bufferedReader().use { it.readText() }
    }
}
