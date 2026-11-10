package com.fz.office.pdf

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.fz.office.viewer.ReaderDashboard
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * FZ Office PDF screen backed by MJ PDF's PDFView and PDFium engine.
 * The library owns page layout, rendering, fling, pinch zoom and gesture arbitration.
 */
class PdfViewerActivity : Activity() {

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val MAX_PDF_BYTES = 512L * 1024L * 1024L
        private const val CANVAS = 0xffe9eeeb.toInt()
        private const val INK = 0xff18352d.toInt()
        private const val MUTED = 0xff60766d.toInt()
    }

    private data class ThumbnailViews(val image: ImageView, val label: TextView)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val thumbnailWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val thumbnailPending = HashSet<Int>()
    private val thumbnailCache = object : LruCache<Int, Bitmap>(4 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val preferences by lazy { getSharedPreferences("fz_pdf_reader", MODE_PRIVATE) }
    private val bookmarks = linkedSetOf<Int>()

    private var documentUri: Uri? = null
    private var documentName = "PDF document"
    private var pdfView: PDFView? = null
    private var shell: ReaderDashboard? = null
    private var cachedPdf: File? = null
    private var thumbnailCore: PdfiumCore? = null
    private var thumbnailDocument: PdfDocument? = null
    private var pageCount = 0
    private var pageIndex = 0
    private var savedZoom = 1f
    private var fallbackStarted = false
    @Volatile private var closing = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val rawUri = intent.getStringExtra(EXTRA_DOCUMENT_URI)
        if (rawUri.isNullOrBlank()) {
            Toast.makeText(this, "No PDF file was selected.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        documentUri = Uri.parse(rawUri)
        intent.getStringExtra(EXTRA_DOCUMENT_NAME)?.takeIf { it.isNotBlank() }?.let {
            documentName = it
        }
        pageIndex = (state?.getInt("pdf_page") ?: 0).coerceAtLeast(0)
        savedZoom = (state?.getFloat("pdf_zoom") ?: 1f).coerceIn(1f, 6f)
        loadBookmarks()
        buildScreen()
        openPdf()
    }

    private fun buildScreen() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()

        val dashboard = ReaderDashboard(this, documentName, "PDF · MJ PDFIUM · READ ONLY") { finish() }
        shell = dashboard
        dashboard.addToolbarAction("Pages", "Open page previews") { showPages() }
        val star = dashboard.addToolbarAction("☆", "Bookmark current page") { toggleBookmark() }
        star.setOnLongClickListener {
            showBookmarks()
            true
        }
        dashboard.addToolbarAction("Fit", "Fit page to screen width") {
            pdfView?.resetZoom()
        }
        dashboard.addToolbarAction("↗", "Share PDF") { shareDocument() }

        val view = PDFView(this, null).apply {
            setBackgroundColor(CANVAS)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setMinZoom(1f)
            setMidZoom(2f)
            setMaxZoom(6f)
        }
        pdfView = view
        dashboard.setDocumentView(view)
        dashboard.setStatus("Preparing PDF · Scroll to read · Pinch to zoom")
        dashboard.setLoading(true)
        dashboard.status.setOnLongClickListener {
            showGoToPage()
            true
        }

        setContentView(dashboard.root)
        applySafeAreaInsets(dashboard.root)
    }

    private fun openPdf(sourceFile: File? = null) {
        val view = pdfView ?: return
        if (closing || isFinishing || isDestroyed) return
        shell?.setLoading(true)
        shell?.setStatus(if (sourceFile == null) "Opening PDF with MJ PDFium…" else "Opening cached PDF…")

        try {
            val config = if (sourceFile == null) {
                view.fromUri(checkNotNull(documentUri))
            } else {
                view.fromFile(sourceFile)
            }
            config
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubleTap(true)
                .defaultPage(pageIndex)
                .enableAnnotationRendering(true)
                .enableTextSelection(true)
                .enableAntialiasing(true)
                .spacing(dp(9))
                .autoSpacing(false)
                .pageFitPolicy(FitPolicy.WIDTH)
                .fitEachPage(false)
                .pageSnap(false)
                .pageFling(false)
                .onLoad { total -> onPdfLoaded(total) }
                .onPageChange { page, total -> onPdfPageChanged(page, total) }
                .onError { error -> onPdfError(error, sourceFile != null) }
                .load()
        } catch (error: Throwable) {
            onPdfError(error, sourceFile != null)
        }
    }

    private fun onPdfLoaded(total: Int) {
        if (closing) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            pageCount = total
            shell?.setLoading(false)
            val view = pdfView ?: return@runOnUiThread
            view.setMinZoom(1f)
            view.setMidZoom(2f)
            view.setMaxZoom(6f)
            view.post {
                if (closing || isFinishing || isDestroyed) return@post
                view.zoomTo(savedZoom)
                view.jumpTo(pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)), false)
                updatePageStatus()
            }
        }
    }

    private fun onPdfPageChanged(page: Int, total: Int) {
        if (closing) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            pageIndex = page.coerceAtLeast(0)
            pageCount = total.coerceAtLeast(0)
            updatePageStatus()
        }
    }

    private fun onPdfError(error: Throwable, fromCache: Boolean) {
        if (closing || isFinishing || isDestroyed) return
        if (!fromCache && !fallbackStarted) {
            fallbackStarted = true
            runOnUiThread {
                shell?.setLoading(true)
                shell?.setStatus("Preparing a local copy for this PDF provider…")
            }
            cacheWorker.execute {
                val result = try {
                    copyToPrivateCache()
                } catch (_: Exception) {
                    null
                }
                mainHandler.post {
                    if (closing || isFinishing || isDestroyed) {
                        result?.delete()
                        return@post
                    }
                    if (result != null) {
                        cachedPdf = result
                        openPdf(result)
                    } else {
                        showError(error)
                    }
                }
            }
            return
        }
        runOnUiThread { showError(error) }
    }

    @Throws(IOException::class)
    private fun copyToPrivateCache(): File {
        val target = File.createTempFile("fz-office-pdf-", ".pdf", cacheDir)
        var total = 0L
        try {
            val input = contentResolver.openInputStream(checkNotNull(documentUri))
                ?: throw IOException("The selected PDF cannot be read.")
            input.use { source ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > MAX_PDF_BYTES) {
                            throw IOException("PDF is larger than the 512 MB safety limit.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            return target
        } catch (error: Exception) {
            target.delete()
            if (error is IOException) throw error
            throw IOException("Could not prepare the selected PDF.", error)
        }
    }

    private fun updatePageStatus() {
        val message = if (pageCount > 0) {
            "Page " + (pageIndex + 1) + " / " + pageCount + " · Scroll · Pinch to zoom"
        } else {
            "Preparing PDF…"
        }
        shell?.setStatus(message)
        val star = shell?.toolbar?.let { toolbar ->
            (0 until toolbar.childCount)
                .map { toolbar.getChildAt(it) }
                .filterIsInstance<TextView>()
                .firstOrNull { it.contentDescription == "Bookmark current page" }
        }
        star?.text = if (bookmarks.contains(pageIndex)) "★" else "☆"
    }

    private fun showPages() {
        if (pageCount < 1) return
        val grid = GridView(this).apply {
            numColumns = 3
            horizontalSpacing = dp(10)
            verticalSpacing = dp(12)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dp(12), dp(12), dp(12), dp(12))
            adapter = ThumbnailAdapter()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pages · " + pageCount)
            .setView(grid)
            .setNegativeButton("Close", null)
            .create()
        grid.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            jumpToPage(position)
        }
        dialog.setOnDismissListener {
            thumbnailWorker.execute { closeThumbnailDocument() }
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).roundToInt(),
            (resources.displayMetrics.heightPixels * 0.78f).roundToInt()
        )
    }

    private inner class ThumbnailAdapter : BaseAdapter() {
        override fun getCount(): Int = pageCount
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val cell: LinearLayout
            val holder: ThumbnailViews
            if (recycled == null) {
                cell = LinearLayout(this@PdfViewerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = rounded(Color.WHITE, 10)
                }
                val image = ImageView(this@PdfViewerActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(0xfff2f5f3.toInt())
                    contentDescription = "PDF page preview"
                }
                val label = TextView(this@PdfViewerActivity).apply {
                    textSize = 10f
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                }
                cell.addView(image, LinearLayout.LayoutParams(-1, dp(142)))
                cell.addView(label, LinearLayout.LayoutParams(-1, dp(25)))
                holder = ThumbnailViews(image, label)
                cell.tag = holder
            } else {
                cell = recycled as LinearLayout
                holder = cell.tag as ThumbnailViews
            }
            holder.image.tag = position
            holder.label.text = if (bookmarks.contains(position)) {
                "★  " + (position + 1)
            } else {
                (position + 1).toString()
            }
            val bitmap = thumbnailCache.get(position)
            if (bitmap != null && !bitmap.isRecycled) {
                holder.image.setImageBitmap(bitmap)
            } else {
                holder.image.setImageDrawable(null)
                requestThumbnail(position, holder.image)
            }
            return cell
        }
    }

    private fun requestThumbnail(position: Int, target: ImageView) {
        if (closing || position !in 0 until pageCount) return
        val cached = thumbnailCache.get(position)
        if (cached != null && !cached.isRecycled) {
            if (target.tag == position) target.setImageBitmap(cached)
            return
        }
        synchronized(thumbnailPending) {
            if (!thumbnailPending.add(position)) return
        }
        thumbnailWorker.execute {
            var bitmap: Bitmap? = null
            try {
                var core = thumbnailCore
                var document = thumbnailDocument
                if (core == null || document == null) {
                    core = PdfiumCore(this@PdfViewerActivity)
                    val fd = cachedPdf?.let {
                        ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
                    } ?: contentResolver.openFileDescriptor(checkNotNull(documentUri), "r")
                    if (fd == null) throw IOException("PDF thumbnail source is unavailable.")
                    document = core.newDocument(fd)
                    thumbnailCore = core
                    thumbnailDocument = document
                }
                core.openPage(document, position)
                val widthPoints = core.getPageWidthPoint(document, position).coerceAtLeast(1)
                val heightPoints = core.getPageHeightPoint(document, position).coerceAtLeast(1)
                val width = 150
                val height = (width.toFloat() * heightPoints / widthPoints).roundToInt()
                    .coerceIn(1, 260)
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
                    eraseColor(Color.WHITE)
                }
                core.renderPageBitmap(document, bitmap, position, 0, 0, width, height)
                core.closePage(document, position)
            } catch (_: Exception) {
                closeThumbnailDocument()
            }
            val result = bitmap
            mainHandler.post {
                synchronized(thumbnailPending) { thumbnailPending.remove(position) }
                if (closing || isFinishing || isDestroyed) {
                    if (result != null && !result.isRecycled) result.recycle()
                    return@post
                }
                if (result != null) {
                    thumbnailCache.put(position, result)
                    if (target.tag == position) target.setImageBitmap(result)
                }
            }
        }
    }

    private fun closeThumbnailDocument() {
        try {
            val core = thumbnailCore
            val document = thumbnailDocument
            if (core != null && document != null) core.closeDocument(document)
        } catch (_: Exception) {
        } finally {
            thumbnailCore = null
            thumbnailDocument = null
        }
    }

    private fun jumpToPage(index: Int) {
        if (pageCount < 1) return
        pageIndex = index.coerceIn(0, pageCount - 1)
        pdfView?.jumpTo(pageIndex, true)
        updatePageStatus()
    }

    private fun toggleBookmark() {
        if (pageCount < 1) return
        if (!bookmarks.add(pageIndex)) bookmarks.remove(pageIndex)
        saveBookmarks()
        updatePageStatus()
        Toast.makeText(
            this,
            if (bookmarks.contains(pageIndex)) "Page bookmarked." else "Bookmark removed.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBookmarks() {
        val saved = bookmarks.filter { it in 0 until pageCount }.sorted()
        if (saved.isEmpty()) {
            Toast.makeText(this, "No bookmarks in this PDF yet.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setItems(saved.map { "Page " + (it + 1) }.toTypedArray()) { _, selected ->
                jumpToPage(saved[selected])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showGoToPage() {
        if (pageCount < 1) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Page number (1-" + pageCount + ")"
            setText((pageIndex + 1).toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Go") { _, _ ->
                val requested = input.text.toString().toIntOrNull()
                if (requested == null || requested !in 1..pageCount) {
                    Toast.makeText(this, "Enter a page from 1 to " + pageCount + ".", Toast.LENGTH_SHORT).show()
                } else {
                    jumpToPage(requested - 1)
                }
            }
            .show()
    }

    private fun shareDocument() {
        val uri = documentUri ?: return
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "This PDF cannot be shared right now.", Toast.LENGTH_LONG).show()
        }
    }

    private fun bookmarkKey(): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((documentUri?.toString() ?: "unknown").toByteArray(Charsets.UTF_8))
        val hash = bytes.joinToString("") {
            "%02x".format(Locale.ROOT, it.toInt() and 0xff)
        }
        return "bookmarks_" + hash
    }

    private fun loadBookmarks() {
        val saved = preferences.getStringSet(bookmarkKey(), emptySet()) ?: emptySet()
        bookmarks.clear()
        saved.mapNotNull { it.toIntOrNull() }.forEach { bookmarks.add(it) }
    }

    private fun saveBookmarks() {
        preferences.edit()
            .putStringSet(bookmarkKey(), bookmarks.map { it.toString() }.toSet())
            .apply()
    }

    private fun showError(error: Throwable) {
        if (isFinishing || isDestroyed) return
        shell?.setLoading(false)
        shell?.setStatus("Could not open this PDF")
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: "The file may be damaged, encrypted or unavailable."
        AlertDialog.Builder(this)
            .setTitle("PDF could not be opened")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

    private fun applySafeAreaInsets(root: View) {
        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            @Suppress("DEPRECATION")
            insets.consumeSystemWindowInsets()
        }
        root.requestApplyInsets()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).roundToInt()

    override fun onSaveInstanceState(outState: Bundle) {
        pageIndex = pdfView?.currentPage?.coerceAtLeast(0) ?: pageIndex
        savedZoom = pdfView?.zoom?.coerceIn(1f, 6f) ?: savedZoom
        outState.putInt("pdf_page", pageIndex)
        outState.putFloat("pdf_zoom", savedZoom)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        closing = true
        pdfView?.recycle()
        cacheWorker.shutdownNow()
        thumbnailWorker.execute { closeThumbnailDocument() }
        thumbnailWorker.shutdown()
        thumbnailCache.evictAll()
        cachedPdf?.delete()
        super.onDestroy()
    }
}
