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
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class Tool(
        val title: String, val subtitle: String, val operation: String,
        val icon: Int, val start: Int, val end: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = ThemePrefs.dark(this)
        val foreground = ThemePrefs.foreground(this)
        val background = ThemePrefs.background(this)
        window.statusBarColor = background
        window.navigationBarColor = background
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(20))
            setBackgroundColor(background)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "PDFForge"
            textSize = 29f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(foreground)
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        header.addView(TextView(this).apply {
            text = "⚙"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(foreground)
            contentDescription = "Appearance settings"
            setOnClickListener {
                val choices = arrayOf("Follow system", "Light", "Dark")
                val values = arrayOf("system", "light", "dark")
                val selected = values.indexOf(ThemePrefs.mode(this@MainActivity)).coerceAtLeast(0)
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Appearance")
                    .setSingleChoiceItems(choices, selected) { dialog, index ->
                        ThemePrefs.set(this@MainActivity, values[index])
                        dialog.dismiss()
                        recreate()
                    }.setNegativeButton("Cancel", null).show()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Your PDF studio  •  Private & offline"
            textSize = 13f
            setTextColor(ThemePrefs.secondary(this@MainActivity))
            setPadding(dp(2), 0, 0, dp(16))
        })
        root.addView(TextView(this).apply {
            text = "ALL TOOLS"
            textSize = 12f
            letterSpacing = 0.13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (dark) Color.rgb(190, 199, 222) else Color.rgb(87, 99, 120))
            setPadding(dp(2), dp(4), 0, dp(12))
        })
        val tools = listOf(
            Tool("Images to PDF", "Convert your photos into a PDF", "images", R.drawable.ic_image_to_pdf, Color.rgb(255, 110, 102), Color.rgb(218, 28, 106)),
            Tool("Merge PDFs", "Combine multiple PDFs into one", "merge", R.drawable.ic_merge_pdf, Color.rgb(167, 74, 245), Color.rgb(72, 32, 209)),
            Tool("Split / Extract", "Select pages or split documents", "pages", R.drawable.ic_split_pdf, Color.rgb(48, 219, 148), Color.rgb(0, 151, 169)),
            Tool("Compress PDF", "Reduce file size, keep quality", "compress", R.drawable.ic_compress_pdf, Color.rgb(255, 194, 49), Color.rgb(248, 91, 39)),
            Tool("Organize PDF", "Reorder, rotate and remove pages", "organize", R.drawable.ic_organize_pdf, Color.rgb(255, 80, 167), Color.rgb(194, 26, 128))
        )
        tools.forEach { tool ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(12), dp(14))
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(tool.start, tool.end)).apply {
                    cornerRadius = dp(19).toFloat()
                }
                elevation = dp(3).toFloat()
                isClickable = true
                isFocusable = true
                contentDescription = "${tool.title}. ${tool.subtitle}"
                setOnClickListener {
                    val destination = if (tool.operation in listOf("merge", "organize", "pages")) PageEditorActivity::class.java else ToolActivity::class.java
                    val operation = if (tool.operation == "pages") "extract" else tool.operation
                    startActivity(Intent(this@MainActivity, destination).putExtra("tool", operation))
                }
            }
            card.addView(ImageView(this).apply {
                setImageResource(tool.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(68), dp(68)))
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(6), 0)
            }
            labels.addView(TextView(this).apply {
                text = tool.title
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            labels.addView(TextView(this).apply {
                text = tool.subtitle
                textSize = 12f
                setTextColor(Color.rgb(255, 247, 251))
                maxLines = 2
                setPadding(0, dp(5), 0, 0)
            })
            card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply {
                text = "›"
                textSize = 32f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(0x35FFFFFF)
                    shape = GradientDrawable.OVAL
                }
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            root.addView(card, LinearLayout.LayoutParams(-1, dp(110)).apply {
                bottomMargin = dp(12)
            })
        }
        root.addView(TextView(this).apply {
            text = "Private by design  •  Files stay on your device"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(ThemePrefs.secondary(this@MainActivity))
            setPadding(0, dp(10), 0, dp(8))
        })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(background)
            clipToPadding = false
        }
        scroll.addView(root)
        setContentView(scroll)
        SafeArea.apply(this, scroll)
    }
}
