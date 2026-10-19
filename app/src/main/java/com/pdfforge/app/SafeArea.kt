package com.pdfforge.app

import android.app.Activity
import android.view.View
import android.view.WindowInsets

/** Keep content clear of Android status and navigation bars, including edge-to-edge devices. */
object SafeArea {
    fun apply(activity: Activity, root: View) {
        activity.window.statusBarColor = ThemePrefs.background(activity)
        activity.window.navigationBarColor = ThemePrefs.background(activity)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = if (ThemePrefs.dark(activity)) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(
                left + insets.systemWindowInsetLeft,
                top + insets.systemWindowInsetTop,
                right + insets.systemWindowInsetRight,
                bottom + insets.systemWindowInsetBottom
            )
            insets
        }
        root.requestApplyInsets()
    }
}
