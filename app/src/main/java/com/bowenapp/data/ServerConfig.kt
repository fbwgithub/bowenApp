package com.bowenapp.data

import android.content.Context
import android.content.SharedPreferences

object ServerConfig {
    private const val PREF_NAME = "bowenapp_config"
    private const val KEY_BASE_URL = "base_url"
    const val DEFAULT_URL = "http://192.168.1.12:5000/"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(): String {
        val url = prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
        return if (url.endsWith("/")) url else "$url/"
    }

    fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun getBaseIp(): String {
        val url = getBaseUrl()
        return url.removePrefix("http://").removePrefix("https://").removeSuffix("/")
    }
}
