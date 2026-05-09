package com.akslabs.circletosearch.utils

import android.content.Context
import android.content.SharedPreferences

class UIPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_SHOW_GRADIENT_BORDER = "show_gradient_border"
        private const val KEY_SHOW_FRIENDLY_MESSAGES = "show_friendly_messages"
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }
    
    fun setDarkMode(isEnabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, isEnabled).apply()
    }
    
    fun isShowGradientBorder(): Boolean {
        return prefs.getBoolean(KEY_SHOW_GRADIENT_BORDER, true)
    }
    
    fun setShowGradientBorder(isEnabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_GRADIENT_BORDER, isEnabled).apply()
    }

    fun isShowFriendlyMessages(): Boolean {
        return prefs.getBoolean(KEY_SHOW_FRIENDLY_MESSAGES, true)
    }

    fun setShowFriendlyMessages(isEnabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FRIENDLY_MESSAGES, isEnabled).apply()
    }
}
