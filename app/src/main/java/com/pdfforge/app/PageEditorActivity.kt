package com.pdfforge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlin.concurrent.thread

class PageEditorActivity : Activity() {
    private data class PageRef(val source: Int, val page: Int)
    private val sources = mutableListOf<File>()
    private val pages = mutableListOf<PageRef>()
    private lateinit var grid: GridLayout
    private lateinit var info: TextView
    private lateinit var save: Button
    private var output: File? = null
    private var mode = "organize"
    private val pickRequest = 201
    private val saveRequest = 202
    private val navy = Color.rgb(235, 242, 255)
    private val blue = Color.rgb(242, 90, 96)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, size: Float = 16f, color: Int = navy) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("tool") ?: "organize"
        title = "PDFForge"
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 29, 53))
            setPadding(dp(16), dp(18), dp(16), dp(10))
        }
        val header = label("‹  " + when(mode) {
            "merge" -> "Merge PDFs"
            "extract" -> "Extract pages"
            else -> "Organize PDF"
        }, 26f)
        header.setOnClickListener { finish() }
        outer.addView(header)
        outer.addView(label("PDFForge Studio  •  Visual page editor", 13f, Color.rgb(242, 90, 96)))
        outer.addView(label("Hold and drag a page to reorder. Tap a page to view or remove it.", 13f, Color.rgb(182, 200, 229)))
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val add = Button(this).apply {
            text = "+ Add PDF"
            isAllCaps = false
            setOnClickListener { pickFiles() }
        }
        toolbar.addView(add, LinearLayout.LayoutParams(0, -2, 1f))
        val preview = Button(this).apply {
            text = "Preview"
            isAllCaps = false
            setOnClickListener { buildResult(true) }
        }
        toolbar.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
        add.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(65, 92, 137))
        add.setTextColor(Color.WHITE)
        preview.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(65, 92, 137))
        preview.setTextColor(Color.WHITE)
        outer.addView(toolbar)
        info = label("Choose a PDF to start editing.", 14f)
        info.setPadding(0, dp(12), 0, dp(12))
        outer.addView(info)
        val scroll = ScrollView(this)
        grid = GridLayout(this).apply { columnCount = 2 }
        scroll.addView(grid)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        save = Button(this).apply {
            text = "Preview & Save PDF"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue)
            isEnabled = false
            setOnClickListener { buildResult(true) }
        }
        outer.addView(save, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(outer)
        pickFiles()
    }
    private fun pickFiles() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mode == "merge")
        }
        startActivityForResult(picker, pickRequest)
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == saveRequest) {
            val file = output ?: return
            try {
                contentResolver.openOutputStream(data.data!!)?.use { stream ->
                    file.inputStream().use { it.copyTo(stream) }
                }
                Toast.makeText(this, "PDF saved", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { info.text = "Save failed: ${e.message}" }
            return
        }
        if (requestCode != pickRequest) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
        data.data?.let { if (it !in uris) uris.add(it) }
        if (uris.isEmpty()) return
        info.text = "Opening PDF pages..."
        thread {
            try {
                val newFiles = mutableListOf<File>()
                val newPages = mutableListOf<PageRef>()
                uris.forEach { uri ->
                    val local = File.createTempFile("editor_", ".pdf", cacheDir)
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            local.outputStream().use { input.copyTo(it) }
                        } ?: error("Unable to open PDF")
                        PDDocument.load(local).use { doc ->
                            val sourceIndex = sources.size + newFiles.size
                            for (i in 0 until doc.numberOfPages) newPages.add(PageRef(sourceIndex, i))
                        }
                        newFiles.add(local)
                    } catch (e: Exception) { local.delete(); throw e }
                }
                runOnUiThread {
                    if (mode != "merge") { sources.forEach { it.delete() }; sources.clear(); pages.clear() }
                    sources.addAll(newFiles)
                    pages.addAll(newPages)
                    output = null
                    refresh()
                }
            } catch (e: Exception) {
                runOnUiThread { info.text = "Unable to open PDF: ${e.message}" }
            }
        }
    }
    private fun refresh() {
        grid.removeAllViews()
        info.text = "${pages.size} pages • Long-press and drag to rearrange"
        save.isEnabled = pages.isNotEmpty()
        val tileWidth = (resources.displayMetrics.widthPixels - dp(48)) / 2
        pages.toList().forEachIndexed { index, ref ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(Color.rgb(35, 53, 82))
                elevation = dp(2).toFloat()
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.rgb(225, 233, 246))
            }
            tile.addView(image, LinearLayout.LayoutParams(-1, dp(165)))
            tile.addView(label("Page ${index + 1}", 14f))
            tile.setOnClickListener {
                AlertDialog.Builder(this).setTitle("Page ${index + 1}")
                    .setItems(arrayOf("View page", "Remove page")) { _, choice ->
                        if (choice == 0) showSourcePage(ref)
                        else { pages.removeAt(index); output = null; refresh() }
                    }.show()
            }
            tile.setOnLongClickListener {
                val data = ClipData.newPlainText("page", index.toString())
                tile.startDragAndDrop(data, View.DragShadowBuilder(tile), index, 0)
                true
            }
            tile.setOnDragListener { _, event ->
                if (event.action == DragEvent.ACTION_DROP) {
                    val from = event.localState as? Int ?: return@setOnDragListener true
                    if (from in pages.indices && index in pages.indices && from != index) {
                        val item = pages.removeAt(from)
                        pages.add(index, item)
                        output = null
                        refresh()
                    }
                }
                true
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = tileWidth - dp(8)
                height = -2
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
            thread {
                try {
                    val bitmap = renderPage(sources[ref.source], ref.page, 300)
                    runOnUiThread { if (tile.parent != null) image.setImageBitmap(bitmap) else bitmap.recycle() }
                } catch (_: Exception) { }
            }
        }
    }
    private fun renderPage(file: File, index: Int, maxWidth: Int): Bitmap {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val scale = maxWidth.toFloat() / page.width
                    val bitmap = Bitmap.createBitmap(maxWidth, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
    private fun showSourcePage(ref: PageRef) {
        thread {
            try {
                val bitmap = renderPage(sources[ref.source], ref.page, 900)
                runOnUiThread {
                    val view = ImageView(this).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                    }
                    AlertDialog.Builder(this).setTitle("Original page ${ref.page + 1}")
                        .setView(view).setPositiveButton("Close", null).show()
                }
            } catch (e: Exception) { runOnUiThread { info.text = "Preview failed: ${e.message}" } }
        }
    }
    private fun buildResult(showPreview: Boolean) {
        if (pages.isEmpty()) return
        save.isEnabled = false
        info.text = "Preparing PDF preview..."
        val snapshot = pages.toList()
        thread {
            try {
                val file = File(cacheDir, "edited_${System.currentTimeMillis()}.pdf")
                val docs = sources.map { PDDocument.load(it) }
                try {
                    PDDocument().use { result ->
                        snapshot.forEach { ref -> result.importPage(docs[ref.source].getPage(ref.page)) }
                        result.save(file)
                    }
                } finally { docs.forEach { it.close() } }
                output = file
                runOnUiThread {
                    save.isEnabled = true
                    info.text = "${snapshot.size} pages ready • Review before saving"
                    if (showPreview) showResult(file)
                }
            } catch (e: Exception) {
                runOnUiThread { info.text = "Processing failed: ${e.message}"; save.isEnabled = true }
            }
        }
    }
    private fun showResult(file: File) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pageImage = ImageView(this).apply { adjustViewBounds = true }
        val counter = label("", 15f)
        counter.gravity = Gravity.CENTER
        body.addView(counter)
        body.addView(pageImage, LinearLayout.LayoutParams(-1, dp(460)))
        val controls = LinearLayout(this)
        val previous = Button(this).apply { text = "Previous" }
        val next = Button(this).apply { text = "Next" }
        controls.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(controls)
        val dialog = AlertDialog.Builder(this).setTitle("Final PDF preview")
            .setView(body).setNegativeButton("Back to edit", null)
            .setPositiveButton("Save PDF") { _, _ -> saveOutput() }.create()
        dialog.show()
        var page = 0
        var generation = 0
        fun update() {
            val current = page
            val request = ++generation
            counter.text = "Loading..."
            thread {
                try {
                    val bitmap = renderPage(file, current, 700)
                    runOnUiThread {
                        if (dialog.isShowing && request == generation) {
                            pageImage.setImageBitmap(bitmap)
                            counter.text = "Page ${current + 1} of ${pages.size}"
                            previous.isEnabled = current > 0
                            next.isEnabled = current < pages.lastIndex
                        } else bitmap.recycle()
                    }
                } catch (e: Exception) { runOnUiThread { counter.text = "Preview failed: ${e.message}" } }
            }
        }
        previous.setOnClickListener { if (page > 0) { page--; update() } }
        next.setOnClickListener { if (page < pages.lastIndex) { page++; update() } }
        update()
    }
    private fun saveOutput() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "PDFForge_edited.pdf")
        }, saveRequest)
    }
    override fun onDestroy() {
        super.onDestroy()
        sources.forEach { it.delete() }
    }
}
