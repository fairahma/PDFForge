package com.pdfforge.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navy = ThemePrefs.foreground(this)
        val blue = Color.rgb(117, 91, 255)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(24))
            setBackgroundColor(ThemePrefs.background(this@MainActivity))
        }
        root.addView(TextView(this).apply {
            text = "PDFForge"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
        })
        val settings = TextView(this).apply {
            text = "⚙  Appearance"
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ThemePrefs.accent)
            setPadding(0, 0, 0, dp(10))
            setOnClickListener {
                val modes = arrayOf("Follow system", "Light", "Dark")
                val values = arrayOf("system", "light", "dark")
                val current = values.indexOf(ThemePrefs.mode(this@MainActivity)).coerceAtLeast(0)
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("App appearance")
                    .setSingleChoiceItems(modes, current) { dialog, index ->
                        ThemePrefs.set(this@MainActivity, values[index])
                        dialog.dismiss()
                        recreate()
                    }.setNegativeButton("Cancel", null).show()
            }
        }
        root.addView(settings)
        root.addView(TextView(this).apply {
            text = "Your PDF studio • Private & offline"
            textSize = 15f
            setTextColor(ThemePrefs.secondary(this@MainActivity))
            setPadding(0, dp(5), 0, dp(22))
        })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(69, 54, 157), Color.rgb(48, 107, 226))).apply { cornerRadius = dp(20).toFloat() }
        }
        hero.addView(TextView(this).apply {
            text = "All the PDF tools you need"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        hero.addView(TextView(this).apply {
            text = "Fast, simple and secure. Everything stays on your device."
            textSize = 14f
            setTextColor(Color.rgb(218, 231, 255))
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(hero)
        root.addView(TextView(this).apply {
            text = "YOUR TOOLS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
            setPadding(0, dp(28), 0, dp(14))
        })
        val tools = listOf(
            Triple("▤", "Merge PDFs", "merge"),
            Triple("✂", "Split / Extract", "pages"),
            Triple("▥", "Organize", "organize"),
            Triple("⇣", "Compress", "compress"),
            Triple("▧", "Images to PDF", "images")
        )
        val grid = GridLayout(this).apply { columnCount = 2 }
        val width = (resources.displayMetrics.widthPixels - dp(52)) / 2
        tools.forEach { (_, name, op) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(17), dp(18), dp(12), dp(18))
                background = shape(if (ThemePrefs.dark(this@MainActivity)) when(op) { "merge" -> Color.rgb(75, 52, 85); "pages" -> Color.rgb(36, 66, 100); "organize" -> Color.rgb(30, 79, 78); "compress" -> Color.rgb(94, 68, 43); else -> Color.rgb(75, 56, 106) } else when(op) { "merge" -> Color.rgb(255, 225, 230); "pages" -> Color.rgb(217, 238, 255); "organize" -> Color.rgb(215, 249, 237); "compress" -> Color.rgb(255, 239, 203); else -> Color.rgb(237, 224, 255) }, 18)
                elevation = dp(2).toFloat()
                setOnClickListener {
                    val destination = if (op == "merge" || op == "organize" || op == "pages") PageEditorActivity::class.java else ToolActivity::class.java
                    startActivity(Intent(this@MainActivity, destination).putExtra("tool", if (op == "pages") "extract" else op))
                }
            }
            card.addView(ToolIcon(this, op, blue), LinearLayout.LayoutParams(dp(36), dp(36)))
            card.addView(TextView(this).apply {
                text = name
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemePrefs.foreground(this@MainActivity))
                setPadding(0, dp(12), 0, 0)
            })
            card.addView(TextView(this).apply { text = when(op) { "merge" -> "Combine documents"; "pages" -> "Select or split pages"; "organize" -> "Reorder and remove"; "compress" -> "Reduce file size"; else -> "Convert photos" }; textSize = 11f; setTextColor(ThemePrefs.secondary(this@MainActivity)); setPadding(0, dp(6), 0, 0) })
            grid.addView(card, GridLayout.LayoutParams().apply {
                this.width = width - dp(8)
                height = dp(148)
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
        }
        root.addView(grid)
        root.addView(TextView(this).apply {
            text = "Private by design • Files stay on your device"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(182, 200, 229))
            setPadding(0, dp(24), 0, 0)
        })
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        SafeArea.apply(this, scroll)
    }
}
