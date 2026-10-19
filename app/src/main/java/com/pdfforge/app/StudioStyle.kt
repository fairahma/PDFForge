package com.pdfforge.app

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

/** Shared spacing, typography and surfaces for all PDFForge screens. */
object StudioStyle {
    fun dp(activity: Activity, value: Int) =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    fun surface(activity: Activity, radius: Int = 16, selected: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (selected) {
                if (ThemePrefs.dark(activity)) android.graphics.Color.rgb(48, 40, 91)
                else android.graphics.Color.rgb(239, 235, 255)
            } else ThemePrefs.surface(activity))
            cornerRadius = dp(activity, radius).toFloat()
            setStroke(dp(activity, if (selected) 2 else 1),
                if (selected) ThemePrefs.accent else if (ThemePrefs.dark(activity))
                    android.graphics.Color.rgb(57, 74, 104)
                else android.graphics.Color.rgb(224, 230, 241))
        }

    fun text(activity: Activity, view: TextView, size: Float = 14f, heading: Boolean = false) {
        view.textSize = size
        view.typeface = if (heading) Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        else Typeface.create("sans-serif", Typeface.NORMAL)
        view.setTextColor(ThemePrefs.foreground(activity))
        view.includeFontPadding = false
    }
}
