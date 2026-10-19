package com.pdfforge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var grid: LinearLayout
    private lateinit var canvas: ImageView
    private lateinit var pageTitle: TextView
    private var activePage = 0
    private var renderGeneration = 0
    private lateinit var info: TextView
    private lateinit var save: Button
    private var extractInput: EditText? = null
    private var output: File? = null
    private var mode = "organize"
    private val pickRequest = 201
    private val saveRequest = 202
    private val folderRequest = 203
    private var splitMode = false
    private var splitCuts = emptyList<Int>()
    private val navy get() = ThemePrefs.foreground(this)
    private val blue = ThemePrefs.accent
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
            setBackgroundColor(ThemePrefs.background(this@PageEditorActivity))
            setPadding(dp(16), dp(18), dp(16), dp(10))
        }
        val header = label("‹  " + when(mode) {
            "merge" -> "Merge PDFs"
            "extract" -> "Split / Extract"
            else -> "Organize PDF"
        }, 23f).apply { typeface = Typeface.DEFAULT_BOLD }
        header.setOnClickListener { finish() }
        outer.addView(header)
        outer.addView(label("Arrange pages • Hold to reorder • Tap for options", 12f, ThemePrefs.secondary(this)).apply { setPadding(0, dp(3), 0, dp(14)) })
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(5), 0, dp(5)) }
        val add = Button(this).apply {
            text = "+  Add files"
            isAllCaps = false
            setOnClickListener { pickFiles() }
        }
        toolbar.addView(add, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        val preview = Button(this).apply {
            text = "Preview"
            isAllCaps = false
            setOnClickListener { buildResult(true) }
        }
        toolbar.addView(preview, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        add.background = rounded(Color.rgb(41, 63, 98), 12)
        add.setTextColor(Color.WHITE)
        preview.background = rounded(Color.rgb(41, 63, 98), 12)
        preview.setTextColor(Color.WHITE)
        outer.addView(toolbar)
        if (mode == "extract") {
            val switcher = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
            val extractRadio = RadioButton(this).apply {
                id = View.generateViewId(); text = "Extract"; setTextColor(navy); isChecked = true
            }
            val splitRadio = RadioButton(this).apply {
                id = View.generateViewId(); text = "Split"; setTextColor(navy)
            }
            switcher.addView(extractRadio)
            switcher.addView(splitRadio)
            outer.addView(switcher)
            switcher.setOnCheckedChangeListener { _, checked ->
                splitMode = checked == splitRadio.id
                extractInput?.hint = if (splitMode) "Split after page numbers: 3,7" else "Extract: 1,3,5-8"
                if (::save.isInitialized) save.text = if (splitMode) "Save split PDFs" else "Extract selected pages"
            }
            outer.addView(label("Page selection", 13f))
            extractInput = EditText(this).apply {
                hint = "1,3,5-8  •  Blank = all pages"
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(168, 184, 208))
                backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            outer.addView(extractInput)
        }
        info = label("Select a PDF to begin", 13f, ThemePrefs.secondary(this))
        info.setPadding(0, dp(20), 0, dp(12))
        outer.addView(info)
        val workspace = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val railScroll = ScrollView(this).apply { background = rounded(ThemePrefs.surface(this@PageEditorActivity), 14) }
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        railScroll.addView(grid)
        workspace.addView(railScroll, LinearLayout.LayoutParams(dp(104), -1))
        val viewer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(8))
        }
        pageTitle = label("Select a page", 13f, ThemePrefs.secondary(this)).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(8))
        }
        viewer.addView(pageTitle)
        val pageScroll = ScrollView(this).apply { isFillViewport = true }
        canvas = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.WHITE)
        }
        pageScroll.addView(canvas, LinearLayout.LayoutParams(-1, -2))
        viewer.addView(pageScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val remove = Button(this).apply {
            text = "Delete"
            isAllCaps = false
            background = rounded(Color.rgb(77, 46, 67), 12)
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (pages.isNotEmpty()) {
                    pages.removeAt(activePage.coerceIn(0, pages.lastIndex))
                    activePage = activePage.coerceAtMost((pages.size - 1).coerceAtLeast(0))
                    output = null
                    refresh()
                }
            }
        }
        actions.addView(remove, LinearLayout.LayoutParams(0, dp(44), 1f))
        val view = Button(this).apply {
            text = "Full view"
            isAllCaps = false
            background = rounded(Color.rgb(42, 63, 99), 12)
            setTextColor(Color.WHITE)
            setOnClickListener { if (pages.isNotEmpty()) showSourcePage(pages[activePage]) }
        }
        actions.addView(view, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        viewer.addView(actions)
        workspace.addView(viewer, LinearLayout.LayoutParams(0, -1, 1f))
        outer.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        save = Button(this).apply {
            text = if (mode == "merge") "Review & save merged PDF" else if (mode == "extract") "Extract selected pages" else "Review & save PDF"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue, 14)
            isEnabled = false
            setOnClickListener { if (mode == "extract" && splitMode) saveSplit() else buildResult(true) }
        }
        outer.addView(save, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
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
        if (requestCode == folderRequest) {
            val tree = data.data ?: return
            val snapshot = pages.toList()
            val cuts = splitCuts.toList()
            thread {
                try {
                    val docs = sources.map { PDDocument.load(it) }
                    try {
                        val bounds = listOf(0) + cuts + listOf(snapshot.size)
                        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                            tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
                        for (part in 0 until bounds.lastIndex) {
                            val destination = android.provider.DocumentsContract.createDocument(
                                contentResolver, parent, "application/pdf", "PDFForge_part_${part + 1}.pdf")
                                ?: error("Cannot create output file")
                            val temporary = File.createTempFile("part_", ".pdf", cacheDir)
                            try {
                                PDDocument().use { pdf ->
                                    snapshot.subList(bounds[part], bounds[part + 1]).forEach { ref ->
                                        pdf.importPage(docs[ref.source].getPage(ref.page))
                                    }
                                    pdf.save(temporary)
                                }
                                contentResolver.openOutputStream(destination)?.use { stream ->
                                    temporary.inputStream().use { it.copyTo(stream) }
                                } ?: error("Cannot write output")
                            } finally { temporary.delete() }
                        }
                    } finally { docs.forEach { it.close() } }
                    runOnUiThread { info.text = "Saved ${cuts.size + 1} PDF files" }
                } catch (e: Exception) {
                    runOnUiThread { info.text = "Split failed: ${e.message}" }
                }
            }
            return
        }
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
        info.text = "${pages.size} PAGES  •  Hold a page, then drag onto a highlighted thumbnail"
        save.isEnabled = pages.isNotEmpty()
        if (pages.isEmpty()) {
            pageTitle.text = "No pages selected"
            canvas.setImageDrawable(null)
            return
        }
        activePage = activePage.coerceIn(0, pages.lastIndex)
        pages.toList().forEachIndexed { index, ref ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(5), dp(5), dp(5), dp(7))
                background = rounded(if (index == activePage) Color.rgb(65, 85, 147) else ThemePrefs.surface(this@PageEditorActivity), 10)
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }
            tile.addView(thumb, LinearLayout.LayoutParams(-1, dp(110)))
            tile.addView(label("${index + 1}  ⋮", 12f, if (index == activePage) Color.WHITE else navy).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            tile.setOnClickListener { activePage = index; refresh() }
            tile.setOnLongClickListener {
                tile.startDragAndDrop(ClipData.newPlainText("page", index.toString()), View.DragShadowBuilder(tile), index, 0)
                true
            }
            val normalBackground = tile.background
            val dropBackground = GradientDrawable().apply {
                setColor(if (ThemePrefs.dark(this@PageEditorActivity)) Color.rgb(43, 58, 98) else Color.rgb(231, 225, 255))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(3), Color.rgb(117, 91, 255))
            }
            val positionLabel = tile.getChildAt(1) as TextView
            tile.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        val from = event.localState as? Int
                        if (from != null && from != index) {
                            tile.background = dropBackground
                            positionLabel.text = "Drop here ↓"
                            positionLabel.setTextColor(if (ThemePrefs.dark(this@PageEditorActivity)) Color.WHITE else Color.rgb(75, 53, 191))
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        tile.background = normalBackground
                        positionLabel.text = "${index + 1}  ⋮"
                        positionLabel.setTextColor(if (index == activePage) Color.WHITE else navy)
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        val from = event.localState as? Int
                        if (from != null && from in pages.indices && index in pages.indices && from != index) {
                            val moved = pages.removeAt(from)
                            pages.add(index, moved)
                            activePage = index
                            output = null
                            refresh()
                            info.text = "Moved page ${from + 1} to position ${index + 1}"
                        }
                        true
                    }
                    else -> true
                }
            }
            grid.addView(tile, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            thread {
                try {
                    val bitmap = renderPage(sources[ref.source], ref.page, 150)
                    runOnUiThread { if (tile.parent != null) thumb.setImageBitmap(bitmap) else bitmap.recycle() }
                } catch (_: Exception) { }
            }
        }
        pageTitle.text = "Page ${activePage + 1} of ${pages.size}"
        val ref = pages[activePage]
        val generation = ++renderGeneration
        canvas.setImageDrawable(null)
        thread {
            try {
                val bitmap = renderPage(sources[ref.source], ref.page, 750)
                runOnUiThread {
                    if (generation == renderGeneration) canvas.setImageBitmap(bitmap)
                    else bitmap.recycle()
                }
            } catch (e: Exception) {
                runOnUiThread { if (generation == renderGeneration) pageTitle.text = "Unable to render page" }
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
        showScrollablePdf(sources[ref.source], "Selected PDF", null)
    }

    private fun showScrollablePdf(file: File, title: String, onSave: (() -> Unit)?) {
        val scroll = ScrollView(this)
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(225, 233, 246))
        }
        scroll.addView(stack)
        val builder = AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setNegativeButton("Close", null)
        if (onSave != null) builder.setPositiveButton("Save PDF") { _, _ -> onSave() }
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * 0.84f).toInt())
        thread {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val count = renderer.pageCount
                        for (index in 0 until count) {
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
                                    val number = label("Page ${index + 1} of $count", 13f, Color.rgb(21, 43, 80)).apply {
                                        setPadding(dp(4), dp(12), 0, dp(5))
                                    }
                                    val image = ImageView(this).apply {
                                        setImageBitmap(bitmap)
                                        adjustViewBounds = true
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                    stack.addView(number)
                                    stack.addView(image, LinearLayout.LayoutParams(-1, -2))
                                } else bitmap.recycle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (dialog.isShowing) stack.addView(label("Preview failed: ${e.message}", 14f, Color.RED)) }
            }
        }
    }

    private fun saveSplit() {
        if (pages.isEmpty()) return
        try {
            val raw = extractInput?.text?.toString()?.trim().orEmpty()
            require(raw.isNotBlank()) { "Enter split boundaries, e.g. 3,7" }
            val cuts = raw.split(",").map { it.trim().toIntOrNull() ?: error("Invalid page number") }
            require(cuts == cuts.distinct().sorted() && cuts.all { it > 0 && it < pages.size }) {
                "Boundaries must be increasing and before the last page"
            }
            splitCuts = cuts
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), folderRequest)
        } catch (e: Exception) { info.text = e.message ?: "Invalid split selection" }
    }

    private fun requestedPages(): List<PageRef> {
        val raw = extractInput?.text?.toString()?.trim().orEmpty()
        if (mode != "extract" || raw.isBlank()) return pages.toList()
        val chosen = linkedSetOf<Int>()
        for (piece in raw.split(",")) {
            val token = piece.trim()
            val range = Regex("^(\\d+)\\s*-\\s*(\\d+)$").matchEntire(token)
            val first: Int
            val last: Int
            if (range != null) {
                first = range.groupValues[1].toInt()
                last = range.groupValues[2].toInt()
            } else {
                first = token.toIntOrNull() ?: error("Invalid page number: $token")
                last = first
            }
            require(first >= 1 && last >= first && last <= pages.size) {
                "Page range must be within 1-${pages.size}"
            }
            for (n in first..last) chosen.add(n - 1)
        }
        return chosen.map { pages[it] }
    }

    private fun buildResult(showPreview: Boolean) {
        if (pages.isEmpty()) return
        save.isEnabled = false
        info.text = "Preparing PDF preview..."
        val snapshot = try { requestedPages() } catch (e: Exception) {
            info.text = e.message ?: "Invalid page selection"
            save.isEnabled = true
            return
        }
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
        showScrollablePdf(file, "Final PDF preview", { saveOutput() })
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
