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
import android.view.View
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
    private val splitFolderCode = 12
    private var operation = ""
    private var selected = emptyList<Uri>()
    private var result: File? = null
    private lateinit var status: TextView
    private lateinit var saveButton: Button
    private lateinit var previewButton: Button
    private lateinit var pagesInput: EditText
    private var splitMode = false
    private var compressionLevel = 0
    private var resultIsZip = false
    private lateinit var filesPanel: LinearLayout
    private lateinit var runButton: Button
    private lateinit var selectionTitle: TextView
    private lateinit var workspace: LinearLayout
    private var sourceBytes: Long = 0L
    private var compressionResult: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        operation = intent.getStringExtra("tool") ?: "images"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 24)
            setBackgroundColor(ThemePrefs.background(this@ToolActivity))
        }
        fun card(title: String, subtitle: String, op: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 24, 28, 24)
                background = GradientDrawable().apply {
                    setColor(ThemePrefs.surface(this@ToolActivity))
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
            card.addView(TextView(this).apply { text = title; textSize = 19f; setTextColor(ThemePrefs.foreground(this@ToolActivity)) })
            card.addView(TextView(this).apply { text = subtitle; textSize = 13f; setTextColor(ThemePrefs.secondary(this@ToolActivity)) })
            return card
        }
        layout.addView(TextView(this).apply { text = "‹  " + when(operation) { "compress" -> "Compress PDF"; "pages" -> "Split / Extract"; else -> "Images to PDF" }; textSize = 26f; setTextColor(ThemePrefs.foreground(this@ToolActivity)); setPadding(0, 0, 0, 8); setOnClickListener { finish() } })
        layout.addView(TextView(this).apply { text = "Private, offline document workspace"; textSize = 15f; setTextColor(ThemePrefs.secondary(this@ToolActivity)); setPadding(0, 0, 0, 22) })
        
        workspace = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                setColor(ThemePrefs.surface(this@ToolActivity))
                cornerRadius = 28f
                setStroke(1, Color.rgb(65, 83, 116))
            }
        }
        selectionTitle = TextView(this).apply {
            text = if (operation == "compress") "1  SELECT PDF" else "YOUR WORKSPACE"
            textSize = 18f
            setTextColor(ThemePrefs.foreground(this@ToolActivity))
            setPadding(0, 0, 0, 12)
        }
        workspace.addView(selectionTitle)
        filesPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        workspace.addView(filesPanel)
        workspace.addView(Button(this).apply {
            text = if (operation == "compress") "+  Choose PDF" else "+  Add files"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.rgb(55, 76, 117)); cornerRadius = 20f }
            setOnClickListener { if (operation.isNotEmpty()) chooseFiles() else status.text = "Choose a tool above first." }
        })
        layout.addView(workspace, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14; bottomMargin = 12 })
        runButton = Button(this).apply { text = if (operation == "compress") "Compress PDF" else if (operation == "split") "Split into folder" else "Create PDF"; isEnabled = false; setOnClickListener { if (operation == "split" || (operation == "pages" && splitMode)) chooseSplitFolder() else processFiles() } }
        runButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(117, 91, 255))
        runButton.setTextColor(Color.WHITE)
        layout.addView(runButton)
        pagesInput = EditText(this).apply {
            setTextColor(ThemePrefs.foreground(this@ToolActivity))
            setHintTextColor(Color.rgb(179, 194, 220))
            hint = "Pages to extract: e.g. 1,3-5 (blank = all)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        pagesInput.visibility = if (operation == "split" || operation == "pages") android.view.View.VISIBLE else android.view.View.GONE
        if (operation == "split") pagesInput.hint = "Split after pages, e.g. 5,12 (20 pages → 1-5, 6-12, 13-20)"
        if (operation == "pages") {
            val modes = Spinner(this)
            modes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                listOf("Extract selected pages (one PDF)", "Split at page numbers (multiple PDFs)"))
            layout.addView(modes)
            modes.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    splitMode = position == 1
                    pagesInput.hint = if (splitMode) "Split after pages: 5,12" else "Extract pages: 1,3,5-8"
                    runButton.text = if (splitMode) "Save split PDFs to folder" else "Extract pages"
                    if (::saveButton.isInitialized) saveButton.visibility = if (splitMode) android.view.View.GONE else android.view.View.VISIBLE
                    if (::previewButton.isInitialized) previewButton.visibility = if (splitMode) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
        }
        layout.addView(pagesInput)
        if (operation == "compress") {
            layout.addView(TextView(this).apply {
                text = "COMPRESSION QUALITY"
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(ThemePrefs.secondary(this@ToolActivity))
                setPadding(0, 22, 0, 14)
            })
            val options = listOf(
                Triple("Low", "Original quality", "Preserves selectable text"),
                Triple("Medium", "Balanced", "Smaller files, good clarity"),
                Triple("High", "Smallest size", "Strong image compression")
            )
            val selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cards = mutableListOf<LinearLayout>()
            options.forEachIndexed { index, option ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(6, 18, 6, 18)
                    isClickable = true
                    isFocusable = true
                }
                val icon = TextView(this).apply {
                    text = if (index == 0) "◯" else if (index == 1) "◉" else "◕"
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(ThemePrefs.accent)
                }
                card.addView(icon)
                card.addView(TextView(this).apply {
                    text = option.first
                    textSize = 15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(ThemePrefs.foreground(this@ToolActivity))
                })
                card.addView(TextView(this).apply {
                    text = option.second
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(ThemePrefs.secondary(this@ToolActivity))
                })
                card.contentDescription = "${option.first} compression. ${option.second}. ${option.third}"
                cards.add(card)
                selector.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index > 0) leftMargin = 8
                })
                card.setOnClickListener {
                    compressionLevel = index
                    cards.forEachIndexed { i, item ->
                        item.background = GradientDrawable().apply {
                            setColor(if (i == index) {
                                if (ThemePrefs.dark(this@ToolActivity)) Color.rgb(48, 40, 91) else Color.rgb(239, 235, 255)
                            } else ThemePrefs.surface(this@ToolActivity))
                            cornerRadius = 18f
                            setStroke(if (i == index) 3 else 1,
                                if (i == index) ThemePrefs.accent else ThemePrefs.secondary(this@ToolActivity))
                        }
                    }
                }
            }
            layout.addView(selector)
            cards.first().performClick()
            layout.addView(TextView(this).apply {
                text = "Medium and High may flatten pages and remove selectable text."
                textSize = 12f
                setTextColor(ThemePrefs.secondary(this@ToolActivity))
                setPadding(0, 16, 0, 18)
            })
            (runButton.parent as? ViewGroup)?.removeView(runButton)
            layout.addView(runButton, LinearLayout.LayoutParams(-1, 62))
            compressionResult = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(22, 20, 22, 20)
                background = GradientDrawable().apply {
                    setColor(ThemePrefs.surface(this@ToolActivity))
                    cornerRadius = 24f
                }
                visibility = View.GONE
            }
            layout.addView(compressionResult, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
        }

        status = TextView(this).apply { text = "Choose a PDF to begin."; textSize = 15f; setTextColor(Color.WHITE); setPadding(0, 22, 0, 16) }
        layout.addView(status)
        saveButton = Button(this).apply { text = "Save result"; isEnabled = false; setOnClickListener { saveResult() } }
        layout.addView(saveButton)
        if (operation == "compress") saveButton.visibility = android.view.View.GONE
        previewButton = Button(this).apply { text = "Preview PDF result"; isEnabled = false; setOnClickListener { result?.let { preview(it) } } }
        layout.addView(previewButton)
        if (operation == "compress") previewButton.visibility = android.view.View.GONE
        if (operation == "pages") layout.addView(Button(this).apply {
            text = "Visual page selection and organization"
            setOnClickListener {
                startActivity(Intent(this@ToolActivity, PageEditorActivity::class.java).putExtra("tool", "extract"))
            }
        })
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ThemePrefs.background(this@ToolActivity))
            isFillViewport = true
        }
        scroll.addView(layout)
        window.statusBarColor = ThemePrefs.background(this)
        window.navigationBarColor = ThemePrefs.background(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (ThemePrefs.dark(this)) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(scroll)
        SafeArea.apply(this, scroll)
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
            if (operation == "compress") {
                sourceBytes = contentResolver.openAssetFileDescriptor(uris.first(), "r")?.use { it.length } ?: 0L
                result = null
                saveButton.visibility = android.view.View.GONE
                previewButton.visibility = android.view.View.GONE
            }
            showSelectedFiles()
            runButton.isEnabled = true
            status.text = if (operation == "compress") "Ready. Select a compression level and tap Compress PDF." else "Review files and tap Process selected files."
        } else if (requestCode == splitFolderCode) {
            val folder = data.data ?: return
            splitIntoFolder(folder)
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
                setTextColor(ThemePrefs.secondary(this@ToolActivity))
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
                    setColor(ThemePrefs.surface(this@ToolActivity))
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
                setTextColor(ThemePrefs.foreground(this@ToolActivity))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            detail.addView(TextView(this).apply {
                text = if (metadata.second > 0) "${metadata.second / 1024} KB • Item ${index + 1}" else "Item ${index + 1}"
                textSize = 12f
                setTextColor(ThemePrefs.secondary(this@ToolActivity))
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
                    if (operation == "compress") { result = null; saveButton.visibility = android.view.View.GONE; previewButton.visibility = android.view.View.GONE }
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

    private fun chooseSplitFolder() {
        if (selected.isEmpty()) return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), splitFolderCode)
    }

    private fun splitIntoFolder(folder: Uri) {
        val uri = selected.firstOrNull() ?: return
        val boundaries = pagesInput.text.toString()
        runButton.isEnabled = false
        status.text = "Creating split PDFs..."
        thread {
            val source = copySource(uri)
            try {
                val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(folder, android.provider.DocumentsContract.getTreeDocumentId(folder))
                PDDocument.load(source).use { original ->
                    val total = original.numberOfPages
                    require(total > 1) { "PDF must have at least two pages" }
                    require(boundaries.isNotBlank()) { "Enter at least one split point, e.g. 5,12" }
                    val cuts = boundaries.split(",").map {
                        it.trim().toIntOrNull() ?: error("Invalid split point: $it")
                    }
                    require(cuts == cuts.distinct().sorted() && cuts.all { it in 1 until total }) {
                        "Split points must be unique, ascending, and between 1 and ${total - 1}"
                    }
                    val ends = cuts + total
                    var start = 0
                    ends.forEachIndexed { partIndex, end ->
                        val name = "PDFForge_part_${partIndex + 1}_pages_${start + 1}-$end.pdf"
                        val dest = android.provider.DocumentsContract.createDocument(contentResolver, parent, "application/pdf", name)
                            ?: error("Cannot create $name")
                        PDDocument().use { part ->
                            for (i in start until end) part.importPage(original.getPage(i))
                            contentResolver.openOutputStream(dest)?.use { part.save(it) }
                                ?: error("Cannot write $name")
                        }
                        start = end
                    }
                    runOnUiThread { status.text = "Saved ${ends.size} PDFs to your chosen folder."; runButton.isEnabled = true }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Split failed: ${e.message}"; runButton.isEnabled = true }
            } finally { source.delete() }
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
        val selectedCompression = compressionLevel
        thread {
                try {
                    val zip = activeOperation == "split"
                    val file = File(cacheDir, "pdf_forge_${System.currentTimeMillis()}.${if (zip) "zip" else "pdf"}")
                    when (activeOperation) {
                        "images" -> imagesToPdf(uris, file)
                        "merge" -> mergePdfs(uris, file)
                        "extract", "pages" -> extractPages(uris.first(), file, pageSelection)
                        "split" -> splitPages(uris.first(), file)
                        "compress" -> compressPdf(uris.first(), file, selectedCompression)
                    }
                    result = file
                    resultIsZip = zip
                    runOnUiThread {
                        status.text = if (activeOperation == "compress") {
                            val before = sourceBytes
                            val after = file.length()
                            if (before > 0) {
                                val percent = ((before - after).coerceAtLeast(0L) * 100.0 / before).toInt()
                                "3  RESULT: Original ${before / 1024} KB, Result ${after / 1024} KB, Saved $percent%"
                            } else "3  RESULT: ${after / 1024} KB"
                        } else "Ready: ${file.length() / 1024} KB. Preview or save."
                        saveButton.isEnabled = true
                        previewButton.isEnabled = !zip
                        if (activeOperation == "compress") {
                            saveButton.visibility = View.VISIBLE
                            previewButton.visibility = View.VISIBLE
                        }
                        runButton.isEnabled = true
                    }
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
        val scroll = ScrollView(this)
        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(225, 233, 246))
        }
        scroll.addView(pages)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Scroll PDF preview")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
        dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * 0.84).toInt())
        thread {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        for (index in 0 until renderer.pageCount) {
                            if (!dialog.isShowing) break
                            val bitmap = renderer.openPage(index).use { page ->
                                val width = 540
                                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            runOnUiThread {
                                if (dialog.isShowing) {
                                    pages.addView(TextView(this).apply {
                                        text = "Page ${index + 1}"
                                        setTextColor(Color.DKGRAY)
                                        setPadding(0, 14, 0, 6)
                                    })
                                    pages.addView(ImageView(this).apply {
                                        setImageBitmap(bitmap)
                                        adjustViewBounds = true
                                    })
                                } else bitmap.recycle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (dialog.isShowing) status.text = "Preview failed: ${e.message}" }
            }
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
    private fun compressPdf(uri: Uri, output: File, level: Int) {
        val source = copySource(uri)
        try {
            if (level > 0) {
                compressRaster(source, output, level)
            } else PDDocument.load(source).use { document ->
                // Lossless PDF rewrite: preserve selectable text, vector graphics,
                // annotations, and original image resolution.
                document.isAllSecurityToBeRemoved = true
                document.save(output)
            }
            // A PDF rewrite is not guaranteed to reduce size. Preserve the original
            // rather than deliver a larger file labelled as compressed.
            if (output.length() >= source.length()) {
                source.copyTo(output, overwrite = true)
            }
        } finally { source.delete() }
    }

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
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
    private fun compressRaster(source: File, output: File, level: Int) {
        val quality = if (level == 1) 65 else 35
        val scale = if (level == 1) 1.2f else 0.8f
        PDDocument().use { compressed ->
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { original ->
                            val w = (original.width * scale).toInt().coerceAtLeast(1)
                            val h = (original.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                original.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val bytes = java.io.ByteArrayOutputStream().use { stream ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                                    stream.toByteArray()
                                }
                                val page = com.tom_roush.pdfbox.pdmodel.PDPage(
                                    com.tom_roush.pdfbox.pdmodel.common.PDRectangle(original.width.toFloat(), original.height.toFloat())
                                )
                                compressed.addPage(page)
                                val image = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromByteArray(compressed, bytes)
                                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(compressed, page).use { canvas ->
                                    canvas.drawImage(image, 0f, 0f, original.width.toFloat(), original.height.toFloat())
                                }
                            } finally { bitmap.recycle() }
                        }
                    }
                }
            }
            compressed.save(output)
        }
    }

}
