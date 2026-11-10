package com.fz.office.pdf

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.ScaleGestureDetector
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.fz.office.viewer.ReaderDashboard
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The outer horizontal pan surface must never steal a vertical page swipe.
 * At fit width it does not intercept touches at all; while zoomed it intercepts
 * only a clearly horizontal drag, leaving vertical gestures to RecyclerView.
 */
private class PdfHorizontalPanScrollView(context: Context) : HorizontalScrollView(context) {
    var horizontalPanEnabled = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var horizontalDragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!horizontalPanEnabled) return false
        if (horizontalDragging) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) horizontalDragging = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                super.onInterceptTouchEvent(event)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.x - downX)
                val dy = kotlin.math.abs(event.y - downY)
                // A vertical swipe always belongs to RecyclerView, even when zoomed.
                if (dy > touchSlop && dy >= dx) return false
                if (dx > touchSlop && dx > dy) {
                    horizontalDragging = super.onInterceptTouchEvent(event)
                    return horizontalDragging
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                horizontalDragging = false
                return false
            }
        }
        return false
    }
}

/** Offline read-only PDF reader. Pages render lazily on one worker into a bounded cache. */
class PdfViewerActivity : Activity() {

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val MAX_PAGE_DIMENSION = 4096
        private const val MAX_BITMAP_PIXELS = 8_000_000L
        private const val MAX_PDF_BYTES = 512L * 1024L * 1024L
        private const val DEFAULT_PAGE_ASPECT = 1.4142f
        private const val CANVAS = 0xffe9eeeb.toInt()
        private const val INK = 0xff172c28.toInt()
        private const val MUTED = 0xff60766e.toInt()
        private const val ACCENT = 0xff176b5b.toInt()
    }

    private class ThumbnailViews(val image: ImageView, val label: TextView)

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRenders = HashSet<String>()
    private val pendingThumbnails = HashSet<Int>()
    private val bookmarks = linkedSetOf<Int>()
    private val preferences by lazy {
        getSharedPreferences("fz_pdf_reader", MODE_PRIVATE)
    }
    private val pageCache = object : LruCache<String, Bitmap>(pageCacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val thumbnailCache = object : LruCache<Int, Bitmap>(4 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private var documentUri: Uri? = null
    private var documentName = "PDF document"
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var cachedPdf: File? = null
    private var pageCount = 0
    private var pageRatios = FloatArray(0)
    private var pageIndex = 0
    @Volatile private var visibleStartPage = 0
    @Volatile private var visibleEndPage = 5
    private var zoom = 1f
    private var renderBucket = 1f
    @Volatile private var closing = false

    private var horizontalScroll: PdfHorizontalPanScrollView? = null
    private var recycler: RecyclerView? = null
    private var pageLabel: TextView? = null
    private var bookmarkButton: TextView? = null
    private var status: TextView? = null
    private var progress: ProgressBar? = null
    private var pageAdapter: PdfPageAdapter? = null

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
        pageIndex = state?.getInt("pdf_page", 0) ?: 0
        zoom = (state?.getFloat("pdf_zoom", 1f) ?: 1f).coerceIn(1f, 4f)
        renderBucket = qualityBucket(zoom)
        loadBookmarks()
        buildScreen()
        openPdf()
    }

    private fun buildScreen() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()

        val shell = ReaderDashboard(this, documentName, "PDF · VIEW ONLY") { finish() }
        shell.addToolbarAction("Grid", "Page thumbnails") { showThumbnails() }

        val star = shell.addToolbarAction("☆", "Bookmark current page") { toggleBookmark() }
        bookmarkButton = star
        star.setOnLongClickListener {
            showBookmarks()
            true
        }

        shell.addToolbarAction("↗", "Share PDF") { shareDocument() }

        progress = shell.progress
        status = shell.status

        val horizontal = PdfHorizontalPanScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setBackgroundColor(CANVAS)
            horizontalPanEnabled = zoom > 1.01f
        }
        horizontalScroll = horizontal

        val list = RecyclerView(this).apply {
            setBackgroundColor(CANVAS)
            setPadding(dp(8), dp(12), dp(8), dp(24))
            clipToPadding = false
            setItemViewCacheSize(3)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            verticalScrollBarEnabled = true
            itemAnimator = null
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                this@PdfViewerActivity,
                RecyclerView.VERTICAL,
                false
            )
        }
        recycler = list
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.top = dp(5)
                outRect.bottom = dp(10)
            }
        })
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                updateCurrentPage()
            }
        })

        horizontal.addView(
            list,
            FrameLayout.LayoutParams(
                maxOf(resources.displayMetrics.widthPixels, pageWidthPx() + dp(28)),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        shell.setDocumentView(horizontal)

        val previous = shell.addControl("‹ Prev", 1.05f) { jumpToPage(pageIndex - 1) }
        previous.contentDescription = "Previous page"

        val label = shell.addControl("Page — / —", 1.15f) { showGoToPage() }
        label.setTextColor(ACCENT)
        pageLabel = label
        label.setOnLongClickListener {
            showBookmarks()
            true
        }

        val next = shell.addControl("Next ›", 1.05f) { jumpToPage(pageIndex + 1) }
        next.contentDescription = "Next page"
        shell.addControl("−", 0.62f) { setZoom(zoom - 0.25f) }
        shell.addControl("Fit", 0.72f) { setZoom(1f) }
        shell.addControl("+", 0.62f) { setZoom(zoom + 0.25f) }

        setContentView(shell.root)
        applySafeAreaInsets(shell.root)
        updatePageLabel()

        var previewZoom = zoom
        var focusX = 0f
        var focusY = 0f
        val detector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    previewZoom = zoom
                    focusX = detector.focusX
                    focusY = detector.focusY
                    list.pivotX = focusX
                    list.pivotY = focusY
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    previewZoom = (previewZoom * detector.scaleFactor).coerceIn(1f, 4f)
                    focusX = detector.focusX
                    focusY = detector.focusY
                    list.pivotX = focusX
                    list.pivotY = focusY
                    val previewScale = previewZoom / zoom
                    list.scaleX = previewScale
                    list.scaleY = previewScale
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    list.scaleX = 1f
                    list.scaleY = 1f
                    setZoom(previewZoom, focusX, focusY)
                }
            }
        )
        // A one-finger event is always left to RecyclerView for vertical scrolling.
        // Only a two-finger gesture is consumed by the scale detector.
        list.setOnTouchListener { _, event ->
            val handled = detector.onTouchEvent(event)
            event.pointerCount > 1 && handled
        }
    }

    private fun openPdf() {
        status?.text = "Preparing offline PDF…"
        worker.execute {
            var opened: ParcelFileDescriptor? = null
            var loaded: PdfRenderer? = null
            try {
                try {
                    opened = contentResolver.openFileDescriptor(checkNotNull(documentUri), "r")
                    if (opened != null) loaded = PdfRenderer(opened)
                } catch (_: Exception) {
                    try { opened?.close() } catch (_: Exception) { }
                    opened = null
                }

                // SAF providers that expose non-seekable streams use a bounded private cache copy.
                if (loaded == null) {
                    val cacheFile = copyToPrivateCache()
                    cachedPdf = cacheFile
                    opened = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    loaded = PdfRenderer(opened)
                }

                val readyRenderer = checkNotNull(loaded)
                val count = readyRenderer.pageCount
                if (count < 1) throw IllegalStateException("This PDF has no pages.")
                if (closing) {
                    readyRenderer.close()
                    opened?.close()
                    return@execute
                }
                descriptor = opened
                renderer = readyRenderer

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    pageCount = count
                    pageRatios = FloatArray(count) { DEFAULT_PAGE_ASPECT }
                    pageIndex = pageIndex.coerceIn(0, count - 1)
                    // Set the render window before attaching the adapter. This also covers
                    // restoring a saved position deep inside a long PDF.
                    visibleStartPage = pageIndex
                    visibleEndPage = minOf(count - 1, pageIndex + 5)
                    pageAdapter = PdfPageAdapter()
                    recycler?.adapter = pageAdapter
                    updateRecyclerWidth()
                    progress?.visibility = View.GONE
                    status?.text = count.toString() + " pages · Offline · Read only"
                    recycler?.post { jumpToPage(pageIndex, false) }
                }
            } catch (exception: Exception) {
                if (opened != null && descriptor !== opened) {
                    try { opened.close() } catch (_: Exception) { }
                }
                if (loaded != null && renderer !== loaded) {
                    try { loaded.close() } catch (_: Exception) { }
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

    private inner class PdfPageAdapter :
        RecyclerView.Adapter<PdfPageAdapter.PageHolder>() {

        fun refreshForZoom() {
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }

        inner class PageHolder(
            itemView: View,
            val image: ImageView,
            val loading: TextView,
            val caption: TextView
        ) : RecyclerView.ViewHolder(itemView)

        init { setHasStableIds(true) }

        override fun getItemCount(): Int = pageCount
        override fun getItemId(position: Int): Long = position.toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val item = FrameLayout(this@PdfViewerActivity).apply { setBackgroundColor(CANVAS) }
            val image = ImageView(this@PdfViewerActivity).apply {
                setBackgroundColor(Color.WHITE)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "PDF page"
                elevation = dp(2).toFloat()
            }
            val loading = control("Rendering page…", 12, MUTED).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(Color.WHITE)
            }
            val caption = control("", 10, MUTED).apply { gravity = Gravity.CENTER }
            item.addView(image)
            item.addView(loading)
            item.addView(caption)
            return PageHolder(item, image, loading, caption)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val width = pageWidthPx()
            val ratio = pageRatios.getOrElse(position) { DEFAULT_PAGE_ASPECT }.coerceIn(0.45f, 2.8f)
            val height = (width * ratio).roundToInt().coerceAtLeast(dp(180))
            holder.image.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            holder.loading.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            holder.caption.layoutParams = FrameLayout.LayoutParams(width, dp(25), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = height }
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height + dp(25)
            )
            holder.caption.text = if (bookmarks.contains(position)) {
                "Page " + (position + 1) + "  ★"
            } else {
                "Page " + (position + 1)
            }

            val key = pageCacheKey(position, renderBucket)
            val bitmap = pageCache.get(key) ?: bestCachedPage(position)
            if (bitmap != null && !bitmap.isRecycled) {
                holder.image.setImageBitmap(bitmap)
                holder.loading.visibility = View.GONE
            } else {
                holder.image.setImageDrawable(null)
                holder.loading.visibility = View.VISIBLE
            }
            if (pageCache.get(key) == null) requestPageRender(position, renderBucket)
        }
    }

    private fun requestPageRender(position: Int, bucket: Float) {
        if (closing || position !in 0 until pageCount) return
        val key = pageCacheKey(position, bucket)
        if (pageCache.get(key) != null) return
        synchronized(pendingRenders) {
            if (!pendingRenders.add(key)) return
        }

        worker.execute {
            // Fast flings can leave obsolete pages queued. Skip them before using PdfRenderer.
            if (position < (visibleStartPage - 2).coerceAtLeast(0) ||
                position > (visibleEndPage + 2).coerceAtMost(pageCount - 1)
            ) {
                mainHandler.post {
                    synchronized(pendingRenders) { pendingRenders.remove(key) }
                }
                return@execute
            }

            var bitmap: Bitmap? = null
            var ratio = DEFAULT_PAGE_ASPECT
            var error: Exception? = null
            try {
                val active = renderer ?: throw IOException("PDF renderer is not ready.")
                active.openPage(position).use { page ->
                    ratio = page.height.toFloat() / page.width.toFloat()
                    var scale = renderTargetWidth(bucket) / page.width.toFloat()
                    scale = minOf(
                        scale,
                        MAX_PAGE_DIMENSION / page.width.toFloat(),
                        MAX_PAGE_DIMENSION / page.height.toFloat()
                    )
                    val pixels = page.width.toDouble() * page.height * scale * scale
                    if (pixels > MAX_BITMAP_PIXELS) scale *= sqrt(MAX_BITMAP_PIXELS / pixels).toFloat()
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    output.eraseColor(Color.WHITE)
                    page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = output
                }
            } catch (exception: Exception) {
                error = exception
            }

            val result = bitmap
            mainHandler.post {
                synchronized(pendingRenders) { pendingRenders.remove(key) }
                if (closing || isFinishing || isDestroyed) {
                    if (result != null && !result.isRecycled) result.recycle()
                    return@post
                }
                if (result != null) {
                    pageRatios[position] = ratio.coerceIn(0.45f, 2.8f)
                    pageCache.put(key, result)
                    pageAdapter?.notifyItemChanged(position)
                } else if (position == pageIndex) {
                    status?.text = error?.message ?: "Could not render page " + (position + 1)
                }
            }
        }
    }

    private fun bestCachedPage(position: Int): Bitmap? {
        for (bucket in listOf(3f, 2f, 1.5f, 1f)) {
            val bitmap = pageCache.get(pageCacheKey(position, bucket))
            if (bitmap != null && !bitmap.isRecycled) return bitmap
        }
        return null
    }

    private fun pageCacheKey(position: Int, bucket: Float): String =
        position.toString() + ":" + (bucket * 100).roundToInt()

    private fun pageCacheSizeKb(): Int =
        ((Runtime.getRuntime().maxMemory() / 1024L) / 10L)
            .coerceIn(8L * 1024L, 32L * 1024L).toInt()

    private fun pageWidthPx(): Int {
        val viewport = horizontalScroll?.width?.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        return ((viewport - dp(30)).coerceAtLeast(dp(260)) * zoom).roundToInt()
    }

    private fun renderTargetWidth(bucket: Float): Int {
        val viewport = horizontalScroll?.width?.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        return (((viewport - dp(30)).coerceAtLeast(dp(260)) * bucket).roundToInt()).coerceIn(320, 2400)
    }

    private fun qualityBucket(value: Float): Float = when {
        value <= 1.25f -> 1f
        value <= 2f -> 1.5f
        value <= 3f -> 2f
        else -> 3f
    }

    private fun updateRecyclerWidth() {
        val list = recycler ?: return
        val viewport = horizontalScroll?.width?.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val desired = maxOf(viewport, pageWidthPx() + dp(28))
        val params = list.layoutParams
        if (params.width != desired) {
            params.width = desired
            list.layoutParams = params
        }
    }

    private fun setZoom(
        value: Float,
        focusX: Float? = null,
        focusY: Float? = null
    ) {
        val list = recycler ?: return
        list.scaleX = 1f
        list.scaleY = 1f
        if (pageCount < 1) return

        val next = (value.coerceIn(1f, 4f) * 40f).roundToInt() / 40f
        if (abs(next - zoom) < 0.001f) return

        val manager = list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION } ?: pageIndex
        val firstTop = manager.findViewByPosition(first)?.top ?: dp(12)
        val oldZoom = zoom
        val ratio = next / oldZoom
        val horizontal = horizontalScroll
        val oldScrollX = horizontal?.scrollX ?: 0

        // Focus is in RecyclerView coordinates. Convert it to the viewport coordinate.
        val anchorX = focusX?.minus(oldScrollX.toFloat())
            ?: ((horizontal?.width ?: 0) / 2f)
        val anchorY = focusY ?: (list.height / 2f).toFloat()
        val anchoredTop = (anchorY - (anchorY - firstTop) * ratio).roundToInt()

        zoom = next
        renderBucket = qualityBucket(next)
        horizontalScroll?.horizontalPanEnabled = next > 1.01f
        updateRecyclerWidth()
        // One rebind after the gesture, never one per pinch-motion event.
        pageAdapter?.refreshForZoom()

        list.post {
            manager.scrollToPositionWithOffset(
                first.coerceIn(0, pageCount - 1),
                anchoredTop
            )
            if (horizontal != null) {
                val x = ((oldScrollX + anchorX) * ratio - anchorX)
                    .roundToInt().coerceAtLeast(0)
                horizontal.scrollTo(x, 0)
            }
        }
    }

    private fun updateCurrentPage() {
        val manager = recycler?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val visible = manager.findFirstVisibleItemPosition()
        if (visible != RecyclerView.NO_POSITION && visible in 0 until pageCount) {
            pageIndex = visible
            visibleStartPage = visible
            visibleEndPage = manager.findLastVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }?.coerceAtLeast(visible) ?: visible
            updatePageLabel()
        }
    }

    private fun updatePageLabel() {
        pageLabel?.text = if (pageCount > 0) {
            "Page " + (pageIndex + 1) + " / " + pageCount
        } else {
            "Page — / —"
        }
        bookmarkButton?.text = if (bookmarks.contains(pageIndex)) "★" else "☆"
    }

    private fun jumpToPage(index: Int, animate: Boolean = true) {
        if (pageCount < 1) return
        val target = index.coerceIn(0, pageCount - 1)
        pageIndex = target
        updatePageLabel()
        val list = recycler ?: return
        if (animate) {
            list.smoothScrollToPosition(target)
        } else {
            // A direct jump does not necessarily dispatch onScrolled, so update the
            // render window before RecyclerView binds the destination pages.
            visibleStartPage = target
            visibleEndPage = minOf(pageCount - 1, target + 5)
            val manager = list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
            manager.scrollToPositionWithOffset(target, dp(12))
        }
    }

    private fun showGoToPage() {
        if (pageCount < 1) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Page number (1-" + pageCount + ")"
            setText((pageIndex + 1).toString())
            setSelection(text.length)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(2))
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Go") { _, _ ->
                val requested = input.text.toString().toIntOrNull()
                if (requested == null || requested !in 1..pageCount) {
                    Toast.makeText(this, "Enter a page from 1 to " + pageCount + ".", Toast.LENGTH_SHORT).show()
                } else jumpToPage(requested - 1)
            }
            .show()
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
                }
                val label = control("", 10, MUTED).apply { gravity = Gravity.CENTER }
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
            if (bitmap != null && !bitmap.isRecycled) holder.image.setImageBitmap(bitmap)
            else {
                holder.image.setImageDrawable(null)
                requestThumbnail(position, holder.image)
            }
            return cell
        }
    }

    private fun showThumbnails() {
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
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).roundToInt(),
            (resources.displayMetrics.heightPixels * 0.78f).roundToInt()
        )
    }

    private fun requestThumbnail(position: Int, target: ImageView) {
        if (closing || position !in 0 until pageCount) return
        val cached = thumbnailCache.get(position)
        if (cached != null && !cached.isRecycled) {
            if (target.tag == position) target.setImageBitmap(cached)
            return
        }
        synchronized(pendingThumbnails) {
            if (!pendingThumbnails.add(position)) return
        }
        worker.execute {
            var bitmap: Bitmap? = null
            try {
                val active = renderer ?: throw IOException("PDF renderer is not ready.")
                active.openPage(position).use { page ->
                    val width = 150
                    val height = (page.height.toFloat() / page.width * width).roundToInt().coerceAtLeast(1)
                    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    output.eraseColor(Color.WHITE)
                    page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = output
                }
            } catch (_: Exception) { }
            val result = bitmap
            mainHandler.post {
                synchronized(pendingThumbnails) { pendingThumbnails.remove(position) }
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

    private fun shareDocument() {
        val uri = documentUri ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
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

    private fun toggleBookmark() {
        if (pageCount < 1) return
        if (!bookmarks.add(pageIndex)) bookmarks.remove(pageIndex)
        saveBookmarks()
        updatePageLabel()
        pageAdapter?.notifyItemChanged(pageIndex)
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

    private fun showError(message: String?) {
        if (isFinishing || isDestroyed) return
        progress?.visibility = View.GONE
        status?.text = "Could not open this PDF"
        AlertDialog.Builder(this)
            .setTitle("PDF could not be opened")
            .setMessage(
                if (message.isNullOrBlank()) {
                    "The file may be damaged, encrypted or unavailable."
                } else message
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toolbarButton(value: String): TextView =
        control(value, 12, ACCENT).apply {
            gravity = Gravity.CENTER
            background = rounded(0xffeef3f0.toInt(), 11)
            isClickable = true
            isFocusable = true
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

    private fun rounded(color: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
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
        outState.putInt("pdf_page", pageIndex)
        outState.putFloat("pdf_zoom", zoom)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        closing = true
        recycler?.adapter = null
        pageCache.evictAll()
        thumbnailCache.evictAll()
        worker.execute {
            try { renderer?.close() } catch (_: Exception) { }
            try { descriptor?.close() } catch (_: Exception) { }
            try { cachedPdf?.delete() } catch (_: Exception) { }
            renderer = null
            descriptor = null
            cachedPdf = null
        }
        worker.shutdown()
        super.onDestroy()
    }
}
