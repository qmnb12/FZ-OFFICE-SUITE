package com.fz.office.pdf

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.Toast
import android.util.LruCache
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fz.office.viewer.ReaderDashboard
import com.fz.office.viewer.DocumentViewerController
import com.fz.office.viewer.DocumentViewerSurface
import com.fz.office.viewer.UriPdfPrintAdapter
import android.print.PrintDocumentAdapter
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * FZ Office's read-only PDF screen.
 *
 * Page rendering is handled by FzPdfDocument and Android's platform PdfRenderer.
 * Only visible pages are rasterized; rendering is serialized on one worker and
 * recently used page bitmaps are kept in a bounded LRU cache.
 */
class PdfViewerActivity : Activity() {

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val CANVAS = 0xffe9eeeb.toInt()
        private const val MAX_DISPLAY_WIDTH_PX = 8192
        private const val BITMAP_CACHE_KB = 32 * 1024
    }

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val bitmapCache = object : LruCache<String, Bitmap>(BITMAP_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val pendingRenders = Collections.synchronizedSet(mutableSetOf<String>())

    private var documentUri: Uri? = null
    private var documentName = "PDF document"
    private var pdfDocument: FzPdfDocument? = null
    private var pageSizes: List<FzPdfDocument.PageSize> = emptyList()
    private var recyclerView: RecyclerView? = null
    private var pageAdapter: PdfPageAdapter? = null
    private var shell: ReaderDashboard? = null
    private var pageIndex = 0
    private var zoomScale = 1f
    private var closing = false

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
        zoomScale = (state?.getFloat("pdf_zoom") ?: 1f).coerceIn(1f, 6f)
        buildScreen()
        openPdf()
    }

    private fun buildScreen() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()

        val dashboard = ReaderDashboard(this, documentName, "PDF · FZ CORE · READ ONLY") { finish() }
        shell = dashboard

        val recycler = RecyclerView(this).apply {
            setBackgroundColor(CANVAS)
            layoutManager = LinearLayoutManager(this@PdfViewerActivity, RecyclerView.VERTICAL, false)
            clipToPadding = false
            setPadding(dp(8), dp(12), dp(8), dp(12))
            itemAnimator = null
            setHasFixedSize(false)
        }
        recyclerView = recycler
        pageAdapter = PdfPageAdapter()
        recycler.adapter = pageAdapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                updatePageStatus()
            }
        })
        installZoomGestures(recycler)

        dashboard.setDocumentView(recycler)
        DocumentViewerController(
            this,
            dashboard,
            object : DocumentViewerSurface {
                override val view: View = recycler
                override val supportsScrollNavigation: Boolean = true
                override val supportsZoom: Boolean = true
                override val supportsPageNavigation: Boolean = true
                override val supportsPrint: Boolean = true

                override fun scrollBackward() = jumpToPage((pageIndex - 1).coerceAtLeast(0))
                override fun scrollForward() = jumpToPage((pageIndex + 1).coerceAtMost(pageSizes.lastIndex))
                override fun scrollToStart() = jumpToPage(0)
                override fun scrollToEnd() = jumpToPage(pageSizes.lastIndex)

                override fun zoomIn() = setZoom(zoomScale * 1.2f)
                override fun zoomOut() = setZoom(zoomScale / 1.2f)
                override fun fitToWidth() = setZoom(1f)
                override fun pageCount(onResult: (Int?) -> Unit) {
                    onResult(pageSizes.size.takeIf { it > 0 })
                }
                override fun goToPage(index: Int) = jumpToPage(index)
                override fun createPrintAdapter(jobName: String): PrintDocumentAdapter? {
                    val uri = documentUri ?: return null
                    return UriPdfPrintAdapter(this@PdfViewerActivity, uri, jobName)
                }
            },
            documentUri,
            documentName,
            documentUri?.let { contentResolver.getType(it) } ?: "application/pdf"
        )
        dashboard.setStatus("Opening PDF · Scroll to read · Pinch or double-tap to zoom")
        dashboard.setLoading(true)

        setContentView(dashboard.root)
        applySafeAreaInsets(dashboard.root)
    }

    private fun openPdf() {
        val uri = documentUri ?: return
        shell?.setLoading(true)
        shell?.setStatus("Opening PDF with FZ Core…")

        ioExecutor.execute {
            var opened: FzPdfDocument? = null
            try {
                opened = FzPdfDocument.open(this, uri)
                val sizes = ArrayList<FzPdfDocument.PageSize>(opened.pageCount)
                for (index in 0 until opened.pageCount) {
                    if (closing) {
                        opened.close()
                        return@execute
                    }
                    sizes.add(opened.pageSize(index))
                }

                val readyDocument = checkNotNull(opened)
                runOnUiThread {
                    if (closing || isFinishing || isDestroyed) {
                        readyDocument.close()
                        return@runOnUiThread
                    }
                    pdfDocument = readyDocument
                    pageSizes = sizes
                    pageIndex = pageIndex.coerceIn(0, (sizes.size - 1).coerceAtLeast(0))
                    pageAdapter?.submitPages(sizes)
                    shell?.setLoading(false)
                    shell?.setStatus("PDF ready · Scroll · Pinch to zoom")
                    recyclerView?.post {
                        if (closing) return@post
                        (recyclerView?.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(pageIndex, 0)
                        updatePageStatus()
                    }
                }
                opened = null
            } catch (error: Exception) {
                opened?.close()
                runOnUiThread {
                    if (!closing && !isFinishing && !isDestroyed) showError(error)
                }
            }
        }
    }

    private fun installZoomGestures(recycler: RecyclerView) {
        var startZoom = zoomScale
        var previewZoom = zoomScale
        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    startZoom = zoomScale
                    previewZoom = zoomScale
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    previewZoom = (startZoom * detector.scaleFactor).coerceIn(1f, 6f)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    setZoom(previewZoom)
                }
            }
        )
        val doubleTapDetector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                    setZoom(if (zoomScale < 1.4f) 2f else 1f)
                    return true
                }
            }
        )
        recycler.setOnTouchListener { _, event ->
            val scaling = scaleDetector.onTouchEvent(event)
            val doubleTap = doubleTapDetector.onTouchEvent(event)
            scaling || scaleDetector.isInProgress || doubleTap
        }
    }

    private fun setZoom(value: Float) {
        zoomScale = value.coerceIn(1f, 6f)
        val anchor = pageIndex
        pageAdapter?.notifyDataSetChanged()
        recyclerView?.post {
            if (closing) return@post
            (recyclerView?.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(anchor, 0)
            updatePageStatus()
        }
    }

    private fun requestRender(holder: PdfPageHolder, page: Int, renderWidth: Int, cacheKey: String) {
        val document = pdfDocument ?: return
        if (!pendingRenders.add(cacheKey)) return
        ioExecutor.execute {
            if (closing) {
                pendingRenders.remove(cacheKey)
                return@execute
            }
            val bitmap = try {
                document.renderPage(page, renderWidth)
            } catch (_: Exception) {
                null
            }
            pendingRenders.remove(cacheKey)
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap)
                runOnUiThread {
                    if (!closing && holder.image.tag == cacheKey) {
                        holder.image.setImageBitmap(bitmap)
                    }
                }
            } else {
                runOnUiThread {
                    if (!closing && holder.image.tag == cacheKey) {
                        shell?.setStatus("Could not render PDF page " + (page + 1))
                    }
                }
            }
        }
    }

    private fun updatePageStatus() {
        val total = pageSizes.size
        if (total < 1) {
            shell?.setStatus("Preparing PDF…")
            return
        }
        val visible = (recyclerView?.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
        if (visible != null && visible != RecyclerView.NO_POSITION) {
            pageIndex = visible.coerceIn(0, total - 1)
        }
        shell?.setStatus(
            "Page " + (pageIndex + 1) + " / " + total + " · " +
                (zoomScale * 100).roundToInt() + "% · Scroll · Pinch to zoom"
        )
    }

    private fun jumpToPage(index: Int) {
        if (pageSizes.isEmpty()) return
        pageIndex = index.coerceIn(0, pageSizes.lastIndex)
        (recyclerView?.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(pageIndex, 0)
        updatePageStatus()
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
        val visible = (recyclerView?.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
        if (visible != null && visible != RecyclerView.NO_POSITION) pageIndex = visible
        outState.putInt("pdf_page", pageIndex)
        outState.putFloat("pdf_zoom", zoomScale)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        closing = true
        bitmapCache.evictAll()
        pendingRenders.clear()
        val document = pdfDocument
        pdfDocument = null
        try {
            ioExecutor.execute { document?.close() }
        } catch (_: Exception) {
            document?.close()
        }
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private inner class PdfPageAdapter : RecyclerView.Adapter<PdfPageHolder>() {
        private var pages: List<FzPdfDocument.PageSize> = emptyList()

        fun submitPages(newPages: List<FzPdfDocument.PageSize>) {
            pages = newPages
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageHolder {
            val image = ImageView(this@PdfViewerActivity).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(2).toFloat()
                }
                elevation = dp(2).toFloat()
                contentDescription = "PDF page"
            }
            val scroller = HorizontalScrollView(this@PdfViewerActivity).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                clipToPadding = false
                setPadding(dp(8), dp(8), dp(8), dp(14))
                setBackgroundColor(CANVAS)
                addView(image, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
            }
            return PdfPageHolder(scroller, image)
        }

        override fun onBindViewHolder(holder: PdfPageHolder, position: Int) {
            val size = pages[position]
            val baseWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(220))
            val displayWidth = (baseWidth * zoomScale).roundToInt().coerceAtMost(MAX_DISPLAY_WIDTH_PX)
            val displayHeight = (displayWidth.toDouble() * size.height / size.width)
                .coerceAtMost(16_000.0)
                .roundToInt()
                .coerceAtLeast(1)
            holder.image.layoutParams = FrameLayout.LayoutParams(
                displayWidth,
                displayHeight,
                Gravity.CENTER
            )
            holder.scroller.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                displayHeight + dp(22)
            )

            val renderWidth = displayWidth.coerceAtMost(4096)
            val cacheKey = "page:" + position + ":width:" + renderWidth
            holder.image.tag = cacheKey
            val cached = bitmapCache.get(cacheKey)
            holder.image.setImageBitmap(cached)
            if (cached == null) requestRender(holder, position, renderWidth, cacheKey)
        }

        override fun onViewRecycled(holder: PdfPageHolder) {
            holder.image.tag = null
            holder.image.setImageDrawable(null)
            super.onViewRecycled(holder)
        }
    }

    private class PdfPageHolder(
        val scroller: HorizontalScrollView,
        val image: ImageView
    ) : RecyclerView.ViewHolder(scroller)
}
