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

        fun isSameCreator(creatorName: String?, currentUserName: String?): Boolean {
            val creator = creatorName?.trim().orEmpty()
            val user = currentUserName?.trim().orEmpty()
            if (creator.isBlank() || user.isBlank()) return true

            val c1 = creator.lowercase()
            val c2 = user.lowercase()
            if (c1 == c2 || c1.contains(c2) || c2.contains(c1)) return true

            val clean1 = c1.replace("@gmail.com", "").replace(".", " ").replace("_", " ").replace("owner", "").replace("admin", "").trim()
            val clean2 = c2.replace("@gmail.com", "").replace(".", " ").replace("_", " ").replace("owner", "").replace("admin", "").trim()

            if (clean1.isNotBlank() && clean2.isNotBlank()) {
                return clean1.contains(clean2) || clean2.contains(clean1)
            }
            return false
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
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
