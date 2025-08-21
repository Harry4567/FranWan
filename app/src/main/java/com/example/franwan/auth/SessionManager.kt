package com.example.franwan.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuthToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun getAuthToken(): String? = sharedPreferences.getString(KEY_AUTH_TOKEN, null)

    fun isLoggedIn(): Boolean = !getAuthToken().isNullOrEmpty()

    fun isGuestMode(): Boolean = sharedPreferences.getBoolean(KEY_GUEST_MODE, false)

    fun setGuestMode(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_GUEST_MODE, enabled)
            .apply()
    }

    fun canAccessApp(): Boolean = isLoggedIn() || isGuestMode()

    fun saveUserDisplayName(name: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    fun getUserDisplayName(): String? = sharedPreferences.getString(KEY_USER_NAME, null)

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE = "secure_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_GUEST_MODE = "guest_mode"
    }
}


