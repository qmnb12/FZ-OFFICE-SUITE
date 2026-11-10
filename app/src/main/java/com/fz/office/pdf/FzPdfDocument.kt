package com.fz.office.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FZ Office's Kotlin-owned PDF document adapter over Android's platform PdfRenderer.
 *
 * This class owns descriptor lifetime, seekability fallback, page access and
 * bounded bitmap rendering. It does not depend on a bundled PDF reader library.
 * All calls touching PdfRenderer are synchronized because the platform renderer
 * permits only one open page at a time.
 */
class FzPdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val cachedCopy: File?
) : Closeable {

    data class PageSize(val width: Int, val height: Int)
    data class TextMatch(val bounds: List<RectF>)

    private var closed = false

    val pageCount: Int
        @Synchronized get() {
            checkOpen()
            return renderer.pageCount
        }

    @Synchronized
    fun pageSize(index: Int): PageSize {
        checkOpen()
        require(index in 0 until renderer.pageCount) { "PDF page index is out of range." }
        renderer.openPage(index).use { page ->
            return PageSize(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
        }
    }

    /**
     * Search the page's embedded text using Android 15's platform PDF search.
     * This operation can be expensive and must be called from a worker thread.
     */
    @Suppress("NewApi")
    @Synchronized
    fun searchPage(index: Int, query: String): List<TextMatch> {
        checkOpen()
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        check(android.os.Build.VERSION.SDK_INT >= 35) {
            "PDF text search requires Android 15 or later."
        }
        require(index in 0 until renderer.pageCount) { "PDF page index is out of range." }

        renderer.openPage(index).use { page ->
            return page.searchText(normalized).map { match ->
                TextMatch(match.bounds.map { RectF(it) })
            }
        }
    }

    /**
     * Render one page at the requested pixel width, capped by both dimension
     * and total-pixel limits so unusually tall pages cannot exhaust app memory.
     */
    @Synchronized
    fun renderPage(index: Int, targetWidthPx: Int): Bitmap {
        checkOpen()
        require(index in 0 until renderer.pageCount) { "PDF page index is out of range." }
        require(targetWidthPx > 0) { "Render width must be positive." }

        renderer.openPage(index).use { page ->
            var scale = targetWidthPx.coerceIn(
                MIN_RENDER_WIDTH_PX,
                MAX_RENDER_DIMENSION_PX
            ).toDouble() / page.width.toDouble()
            val largestPageDimension = maxOf(page.width, page.height).toDouble()
            scale = minOf(
                scale,
                MAX_RENDER_DIMENSION_PX.toDouble() / largestPageDimension
            )
            val pagePixels = page.width.toDouble() * page.height.toDouble()
            scale = minOf(scale, sqrt(MAX_BITMAP_PIXELS.toDouble() / pagePixels))

            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            return try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (error: Exception) {
                bitmap.recycle()
                throw error
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            renderer.close()
        } finally {
            try {
                descriptor.close()
            } finally {
                cachedCopy?.delete()
            }
        }
    }

    private fun checkOpen() {
        check(!closed) { "PDF document is already closed." }
    }

    companion object {
        const val DEFAULT_MAX_PDF_BYTES = 512L * 1024L * 1024L
        private const val MIN_RENDER_WIDTH_PX = 256
        private const val MAX_RENDER_DIMENSION_PX = 4096
        private const val MAX_BITMAP_PIXELS = 6_000_000L
        private const val COPY_BUFFER_BYTES = 32 * 1024

        /**
         * Opens a SAF Uri directly when the provider supplies a seekable file
         * descriptor. Providers that expose pipes/non-seekable descriptors fall
         * back to a size-bounded private cache copy.
         */
        @Throws(IOException::class)
        fun open(
            context: Context,
            uri: Uri,
            maxPdfBytes: Long = DEFAULT_MAX_PDF_BYTES
        ): FzPdfDocument {
            require(maxPdfBytes > 0L) { "PDF size limit must be positive." }

            var directFailure: Exception? = null
            val directDescriptor = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (error: Exception) {
                directFailure = error
                null
            }

            if (directDescriptor != null) {
                val reportedSize = directDescriptor.statSize
                if (reportedSize > maxPdfBytes) {
                    directDescriptor.close()
                    throw IOException(
                        "PDF is larger than " + (maxPdfBytes / (1024L * 1024L)) + " MB safety limit."
                    )
                }
                try {
                    return create(directDescriptor, null)
                } catch (error: Exception) {
                    directFailure = error
                    runCatching { directDescriptor.close() }
                }
            }

            val cachedFile = copyToPrivateCache(context, uri, maxPdfBytes)
            val cachedDescriptor = try {
                ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (error: Exception) {
                cachedFile.delete()
                throw IOException("Could not open the cached PDF.", error)
            }

            return try {
                create(cachedDescriptor, cachedFile)
            } catch (error: Exception) {
                runCatching { cachedDescriptor.close() }
                cachedFile.delete()
                throw IOException(
                    "The selected file is not a valid or readable PDF.",
                    directFailure ?: error
                )
            }
        }

        private fun create(
            descriptor: ParcelFileDescriptor,
            cachedCopy: File?
        ): FzPdfDocument {
            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount < 1) {
                renderer.close()
                throw IOException("The PDF does not contain any pages.")
            }
            return FzPdfDocument(descriptor, renderer, cachedCopy)
        }

        @Throws(IOException::class)
        private fun copyToPrivateCache(context: Context, uri: Uri, maxBytes: Long): File {
            val target = File.createTempFile("fz-office-pdf-", ".pdf", context.cacheDir)
            var totalBytes = 0L
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected PDF cannot be read.")
                input.use { source ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            totalBytes += count.toLong()
                            if (totalBytes > maxBytes) {
                                throw IOException(
                                    "PDF is larger than " + (maxBytes / (1024L * 1024L)) + " MB safety limit."
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                if (totalBytes == 0L) throw IOException("The selected PDF is empty.")
                return target
            } catch (error: Exception) {
                target.delete()
                if (error is IOException) throw error
                throw IOException("Could not prepare the selected PDF.", error)
            }
        }
    }
}
