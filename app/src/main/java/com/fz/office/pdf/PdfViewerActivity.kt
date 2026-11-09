package com.fz.office.pdf

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Offline, read-only PDF reader. A bounded private cache copy makes provider streams seekable. */
class PdfViewerActivity : Activity() {
    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val MAX_PAGE_DIMENSION = 4096
        private const val MAX_BITMAP_PIXELS = 8_000_000L
        private const val MAX_PDF_BYTES = 512L * 1024L * 1024L
    }

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private var documentUri: Uri? = null
    private var documentName = "PDF document"
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var cachedPdf: File? = null
    private var pageImage: ImageView? = null
    private var pageLabel: TextView? = null
    private var status: TextView? = null
    private var progress: ProgressBar? = null
    private var displayedBitmap: Bitmap? = null
    private var pageIndex = 0
    private var pageCount = 0
    private var renderGeneration = 0
    private var zoom = 1.0f

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
        buildScreen()
        openPdf()
    }

    private fun buildScreen() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xffe9eeeb.toInt())
        }

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)))

        val back = control("‹", 28, 0xff263c38.toInt()).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(42), dp(44)))

        val title = control(documentName, 13, 0xff172c28.toInt()).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(dp(8), 0, dp(5), 0)
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, -1, 1f))

        val zoomOut = toolButton("−").apply { setOnClickListener { changeZoom(-0.15f) } }
        toolbar.addView(zoomOut, LinearLayout.LayoutParams(dp(38), dp(42)))
        val zoomIn = toolButton("+").apply { setOnClickListener { changeZoom(0.15f) } }
        toolbar.addView(zoomIn, LinearLayout.LayoutParams(dp(38), dp(42)))

        val progressView = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        progress = progressView
        root.addView(progressView, LinearLayout.LayoutParams(-1, dp(3)))

        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xffe9eeeb.toInt())
            clipToPadding = false
        }
        val vertical = ScrollView(this).apply {
            isFillViewport = false
            setBackgroundColor(0xffe9eeeb.toInt())
        }
        val pageImageView = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            adjustViewBounds = false
        }
        pageImage = pageImageView
        vertical.addView(pageImageView, FrameLayout.LayoutParams(-2, -2))
        horizontal.addView(vertical, FrameLayout.LayoutParams(-2, -1))
        root.addView(horizontal, LinearLayout.LayoutParams(-1, 0, 1f))

        val pageControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(pageControls, LinearLayout.LayoutParams(-1, dp(54)))

        val previous = toolButton("Previous").apply { setOnClickListener { changePage(-1) } }
        pageControls.addView(previous, LinearLayout.LayoutParams(0, dp(40), 1f))
        val pageLabelView = control("Page — / —", 12, 0xff31534b.toInt()).apply {
            gravity = Gravity.CENTER
        }
        pageLabel = pageLabelView
        pageControls.addView(pageLabelView, LinearLayout.LayoutParams(dp(105), dp(40)))
        val next = toolButton("Next").apply { setOnClickListener { changePage(1) } }
        pageControls.addView(next, LinearLayout.LayoutParams(0, dp(40), 1f))

        val statusView = control("Preparing offline PDF…", 10, 0xff60766e.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(7), dp(13), dp(7))
            setBackgroundColor(Color.WHITE)
        }
        status = statusView
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        applySafeAreaInsets(root)
        updatePageLabel()
    }

    private fun openPdf() {
        status?.text = "Preparing offline PDF…"
        worker.execute {
            var opened: ParcelFileDescriptor? = null
            try {
                var loaded: PdfRenderer? = null
                try {
                    opened = contentResolver.openFileDescriptor(checkNotNull(documentUri), "r")
                    if (opened != null) loaded = PdfRenderer(opened!!)
                } catch (_: Exception) {
                    try { opened?.close() } catch (_: Exception) { }
                    opened = null
                }

                // SAF providers that expose non-seekable streams use the bounded cache fallback.
                if (loaded == null) {
                    val cacheFile = copyToPrivateCache()
                    cachedPdf = cacheFile
                    opened = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    loaded = PdfRenderer(opened!!)
                }

                val readyRenderer = checkNotNull(loaded)
                descriptor = opened
                renderer = readyRenderer
                pageCount = readyRenderer.pageCount
                if (pageCount < 1) throw IllegalStateException("This PDF has no pages.")
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    updatePageLabel()
                    renderCurrentPage()
                }
            } catch (exception: Exception) {
                if (opened != null && descriptor !== opened) {
                    try { opened?.close() } catch (_: Exception) { }
                }
                runOnUiThread { showError(exception.message) }
            }
        }
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
        } catch (exception: Exception) {
            target.delete()
            if (exception is IOException) throw exception
            throw IOException("Could not prepare the selected PDF.", exception)
        }
    }

    private fun renderCurrentPage() {
        val activeRenderer = renderer ?: return
        if (pageIndex < 0 || pageIndex >= pageCount) return

        val generation = ++renderGeneration
        val targetPage = pageIndex
        val targetZoom = zoom
        progress?.visibility = View.VISIBLE
        status?.text = "Rendering page ${targetPage + 1}…"
        updatePageLabel()

        worker.execute {
            var bitmap: Bitmap? = null
            try {
                val page = activeRenderer.openPage(targetPage)
                try {
                    val screenWidth = maxOf(
                        dp(320),
                        resources.displayMetrics.widthPixels - dp(28)
                    )
                    var scale = (screenWidth / page.width.toFloat()) * targetZoom
                    val maxDimensionScale = minOf(
                        MAX_PAGE_DIMENSION / page.width.toFloat(),
                        MAX_PAGE_DIMENSION / page.height.toFloat()
                    )
                    scale = minOf(scale, maxDimensionScale)
                    val pixels = page.width.toDouble() * page.height * scale * scale
                    if (pixels > MAX_BITMAP_PIXELS) {
                        scale *= kotlin.math.sqrt(MAX_BITMAP_PIXELS / pixels).toFloat()
                    }

                    val width = maxOf(1, (page.width * scale).roundToInt())
                    val height = maxOf(1, (page.height * scale).roundToInt())
                    val renderBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap = renderBitmap
                    renderBitmap.eraseColor(Color.WHITE)
                    page.render(
                        renderBitmap, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    bitmap = null
                    runOnUiThread {
                        if (generation != renderGeneration || isFinishing || pageImage == null) {
                            renderBitmap.recycle()
                            return@runOnUiThread
                        }
                        val previousBitmap = displayedBitmap
                        displayedBitmap = renderBitmap
                        pageImage?.setImageBitmap(renderBitmap)
                        pageImage?.requestLayout()
                        if (previousBitmap != null && !previousBitmap.isRecycled) {
                            previousBitmap.recycle()
                        }
                        progress?.visibility = View.GONE
                        status?.text = "PDF · Read only · Original file unchanged"
                        updatePageLabel()
                    }
                } finally {
                    page.close()
                }
            } catch (_: Exception) {
                bitmap?.let { if (!it.isRecycled) it.recycle() }
                runOnUiThread {
                    if (generation != renderGeneration || isFinishing) return@runOnUiThread
                    progress?.visibility = View.GONE
                    status?.text = "Could not render this PDF page"
                    Toast.makeText(this, "PDF page render failed.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun changePage(direction: Int) {
        if (pageCount < 1) return
        val next = (pageIndex + direction).coerceIn(0, pageCount - 1)
        if (next == pageIndex) return
        pageIndex = next
        renderCurrentPage()
    }

    private fun changeZoom(delta: Float) {
        if (renderer == null) return
        val next = (zoom + delta).coerceIn(0.7f, 3.0f)
        if (kotlin.math.abs(next - zoom) < 0.001f) return
        zoom = next
        renderCurrentPage()
    }

    private fun updatePageLabel() {
        pageLabel?.text = if (pageCount > 0) {
            "Page ${pageIndex + 1} / $pageCount"
        } else {
            "Page — / —"
        }
    }

    private fun showError(message: String?) {
        if (isFinishing) return
        progress?.visibility = View.GONE
        status?.text = "Could not open this PDF"
        android.app.AlertDialog.Builder(this)
            .setTitle("PDF could not be opened")
            .setMessage(
                if (message.isNullOrBlank()) {
                    "The file may be damaged, encrypted or unavailable."
                } else {
                    message
                }
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun applySafeAreaInsets(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            insets.consumeSystemWindowInsets()
        }
        root.requestApplyInsets()
    }

    private fun toolButton(value: String): TextView {
        val view = control(value, 12, 0xff31534b.toInt()).apply {
            gravity = Gravity.CENTER
        }
        view.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xffeef3f0.toInt())
            cornerRadius = dp(11).toFloat()
        }
        return view
    }

    private fun control(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.NORMAL
            )
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        renderGeneration++
        pageImage?.setImageDrawable(null)
        displayedBitmap?.let { if (!it.isRecycled) it.recycle() }
        displayedBitmap = null

        worker.execute {
            try { renderer?.close() } catch (_: Exception) { } finally { renderer = null }
            try { descriptor?.close() } catch (_: Exception) { } finally { descriptor = null }
            try { cachedPdf?.delete() } catch (_: Exception) { } finally { cachedPdf = null }
        }
        worker.shutdown()
        super.onDestroy()
    }
}
