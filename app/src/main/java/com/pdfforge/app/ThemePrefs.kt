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
    fun background(activity: Activity) = if (dark(activity)) Color.rgb(15, 23, 35) else Color.rgb(246, 248, 250)
    fun surface(activity: Activity) = if (dark(activity)) Color.rgb(28, 40, 55) else Color.WHITE
    fun foreground(activity: Activity) = if (dark(activity)) Color.WHITE else Color.rgb(25, 37, 51)
    fun secondary(activity: Activity) = if (dark(activity)) Color.rgb(168, 185, 200) else Color.rgb(99, 115, 131)
    val accent = Color.rgb(65, 190, 169)
}
