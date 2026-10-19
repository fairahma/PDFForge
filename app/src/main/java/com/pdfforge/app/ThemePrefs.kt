package com.pdfforge.app

import android.app.Activity
import android.graphics.Color

object ThemePrefs {
    private const val PREFS = "pdfforge_appearance"
    private const val KEY = "mode"
    fun mode(activity: Activity): String =
        activity.getSharedPreferences(PREFS, 0).getString(KEY, "system") ?: "system"
    fun dark(activity: Activity): Boolean = when (mode(activity)) {
        "light" -> false
        "dark" -> true
        else -> (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    fun set(activity: Activity, mode: String) {
        activity.getSharedPreferences(PREFS, 0).edit().putString(KEY, mode).apply()
    }
    fun background(activity: Activity) = if (dark(activity)) Color.rgb(16, 29, 53) else Color.rgb(248, 250, 255)
    fun surface(activity: Activity) = if (dark(activity)) Color.rgb(35, 53, 82) else Color.WHITE
    fun foreground(activity: Activity) = if (dark(activity)) Color.WHITE else Color.rgb(24, 39, 67)
    fun secondary(activity: Activity) = if (dark(activity)) Color.rgb(190, 207, 232) else Color.rgb(89, 108, 140)
    val accent = Color.rgb(107, 83, 245)
}
