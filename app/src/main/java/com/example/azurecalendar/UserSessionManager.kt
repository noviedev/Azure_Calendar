package com.example.azurecalendar

import android.content.Context
import android.content.SharedPreferences

class UserSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "azure_calendar_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
    }

    fun saveSession(username: String, role: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USERNAME, username)
            putString(KEY_ROLE, role)
            apply()
        }
    }

    fun getRole(): String? {
        return if (prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            prefs.getString(KEY_ROLE, null)
        } else {
            null
        }
    }

    fun getUsername(): String {
        return prefs.getString(KEY_USERNAME, "Admin") ?: "Admin"
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
