package com.fz.office.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * A finger-sized scroll thumb laid over a document reader.
 *
 * Android's own WebView scrollbar is a hairline that cannot be grabbed, and the
 * readers keep their scrolling inside a WebView or a RecyclerView. This view
 * draws a 6dp thumb in a 30dp strip on the right edge and drives whichever
 * scroller sits under it with scrollTo, so one gesture works on every reader
 * surface: Gander PDF.js, FZ Writer, XLSX, PPTX and the FZ PDF fallback.
 *
 * Only the strip takes touches; the rest of the reader still scrolls normally
 * because an unhandled gesture falls through to the WebView beneath.
 */
class ReaderScrollBar(
    context: Context,
    private val host: View
) : View(context) {

    private val density = resources.displayMetrics.density
    private val thumbWidth = 6f * density
    private val thumbWidthActive = 8f * density
    private val trackWidth = 3f * density
    private val edgeGap = 13f * density
    private val minThumbHeight = 52f * density
    private val verticalInset = 6f * density
    private val grabSlack = 10f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33101512
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E9E86.toInt()
    }
    private val thumbRect = RectF()

    private var scroller: View? = null
    private var range = 0
    private var extent = 0
    private var offset = 0
    private var dragging = false
    private var grabOffset = 0f
    private var observed: ViewTreeObserver? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { sync() }
    private val refresh = object : Runnable {
        override fun run() {
            sync()
            if (isAttachedToWindow) postDelayed(this, 350L)
        }
    }

    init {
        contentDescription = "Scroll document"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(refresh)
        postDelayed(refresh, 300L)
        resolveScroller()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refresh)
        observed?.removeOnScrollChangedListener(scrollListener)
        observed = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sync()
    }

    private fun sync() {
        val source = resolveScroller()
        if (source == null) {
            if (range != 0) {
                range = 0
                extent = 0
                offset = 0
                invalidate()
            }
            return
        }
        val nextRange = scrollRange(source)
        val nextExtent = scrollExtent(source)
        val nextOffset = scrollOffset(source)
        if (nextRange == range && nextExtent == extent && nextOffset == offset) return
        range = nextRange
        extent = nextExtent
        offset = nextOffset
        if (isScrollable()) updateThumb()
        invalidate()
    }

    private fun resolveScroller(): View? {
        val cached = scroller
        if (cached != null && cached.isAttachedToWindow && scrolls(cached)) return cached
        val found = findScroller(host)
        scroller = found
        if (found != null && isAttachedToWindow) {
            val observer = viewTreeObserver
            if (observer.isAlive && observed !== observer) {
                observed?.removeOnScrollChangedListener(scrollListener)
                observer.addOnScrollChangedListener(scrollListener)
                observed = observer
            }
        }
        return found
    }

    private fun findScroller(view: View): View? {
        if (view !== this && view.visibility == View.VISIBLE && scrolls(view)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findScroller(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /*
     * View keeps these protected; RecyclerView widens them to public, while WebView
     * leaves them protected and reaches its numbers through reflection. A WebView
     * that refuses the call falls back to its public content height and scrollY, so
     * the bar degrades to an estimate rather than disappearing.
     */
    private fun scrollRange(view: View): Int = when (view) {
        is RecyclerView -> view.computeVerticalScrollRange()
        is WebView -> metric(rangeMethod, view)
            .takeIf { it > 0 }
            ?: (view.contentHeight * view.scale).roundToInt()
        else -> 0
    }

    private fun scrollExtent(view: View): Int = when (view) {
        is RecyclerView -> view.computeVerticalScrollExtent()
        is WebView -> metric(extentMethod, view).takeIf { it > 0 }
            ?: (view.height - view.paddingTop - view.paddingBottom)
        else -> 0
    }

    private fun scrollOffset(view: View): Int = when (view) {
        is RecyclerView -> view.computeVerticalScrollOffset()
        is WebView -> metric(offsetMethod, view).takeIf { it >= 0 }
            ?: view.scrollY
        else -> 0
    }

    private companion object {
        val rangeMethod: Method? = scrollMetric("computeVerticalScrollRange")
        val extentMethod: Method? = scrollMetric("computeVerticalScrollExtent")
        val offsetMethod: Method? = scrollMetric("computeVerticalScrollOffset")

        fun scrollMetric(name: String): Method? = try {
            View::class.java.getDeclaredMethod(name).apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }

        fun metric(method: Method?, view: View): Int = try {
            (method?.invoke(view) as? Int) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun scrolls(view: View): Boolean {
        val available = scrollExtent(view)
        return available > 0 && scrollRange(view) > available + 1
    }

    private fun isScrollable(): Boolean = extent > 0 && range > extent + 1

    private fun trackLength(): Float = (height - verticalInset * 2f).coerceAtLeast(1f)

    private fun thumbLength(): Float {
        if (!isScrollable()) return 0f
        val available = trackLength()
        return (available * extent.toFloat() / range.toFloat())
            .coerceIn(minThumbHeight.coerceAtMost(available), available)
    }

    private fun updateThumb() {
        if (!isScrollable() || width <= 0 || height <= 0) return
        val available = trackLength()
        val length = thumbLength()
        val travel = available - length
        val progress = (offset.toFloat() / (range - extent).toFloat()).coerceIn(0f, 1f)
        val top = verticalInset + travel * progress
        val center = width - edgeGap
        thumbRect.set(center - thumbWidth / 2f, top, center + thumbWidth / 2f, top + length)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isScrollable()) return
        updateThumb()
        val center = width - edgeGap
        val trackRadius = trackWidth / 2f
        canvas.drawRoundRect(
            center - trackRadius,
            verticalInset,
            center + trackRadius,
            height - verticalInset,
            trackRadius,
            trackRadius,
            trackPaint
        )
        val thumbRadius = (if (dragging) thumbWidthActive else thumbWidth) / 2f
        canvas.drawRoundRect(
            center - thumbRadius,
            thumbRect.top,
            center + thumbRadius,
            thumbRect.bottom,
            thumbRadius,
            thumbRadius,
            thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val source = resolveScroller() ?: return false
        sync()
        if (!isScrollable()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                offset = scrollOffset(source)
                updateThumb()
                val onThumb = event.y >= thumbRect.top - grabSlack &&
                    event.y <= thumbRect.bottom + grabSlack
                grabOffset = if (onThumb) event.y - thumbRect.top else thumbRect.height() / 2f
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!onThumb) moveThumbTo(event.y - grabOffset, source)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                moveThumbTo(event.y - grabOffset, source)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return false
    }

    private fun moveThumbTo(thumbTop: Float, source: View) {
        if (!isScrollable()) return
        val available = trackLength()
        val length = thumbLength()
        val lowest = verticalInset + available - length
        val top = thumbTop.coerceIn(verticalInset, lowest.coerceAtLeast(verticalInset))
        val travel = lowest - verticalInset
        val progress = if (travel <= 0f) 0f else ((top - verticalInset) / travel).coerceIn(0f, 1f)
        val target = (progress * (range - extent)).roundToInt()
        // RecyclerView.scrollTo only warns; it scrolls by a delta instead. WebView
        // takes the absolute offset the way its own scrollbar does.
        if (source is RecyclerView) {
            source.stopScroll()
            source.scrollBy(0, target - scrollOffset(source))
        } else {
            source.scrollTo(source.scrollX, target)
        }
        offset = target
        updateThumb()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
