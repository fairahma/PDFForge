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
        val navy = Color.WHITE
        val blue = Color.rgb(242, 90, 96)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(34), dp(20), dp(20))
            setBackgroundColor(Color.rgb(16, 29, 53))
        }
        root.addView(TextView(this).apply {
            text = "PDFForge"
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
        })
        root.addView(TextView(this).apply {
            text = "Your PDF studio • Private & offline"
            textSize = 15f
            setTextColor(Color.rgb(182, 200, 229))
            setPadding(0, dp(5), 0, dp(28))
        })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = shape(Color.rgb(37, 61, 102), 22)
        }
        hero.addView(TextView(this).apply {
            text = "Create something great."
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        hero.addView(TextView(this).apply {
            text = "Choose a tool below to open its own workspace."
            textSize = 14f
            setTextColor(Color.rgb(218, 231, 255))
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(hero)
        root.addView(TextView(this).apply {
            text = "ALL TOOLS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
            setPadding(0, dp(28), 0, dp(14))
        })
        val tools = listOf(
            Triple("📑", "Merge PDFs", "merge"),
            Triple("🗂", "Organize", "organize"),
            Triple("📤", "Extract pages", "extract"),
            Triple("✂️", "Split PDF", "split"),
            Triple("🗜", "Compress", "compress"),
            Triple("🖼", "Images to PDF", "images")
        )
        val grid = GridLayout(this).apply { columnCount = 2 }
        val width = (resources.displayMetrics.widthPixels - dp(52)) / 2
        tools.forEach { (symbol, name, op) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(17), dp(18), dp(12), dp(18))
                background = shape(Color.rgb(35, 53, 82), 18)
                elevation = dp(2).toFloat()
                setOnClickListener {
                    val destination = if (op == "merge" || op == "organize" || op == "extract") PageEditorActivity::class.java else ToolActivity::class.java
                    startActivity(Intent(this@MainActivity, destination).putExtra("tool", op))
                }
            }
            card.addView(TextView(this).apply {
                text = symbol
                textSize = 32f
                setTextColor(blue)
            })
            card.addView(TextView(this).apply {
                text = name
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, dp(12), 0, 0)
            })
            grid.addView(card, GridLayout.LayoutParams().apply {
                this.width = width - dp(8)
                height = dp(130)
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
    }
}
