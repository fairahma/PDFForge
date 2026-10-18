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
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val pickCode = 10
    private val saveCode = 11
    private var operation = ""
    private var selected = emptyList<Uri>()
    private var result: File? = null
    private lateinit var status: TextView
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 24)
            setBackgroundColor(Color.rgb(250, 250, 250))
        }
        val heading = TextView(this).apply { text = "PDF Forge"; textSize = 30f; setTextColor(Color.BLACK) }
        layout.addView(heading)
        layout.addView(TextView(this).apply { text = "Private • Offline • Lightweight"; textSize = 15f; setPadding(0, 8, 0, 26) })
        listOf(
            "Images to PDF" to "images",
            "Merge PDFs" to "merge",
            "Extract first PDF page" to "split",
            "Compress PDF (image-based)" to "compress"
        ).forEach { (title, op) ->
            layout.addView(Button(this).apply {
                text = title
                isAllCaps = false
                setOnClickListener { operation = op; chooseFiles() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        status = TextView(this).apply { text = "Choose a tool to begin."; textSize = 15f; setPadding(0, 30, 0, 16) }
        layout.addView(status)
        saveButton = Button(this).apply { text = "Save result"; isEnabled = false; setOnClickListener { saveResult() } }
        layout.addView(saveButton)
        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
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
            status.text = "Processing ${uris.size} file(s)…"
            saveButton.isEnabled = false
            thread {
                try {
                    val file = File(cacheDir, "pdf_forge_${System.currentTimeMillis()}.pdf")
                    when (operation) {
                        "images" -> imagesToPdf(uris, file)
                        "merge" -> mergePdfs(uris, file)
                        "split" -> extractFirstPage(uris, file)
                        "compress" -> renderToPdf(uris, file, 55, false)
                    }
                    result = file
                    runOnUiThread { status.text = "Ready: ${file.length() / 1024} KB. Tap Save result."; saveButton.isEnabled = true }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Failed: ${e.message}" }
                }
            }
        } else if (requestCode == saveCode) {
            val file = result ?: return
            try {
                contentResolver.openOutputStream(data.data!!)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                status.text = "PDF saved successfully."
            } catch (e: Exception) { status.text = "Save failed: ${e.message}" }
        }
    }

    private fun saveResult() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "PDFForge_${System.currentTimeMillis()}.pdf")
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

    private fun extractFirstPage(uris: List<Uri>, output: File) {
        val source = File.createTempFile("split_", ".pdf", cacheDir)
        try {
            contentResolver.openInputStream(uris.first())?.use { input ->
                source.outputStream().use { input.copyTo(it) }
            } ?: error("Unable to read PDF")
            PDDocument.load(source).use { original ->
                require(original.numberOfPages > 0) { "PDF has no pages" }
                PDDocument().use { extracted ->
                    extracted.importPage(original.getPage(0))
                    extracted.save(output)
                }
            }
        } finally {
            source.delete()
        }
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
