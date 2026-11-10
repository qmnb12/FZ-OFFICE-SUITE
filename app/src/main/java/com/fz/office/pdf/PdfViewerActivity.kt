package com.fz.office.pdf

import android.app.Activity
import android.content.Context
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
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
        private const val MAX_SEARCH_HITS = 3000
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
    private val horizontalScrollFractionsByPage = HashMap<Int, Float>()
    private var shell: ReaderDashboard? = null
    private var pageIndex = 0
    private var zoomScale = 1f
    @Volatile private var closing = false
    private var activeSearchQuery = ""
    private var searchHits: List<PdfSearchHit> = emptyList()
    private var searchHitIndex = -1
    @Volatile private var searchGeneration = 0
    private var searchPending = false
    private var pendingSearchForward = true
    private var searchHighlightsByPage: Map<Int, List<PdfSearchHighlight>> = emptyMap()

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
        val zoomHost = PdfGestureHost(this, recycler)

        dashboard.setDocumentView(zoomHost)
        DocumentViewerController(
            this,
            dashboard,
            object : DocumentViewerSurface {
                override val view: View = recycler
                override val supportsFind: Boolean = Build.VERSION.SDK_INT >= 35
                override val supportsScrollNavigation: Boolean = true
                override val supportsZoom: Boolean = true
                override val supportsPageNavigation: Boolean = true
                override val supportsPrint: Boolean = true

                override fun scrollBackward() = jumpToPage((pageIndex - 1).coerceAtLeast(0))
                override fun scrollForward() = jumpToPage((pageIndex + 1).coerceAtMost(pageSizes.lastIndex))
                override fun scrollToStart() = jumpToPage(0)
                override fun scrollToEnd() = jumpToPage(pageSizes.lastIndex)
                override fun find(query: String, forward: Boolean) = findPdfText(query, forward)
                override fun clearFind() = clearPdfFind()

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

    private inner class PdfGestureHost(
        context: Context,
        private val recycler: RecyclerView
    ) : FrameLayout(context) {
        private var previewZoom = zoomScale

        private val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    previewZoom = zoomScale
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    // ScaleGestureDetector reports the incremental factor since the previous move.
                    previewZoom = (previewZoom * detector.scaleFactor).coerceIn(1f, 6f)
                    applyPreviewZoom(previewZoom)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    setZoom(previewZoom)
                }
            }
        )

        private val doubleTapDetector = android.view.GestureDetector(
            context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                    setZoom(if (zoomScale < 1.4f) 2f else 1f)
                    return true
                }
            }
        )

        init {
            isClickable = true
            addView(
                recycler,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
            // Observe touches before child HorizontalScrollViews can consume them.
            scaleDetector.onTouchEvent(event)
            doubleTapDetector.onTouchEvent(event)
            return super.dispatchTouchEvent(event)
        }

        override fun onInterceptTouchEvent(event: android.view.MotionEvent): Boolean {
            // Capture the stream as soon as a second finger lands, before nested page scrollers consume it.
            // Single-finger vertical scrolling continues through RecyclerView unchanged.
            return event.pointerCount >= 2 || scaleDetector.isInProgress
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean = true
    }

    private fun applyPreviewZoom(value: Float) {
        val baseZoom = zoomScale.coerceAtLeast(0.01f)
        val factor = value / baseZoom
        for (index in 0 until (recyclerView?.childCount ?: 0)) {
            val pageScroller = recyclerView?.getChildAt(index) as? HorizontalScrollView ?: continue
            val pageContent = pageScroller.getChildAt(0) ?: continue
            pageContent.pivotX = pageContent.width / 2f
            pageContent.pivotY = pageContent.height / 2f
            pageContent.scaleX = factor
            pageContent.scaleY = factor
        }
    }

    private fun captureHorizontalScrollFractions(): Map<Int, Float> {
        val recycler = recyclerView ?: return emptyMap()
        val positions = LinkedHashMap<Int, Float>()
        for (index in 0 until recycler.childCount) {
            val scroller = recycler.getChildAt(index) as? HorizontalScrollView ?: continue
            val position = recycler.getChildAdapterPosition(scroller)
            if (position == RecyclerView.NO_POSITION) continue
            val content = scroller.getChildAt(0) ?: continue
            val range = horizontalScrollRange(scroller)
            if (range > 0) {
                val fraction = (scroller.scrollX.toFloat() / range).coerceIn(0f, 1f)
                positions[position] = fraction
                horizontalScrollFractionsByPage[position] = fraction
            }
        }
        return positions
    }

    private fun restoreHorizontalScrollFractions(positions: Map<Int, Float>) {
        val recycler = recyclerView ?: return
        for ((position, fraction) in positions) {
            val holder = recycler.findViewHolderForAdapterPosition(position) ?: continue
            val scroller = holder.itemView as? HorizontalScrollView ?: continue
            scroller.post {
                val range = horizontalScrollRange(scroller)
                horizontalScrollFractionsByPage[position] = fraction
                scroller.scrollTo((range * fraction).roundToInt(), 0)
            }
        }
    }

    private fun refreshVisibleSearchHighlights() {
        val recycler = recyclerView ?: return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            val position = recycler.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position !in pageSizes.indices) continue
            val holder = recycler.getChildViewHolder(child) as? PdfPageHolder ?: continue
            val size = pageSizes[position]
            holder.overlay.setHighlights(
                size.width,
                size.height,
                searchHighlightsByPage[position].orEmpty()
            )
        }
    }

    private fun setZoom(value: Float) {
        val horizontalPositions = captureHorizontalScrollFractions()
        val recycler = recyclerView ?: return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager
        val anchor = layoutManager?.findFirstVisibleItemPosition()
            ?.takeIf { it != RecyclerView.NO_POSITION } ?: pageIndex
        val anchorView = layoutManager?.findViewByPosition(anchor)
        val verticalOffset = anchorView?.top?.minus(recycler.paddingTop) ?: 0

        zoomScale = value.coerceIn(1f, 6f)
        pageAdapter?.notifyDataSetChanged()
        recycler.post {
            if (closing) return@post
            (recycler.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(anchor, verticalOffset)
            recycler.post {
                if (closing) return@post
                restoreHorizontalScrollFractions(horizontalPositions)
                updatePageStatus()
            }
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
        val matchStatus = if (searchHits.isNotEmpty() && searchHitIndex >= 0) {
            " · Match " + (searchHitIndex + 1) + "/" + searchHits.size
        } else {
            ""
        }
        shell?.setStatus(
            "Page " + (pageIndex + 1) + " / " + total + " · " +
                (zoomScale * 100).roundToInt() + "%" + matchStatus + " · Scroll · Pinch to zoom"
        )
    }

    private fun findPdfText(query: String, forward: Boolean) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        if (Build.VERSION.SDK_INT < 35) {
            Toast.makeText(this, "PDF text search requires Android 15 or later.", Toast.LENGTH_LONG).show()
            return
        }
        if (pdfDocument == null || pageSizes.isEmpty()) {
            Toast.makeText(this, "Wait for the PDF to finish opening, then try Find again.", Toast.LENGTH_SHORT).show()
            return
        }

        if (normalized != activeSearchQuery) {
            activeSearchQuery = normalized
            searchGeneration += 1
            val generation = searchGeneration
            searchPending = true
            pendingSearchForward = forward
            searchHits = emptyList()
            searchHitIndex = -1
            searchHighlightsByPage = emptyMap()
            refreshVisibleSearchHighlights()
            shell?.setStatus("Searching PDF text…")

            ioExecutor.execute {
                val matches = ArrayList<PdfSearchHit>()
                var failure: String? = null
                val document = pdfDocument
                if (document == null) {
                    failure = "PDF is not ready for text search."
                } else {
                    for (page in pageSizes.indices) {
                        if (closing || generation != searchGeneration) return@execute
                        try {
                            val pageMatches = document.searchPage(page, normalized)
                            for (match in pageMatches) {
                                if (matches.size >= MAX_SEARCH_HITS) break
                                matches.add(PdfSearchHit(page, match.bounds))
                            }
                        } catch (error: Exception) {
                            failure = error.message ?: "PDF text search failed."
                            break
                        }
                        if (matches.size >= MAX_SEARCH_HITS) break
                    }
                }

                runOnUiThread {
                    if (closing || isFinishing || isDestroyed || generation != searchGeneration) return@runOnUiThread
                    searchPending = false
                    searchHits = matches
                    searchHitIndex = when {
                        matches.isEmpty() -> -1
                        pendingSearchForward -> 0
                        else -> matches.lastIndex
                    }
                    refreshSearchHighlights()
                    if (matches.isEmpty()) {
                        shell?.setStatus(
                            failure ?: "No text matches found · Scanned-only pages may not be searchable"
                        )
                    } else {
                        navigateToSearchHit()
                    }
                }
            }
        } else if (searchPending) {
            pendingSearchForward = forward
        } else if (searchHits.isNotEmpty()) {
            searchHitIndex = if (forward) {
                (searchHitIndex + 1) % searchHits.size
            } else {
                (searchHitIndex - 1 + searchHits.size) % searchHits.size
            }
            refreshSearchHighlights()
            navigateToSearchHit()
        } else {
            shell?.setStatus("No text matches found for this search.")
        }
    }

    private fun clearPdfFind() {
        searchGeneration += 1
        activeSearchQuery = ""
        searchPending = false
        searchHits = emptyList()
        searchHitIndex = -1
        searchHighlightsByPage = emptyMap()
        refreshVisibleSearchHighlights()
        updatePageStatus()
    }

    private fun refreshSearchHighlights() {
        val grouped = HashMap<Int, MutableList<PdfSearchHighlight>>()
        searchHits.forEachIndexed { index, hit ->
            grouped.getOrPut(hit.pageIndex) { ArrayList() }.add(
                PdfSearchHighlight(hit.bounds, index == searchHitIndex)
            )
        }
        searchHighlightsByPage = grouped
        refreshVisibleSearchHighlights()
    }

    private fun navigateToSearchHit() {
        val hit = searchHits.getOrNull(searchHitIndex) ?: return
        jumpToPage(hit.pageIndex)
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

    private fun horizontalScrollRange(scroller: HorizontalScrollView): Int {
        val content = scroller.getChildAt(0) ?: return 0
        return (
            content.width + scroller.paddingLeft + scroller.paddingRight - scroller.width
        ).coerceAtLeast(0)
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
            val overlay = PdfMatchOverlayView(this@PdfViewerActivity).apply {
                elevation = dp(3).toFloat()
            }
            val pageContent = FrameLayout(this@PdfViewerActivity).apply {
                addView(image, FrameLayout.LayoutParams(1, 1))
                addView(overlay, FrameLayout.LayoutParams(1, 1))
            }
            val scroller = HorizontalScrollView(this@PdfViewerActivity).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                clipToPadding = false
                setPadding(dp(8), dp(8), dp(8), dp(14))
                setBackgroundColor(CANVAS)
                addView(pageContent, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
            }
            return PdfPageHolder(scroller, pageContent, image, overlay)
        }

        override fun onBindViewHolder(holder: PdfPageHolder, position: Int) {
            val size = pages[position]
            val baseWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(220))
            val displayWidth = (baseWidth * zoomScale).roundToInt().coerceAtMost(MAX_DISPLAY_WIDTH_PX)
            val displayHeight = (displayWidth.toDouble() * size.height / size.width)
                .coerceAtMost(16_000.0)
                .roundToInt()
                .coerceAtLeast(1)
            holder.pageContent.scaleX = 1f
            holder.pageContent.scaleY = 1f
            holder.pageContent.layoutParams = FrameLayout.LayoutParams(
                displayWidth,
                displayHeight,
                Gravity.CENTER
            )
            holder.image.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            holder.overlay.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            holder.overlay.setHighlights(
                size.width,
                size.height,
                searchHighlightsByPage[position].orEmpty()
            )
            holder.scroller.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                displayHeight + dp(22)
            )
            holder.scroller.setOnScrollChangeListener { _, scrollX, _, _, _ ->
                val boundPosition = holder.bindingAdapterPosition
                if (boundPosition != RecyclerView.NO_POSITION) {
                    val range = horizontalScrollRange(holder.scroller)
                    if (range > 0) {
                        horizontalScrollFractionsByPage[boundPosition] =
                            (scrollX.toFloat() / range).coerceIn(0f, 1f)
                    } else {
                        horizontalScrollFractionsByPage.remove(boundPosition)
                    }
                }
            }
            val savedFraction = horizontalScrollFractionsByPage[position] ?: 0f
            holder.scroller.post {
                if (holder.bindingAdapterPosition != position) return@post
                val range = horizontalScrollRange(holder.scroller)
                holder.scroller.scrollTo((range * savedFraction).roundToInt(), 0)
            }

            val renderWidth = displayWidth.coerceAtMost(4096)
            val cacheKey = "page:" + position + ":width:" + renderWidth
            holder.image.tag = cacheKey
            val cached = bitmapCache.get(cacheKey)
            holder.image.setImageBitmap(cached)
            if (cached == null) requestRender(holder, position, renderWidth, cacheKey)
        }

        override fun onViewRecycled(holder: PdfPageHolder) {
            holder.scroller.setOnScrollChangeListener(null)
            holder.image.tag = null
            holder.image.setImageDrawable(null)
            holder.overlay.setHighlights(1, 1, emptyList())
            super.onViewRecycled(holder)
        }
    }

    private class PdfPageHolder(
        val scroller: HorizontalScrollView,
        val pageContent: FrameLayout,
        val image: ImageView,
        val overlay: PdfMatchOverlayView
    ) : RecyclerView.ViewHolder(scroller)
}

private data class PdfSearchHit(
    val pageIndex: Int,
    val bounds: List<RectF>
)

private data class PdfSearchHighlight(
    val bounds: List<RectF>,
    val active: Boolean
)

private class PdfMatchOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pageWidth = 1f
    private var pageHeight = 1f
    private var highlights: List<PdfSearchHighlight> = emptyList()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setHighlights(
        sourceWidth: Int,
        sourceHeight: Int,
        values: List<PdfSearchHighlight>
    ) {
        pageWidth = sourceWidth.coerceAtLeast(1).toFloat()
        pageHeight = sourceHeight.coerceAtLeast(1).toFloat()
        highlights = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || highlights.isEmpty()) return
        val scaleX = width / pageWidth
        val scaleY = height / pageHeight
        paint.style = Paint.Style.FILL
        highlights.forEach { highlight ->
            if (!highlight.active) drawHighlight(canvas, highlight, scaleX, scaleY)
        }
        highlights.forEach { highlight ->
            if (highlight.active) drawHighlight(canvas, highlight, scaleX, scaleY)
        }
    }

    private fun drawHighlight(
        canvas: Canvas,
        highlight: PdfSearchHighlight,
        scaleX: Float,
        scaleY: Float
    ) {
        paint.color = if (highlight.active) {
            Color.argb(145, 255, 145, 0)
        } else {
            Color.argb(92, 255, 226, 58)
        }
        highlight.bounds.forEach { bounds ->
            val rect = RectF(
                bounds.left * scaleX,
                bounds.top * scaleY,
                bounds.right * scaleX,
                bounds.bottom * scaleY
            )
            canvas.drawRoundRect(rect, 3f, 3f, paint)
        }
    }
}
