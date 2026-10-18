package com.pdfforge.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlin.concurrent.thread
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ToolActivity : Activity() {
    private val pickCode = 10
    private val saveCode = 11
    private var operation = ""
    private var selected = emptyList<Uri>()
    private var result: File? = null
    private lateinit var status: TextView
    private lateinit var saveButton: Button
    private lateinit var previewButton: Button
    private lateinit var pagesInput: EditText
    private var resultIsZip = false
    private lateinit var filesPanel: LinearLayout
    private lateinit var runButton: Button
    private lateinit var selectionTitle: TextView
    private lateinit var workspace: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        operation = intent.getStringExtra("tool") ?: "images"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 24)
            setBackgroundColor(Color.rgb(244, 247, 251))
        }
        fun card(title: String, subtitle: String, op: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 24, 28, 24)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 28f
                    setStroke(1, Color.rgb(221, 229, 239))
                }
                setOnClickListener {
                    if (op == "merge" || op == "extract" || op == "organize") {
                        startActivity(Intent(this@ToolActivity, PageEditorActivity::class.java).putExtra("tool", op))
                        return@setOnClickListener
                    }
                    operation = op
                    result = null
                    saveButton.isEnabled = false
                    previewButton.isEnabled = false
                    selected = emptyList()
                    showSelectedFiles()
                    chooseFiles()
                }
            }
            card.addView(TextView(this).apply { text = title; textSize = 19f; setTextColor(Color.rgb(23, 60, 103)) })
            card.addView(TextView(this).apply { text = subtitle; textSize = 13f; setTextColor(Color.DKGRAY) })
            return card
        }
        layout.addView(TextView(this).apply { text = "PDFForge"; textSize = 34f; setTextColor(Color.rgb(23, 60, 103)); setPadding(0, 0, 0, 8) })
        layout.addView(TextView(this).apply { text = "Your private PDF workspace • Offline"; textSize = 15f; setPadding(0, 0, 0, 22) })
        layout.addView(TextView(this).apply { text = when(operation) { "images" -> "Images to PDF"; "split" -> "Split PDF"; else -> "Compress PDF" }; textSize = 25f; setTextColor(Color.rgb(23, 60, 103)); setPadding(0, 0, 0, 12) })
        workspace = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 28f
                setStroke(2, Color.rgb(218, 228, 242))
            }
        }
        selectionTitle = TextView(this).apply {
            text = "YOUR WORKSPACE"
            textSize = 18f
            setTextColor(Color.rgb(23, 60, 103))
            setPadding(0, 0, 0, 12)
        }
        workspace.addView(selectionTitle)
        filesPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        workspace.addView(filesPanel)
        workspace.addView(Button(this).apply {
            text = "+ Add / change files"
            isAllCaps = false
            setOnClickListener { if (operation.isNotEmpty()) chooseFiles() else status.text = "Choose a tool above first." }
        })
        layout.addView(workspace, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14; bottomMargin = 12 })
        runButton = Button(this).apply { text = "Process selected files"; isEnabled = false; setOnClickListener { processFiles() } }
        layout.addView(runButton)
        pagesInput = EditText(this).apply {
            hint = "Pages to extract: e.g. 1,3-5 (blank = all)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        pagesInput.visibility = android.view.View.GONE
        layout.addView(pagesInput)

        status = TextView(this).apply { text = "Choose a tool to begin."; textSize = 15f; setPadding(0, 30, 0, 16) }
        layout.addView(status)
        saveButton = Button(this).apply { text = "Save result"; isEnabled = false; setOnClickListener { saveResult() } }
        layout.addView(saveButton)
        previewButton = Button(this).apply { text = "Preview PDF result"; isEnabled = false; setOnClickListener { result?.let { preview(it) } } }
        layout.addView(previewButton)
        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
        showSelectedFiles()
        chooseFiles()
    }

    private fun chooseFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (operation == "images") "image/*" else "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, operation == "images" || operation == "merge")
        }
        startActivityForResult(intent, pickCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == pickCode) {
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
            data.data?.let { if (!uris.contains(it)) uris.add(it) }
            selected = uris
            if (uris.isEmpty()) return
            showSelectedFiles()
            runButton.isEnabled = true
            status.text = "Review files and tap Process selected files."
        } else if (requestCode == saveCode) {
            val file = result ?: return
            try {
                contentResolver.openOutputStream(data.data!!)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                status.text = "PDF saved successfully."
            } catch (e: Exception) { status.text = "Save failed: ${e.message}" }
        }
    }

    private fun showSelectedFiles() {
        filesPanel.removeAllViews()
        selectionTitle.text = "SELECTED FILES  •  ${selected.size}"
        if (selected.isEmpty()) {
            filesPanel.addView(TextView(this).apply {
                text = "No files selected yet. Choose a tool above to add your documents."
                textSize = 15f
                setTextColor(Color.GRAY)
                setPadding(0, 12, 0, 20)
            })
            if (::runButton.isInitialized) runButton.isEnabled = false
            return
        }
        selected.forEachIndexed { index, uri ->
            val metadata = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0) ?: "Document ${index + 1}"
                    val bytes = if (it.isNull(1)) 0L else it.getLong(1)
                    Pair(name, bytes)
                } else Pair("Document ${index + 1}", 0L)
            } ?: Pair("Document ${index + 1}", 0L)
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 14, 14, 14)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(245, 248, 253))
                    cornerRadius = 18f
                }
            }
            val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            heading.addView(TextView(this).apply {
                text = if (operation == "images") "▧" else "▣"
                textSize = 29f
                setTextColor(Color.rgb(219, 63, 77))
            })
            val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
            detail.addView(TextView(this).apply {
                text = metadata.first
                textSize = 15f
                setTextColor(Color.rgb(30, 43, 63))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            detail.addView(TextView(this).apply {
                text = if (metadata.second > 0) "${metadata.second / 1024} KB • Item ${index + 1}" else "Item ${index + 1}"
                textSize = 12f
                setTextColor(Color.GRAY)
            })
            heading.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            tile.addView(heading)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (operation != "images") {
                actions.addView(Button(this).apply {
                    text = "View"
                    isAllCaps = false
                    setOnClickListener { previewSelected(uri) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (operation == "merge" || operation == "images") {
                actions.addView(Button(this).apply {
                    text = "↑"
                    isEnabled = index > 0
                    setOnClickListener {
                        val list = selected.toMutableList()
                        list.add(index - 1, list.removeAt(index))
                        selected = list
                        showSelectedFiles()
                    }
                })
                actions.addView(Button(this).apply {
                    text = "↓"
                    isEnabled = index < selected.lastIndex
                    setOnClickListener {
                        val list = selected.toMutableList()
                        list.add(index + 1, list.removeAt(index))
                        selected = list
                        showSelectedFiles()
                    }
                })
            }
            actions.addView(Button(this).apply {
                text = "Remove"
                isAllCaps = false
                setOnClickListener {
                    selected = selected.filterIndexed { i, _ -> i != index }
                    showSelectedFiles()
                }
            })
            tile.addView(actions)
            filesPanel.addView(tile, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        if (::runButton.isInitialized) runButton.isEnabled = true
    }

    private fun previewSelected(uri: Uri) {
        status.text = "Preparing selected PDF preview..."
        thread {
            var temporary: File? = null
            try {
                temporary = copySource(uri)
                val file = temporary
                runOnUiThread {
                    status.text = "Previewing selected PDF"
                    preview(file, false)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Cannot preview: ${e.message}" }
            }
        }
    }

    private fun processFiles() {
        val uris = selected.toList()
        if (uris.isEmpty()) return
        runButton.isEnabled = false
        status.text = "Processing ${uris.size} file(s)…"
        saveButton.isEnabled = false
        previewButton.isEnabled = false
        val pageSelection = pagesInput.text.toString()
        val activeOperation = operation
        thread {
                try {
                    val zip = activeOperation == "split"
                    val file = File(cacheDir, "pdf_forge_${System.currentTimeMillis()}.${if (zip) "zip" else "pdf"}")
                    when (activeOperation) {
                        "images" -> imagesToPdf(uris, file)
                        "merge" -> mergePdfs(uris, file)
                        "extract" -> extractPages(uris.first(), file, pageSelection)
                        "split" -> splitPages(uris.first(), file)
                        "compress" -> renderToPdf(uris, file, 55, false)
                    }
                    result = file
                    resultIsZip = zip
                    runOnUiThread { status.text = "Ready: ${file.length() / 1024} KB. Preview or save."; saveButton.isEnabled = true; previewButton.isEnabled = !zip; runButton.isEnabled = true }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Failed: ${e.message}"; runButton.isEnabled = true }
                }
            }

    }

    private fun saveResult() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (resultIsZip) "application/zip" else "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "PDFForge_${System.currentTimeMillis()}.${if (resultIsZip) "zip" else "pdf"}")
        }, saveCode)
    }

    private fun mergePdfs(uris: List<Uri>, output: File) {
        require(uris.size >= 2) { "Select at least two PDFs." }
        val merger = PDFMergerUtility()
        merger.destinationFileName = output.absolutePath
        val temporary = mutableListOf<File>()
        try {
            uris.forEach { uri ->
                val source = File.createTempFile("merge_", ".pdf", cacheDir)
                temporary.add(source)
                contentResolver.openInputStream(uri)?.use { input ->
                    source.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to read PDF")
                merger.addSource(source)
            }
            merger.mergeDocuments(null)
        } finally {
            temporary.forEach { it.delete() }
        }
    }

    private fun copySource(uri: Uri): File {
        val source = File.createTempFile("input_", ".pdf", cacheDir)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open selected PDF")
            return source
        } catch (e: Exception) { source.delete(); throw e }
    }

    private fun parsePages(text: String, total: Int): List<Int> {
        if (text.isBlank()) return (0 until total).toList()
        val pages = linkedSetOf<Int>()
        text.split(",").forEach { token ->
            val part = token.trim()
            val match = Regex("^(\\d+)\\s*-\\s*(\\d+)$").matchEntire(part)
            val range = if (match != null) {
                val a = match.groupValues[1].toInt()
                val b = match.groupValues[2].toInt()
                require(a <= b) { "Invalid range: $part" }
                a..b
            } else {
                val page = part.toIntOrNull() ?: error("Invalid page: $part")
                page..page
            }
            require(range.first >= 1 && range.last <= total) { "Page outside 1-$total: $part" }
            pages.addAll(range.map { it - 1 })
        }
        return pages.toList()
    }

    private fun extractPages(uri: Uri, output: File, selection: String) {
        val source = copySource(uri)
        try {
            PDDocument.load(source).use { original ->
                val pages = parsePages(selection, original.numberOfPages)
                require(pages.isNotEmpty()) { "Select at least one page" }
                PDDocument().use { extracted ->
                    pages.forEach { extracted.importPage(original.getPage(it)) }
                    extracted.save(output)
                }
            }
        } finally { source.delete() }
    }

    private fun splitPages(uri: Uri, output: File) {
        val source = copySource(uri)
        try {
            PDDocument.load(source).use { original ->
                require(original.numberOfPages > 0) { "PDF has no pages" }
                ZipOutputStream(output.outputStream().buffered()).use { zip ->
                    for (index in 0 until original.numberOfPages) {
                        val part = File.createTempFile("page_", ".pdf", cacheDir)
                        try {
                            PDDocument().use { single ->
                                single.importPage(original.getPage(index))
                                single.save(part)
                            }
                            zip.putNextEntry(ZipEntry("page_%03d.pdf".format(index + 1)))
                            part.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        } finally { part.delete() }
                    }
                }
            }
        } finally { source.delete() }
    }

    private fun preview(file: File, allowSave: Boolean = true) {
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12) }
        val label = TextView(this).apply { gravity = Gravity.CENTER; textSize = 16f }
        val image = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val controls = LinearLayout(this)
        val previous = Button(this).apply { text = "Previous" }
        val next = Button(this).apply { text = "Next" }
        controls.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        holder.addView(label)
        holder.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        holder.addView(controls)
        val dialog = android.app.AlertDialog.Builder(this).setTitle("PDF Preview").setView(holder)
            .setNegativeButton("Close", null).create()
        dialog.show()
        dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * 0.8).toInt())
        var current = 0
        var token = 0
        fun showPage() {
            val request = ++token
            val pageIndex = current
            label.text = "Loading page ${pageIndex + 1}..."
            previous.isEnabled = false
            next.isEnabled = false
            thread {
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            val count = renderer.pageCount
                            renderer.openPage(pageIndex).use { page ->
                                val scale = minOf(1.5f, 1000f / page.width)
                                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                runOnUiThread {
                                    if (dialog.isShowing && request == token) {
                                        image.setImageBitmap(bitmap)
                                        label.text = "Page ${pageIndex + 1} of $count"
                                        previous.isEnabled = pageIndex > 0
                                        next.isEnabled = pageIndex < count - 1
                                    } else bitmap.recycle()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { if (dialog.isShowing && request == token) label.text = "Preview failed: ${e.message}" }
                }
            }
        }
        previous.setOnClickListener { if (current > 0) { current--; showPage() } }
        next.setOnClickListener { current++; showPage() }
        showPage()
    }

    private fun imagesToPdf(uris: List<Uri>, file: File) {
        val doc = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalArgumentException("Cannot decode image")
                val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val scale = minOf(555f / bitmap.width, 802f / bitmap.height)
                val w = bitmap.width * scale; val h = bitmap.height * scale
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, null, android.graphics.RectF((595-w)/2, (842-h)/2, (595+w)/2, (842+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bitmap.recycle()
            }
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally { doc.close() }
    }

    // Android's built-in PDF APIs render pages to images. This is a rasterized
    // implementation: selectable text, vector graphics and links are not preserved.
    private fun renderToPdf(uris: List<Uri>, file: File, quality: Int, firstPageOnly: Boolean) {
        val doc = PdfDocument()
        var pageNumber = 1
        try {
            uris.forEach { uri ->
                val local = File.createTempFile("source_", ".pdf", cacheDir)
                try {
                    contentResolver.openInputStream(uri)!!.use { input -> local.outputStream().use { input.copyTo(it) } }
                    ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            val count = if (firstPageOnly) minOf(1, renderer.pageCount) else renderer.pageCount
                            for (i in 0 until count) {
                                renderer.openPage(i).use { src ->
                                    val width = (src.width * if (quality < 100) 0.7 else 1.0).toInt().coerceAtLeast(1)
                                    val height = (src.height * if (quality < 100) 0.7 else 1.0).toInt().coerceAtLeast(1)
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                    bitmap.eraseColor(Color.WHITE)
                                    src.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val page = doc.startPage(PdfDocument.PageInfo.Builder(src.width, src.height, pageNumber++).create())
                                    page.canvas.drawColor(Color.WHITE)
                                    page.canvas.drawBitmap(bitmap, null, android.graphics.RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                                    doc.finishPage(page)
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                } finally { local.delete() }
            }
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally { doc.close() }
    }
}
