package com.fz.office.pdf

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.os.Bundle
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.fz.office.viewer.DocumentViewerController
import com.fz.office.viewer.DocumentViewerSurface
import com.fz.office.viewer.ReaderDashboard
import com.fz.office.viewer.UriPdfPrintAdapter
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Read-only PDF.js viewer adapted from Gander's pinned offline viewer.
 *
 * PDF.js keeps the original PDF bytes intact and redraws visible pages at the
 * requested zoom. Its worker, renderer and image decoders are bundled locally;
 * documents are streamed from their SAF Uri and are never written back.
 */
class PdfJsViewerActivity : Activity() {

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val ASSET_ORIGIN = "https://appassets.androidplatform.net"
        private const val DOCUMENT_PATH = "/doc/file.pdf"
        private const val RANGE_THRESHOLD_BYTES = 512L * 1024L
        private const val MAX_RANGE_BYTES = 8L * 1024L * 1024L
        private const val CSP =
            "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
                "font-src 'self' data: blob:; media-src 'self' data: blob:; " +
                "connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; " +
                "object-src 'none'; form-action 'none'; base-uri 'none'"
    }

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val assetRevision: String by lazy(LazyThreadSafetyMode.NONE) {
        @Suppress("DEPRECATION")
        runCatching { packageManager.getPackageInfo(packageName, 0).lastUpdateTime.toString() }
            .getOrDefault("0")
    }
    private var documentUri: Uri? = null
    private var documentName = "PDF document"
    @Volatile private var documentLength = -1L
    @Volatile private var useRanges = false
    private var webView: WebView? = null
    private var doubleTapZoom: PdfJsDoubleTapZoom? = null
    private var dashboard: ReaderDashboard? = null
    private var surface: PdfJsSurface? = null
    private var appPort: WebMessagePort? = null
    private var pageReady = false
    private var currentPage = 1
    private var totalPages = 0
    @Volatile private var locked = false
    private var baseScale = 0f
    private var lastQuery = ""
    private var searchIndexReady = false
    private val pendingFindNavigation = ArrayList<String>()
    private val pendingCommands = ArrayList<String>()
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
        currentPage = (state?.getInt("pdf_page") ?: 1).coerceAtLeast(1)

        // Gander's current PDF.js bundle uses modern module and worker features.
        // Keep the native reader as a compatibility fallback for older WebViews.
        if (!supportsBundledPdfJs()) {
            openLegacyReader()
            return
        }
        buildReader()
        prepareDocument()
    }

    private fun supportsBundledPdfJs(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val version = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
            ?: return false
        val major = Regex("^(\\d+)").find(version)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return false
        return major >= 125
    }

    private fun openLegacyReader() {
        val uri = documentUri ?: return
        startActivity(Intent(this, PdfViewerActivity::class.java).apply {
            putExtra(PdfViewerActivity.EXTRA_DOCUMENT_URI, uri.toString())
            putExtra(PdfViewerActivity.EXTRA_DOCUMENT_NAME, documentName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }

    private fun buildReader() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()

        val shell = ReaderDashboard(this, documentName, "PDF · GANDER PDF.JS · READ ONLY") {
            finish()
        }
        dashboard = shell
        shell.setLoading(true)
        shell.setStatus("Preparing PDF viewer…")

        val browser = PdfJsWebView(this).apply {
            setBackgroundColor(0xff48443d.toInt())
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        webView = browser
        val doubleTap = PdfJsDoubleTapZoom(this, browser)
        doubleTapZoom = doubleTap
        browser.setOnTouchListener { _, event ->
            doubleTap.touched(event)
            false
        }

        val settings: WebSettings = browser.settings
        settings.javaScriptEnabled = true
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.blockNetworkLoads = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.textZoom = 100
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.minimumFontSize = 1
        settings.defaultTextEncodingName = "UTF-8"
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        browser.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse {
                val range = request.requestHeaders.entries
                    .firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
                return intercept(request.url, range)
            }

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse =
                intercept(Uri.parse(url), null)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = !isTrustedViewerPage(request.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !isTrustedViewerPage(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                dashboard?.setStatus("Opening PDF…")
                dashboard?.setLoading(true)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (closing) return
                dashboard?.setLoading(false)
                baseScale = view.scale.takeIf { it > 0f } ?: 1f
                (view as? PdfJsWebView)?.setFitScale(baseScale)
                pageReady = true
                connectPageBridge()
                dashboard?.setStatus(
                    if (locked) "PDF protected · Printing unavailable"
                    else if (totalPages > 0) "PDF ready · Page $currentPage of $totalPages"
                    else "PDF ready · Pinch or double-tap to zoom"
                )
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame && !closing) {
                    dashboard?.setLoading(false)
                    dashboard?.setStatus("Could not load the PDF viewer")
                    Toast.makeText(
                        this@PdfJsViewerActivity,
                        "The PDF viewer could not be loaded. Check that the PDF is readable.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        shell.setDocumentView(browser)
        val readerSurface = PdfJsSurface(browser)
        surface = readerSurface
        DocumentViewerController(
            this,
            shell,
            readerSurface,
            documentUri,
            documentName,
            documentUri?.let { contentResolver.getType(it) } ?: "application/pdf"
        )
        setContentView(shell.root)
        applySafeAreaInsets(shell.root)
    }

    private fun prepareDocument() {
        val uri = documentUri ?: return
        ioExecutor.execute {
            val (length, seekable, encrypted) = probeDocument(uri)
            // Password-protected PDFs can trigger extra cross-range reads while PDF.js
            // resolves the encryption dictionary and xref. Serve them as one stream so
            // SAF providers cannot break the password flow with a failed partial read.
            val ranged = seekable && !encrypted && length >= RANGE_THRESHOLD_BYTES
            runOnUiThread {
                if (closing || isFinishing || isDestroyed) return@runOnUiThread
                documentLength = length
                useRanges = ranged
                val resume = currentPage.coerceAtLeast(1)
                val url = "$ASSET_ORIGIN/assets/$assetRevision/viewer/pdf.html" +
                    "?name=" + Uri.encode(documentName) +
                    "&ext=pdf&ranged=" + (if (useRanges) "1" else "0") +
                    "&resume=" + resume
                dashboard?.setStatus("Opening PDF with Gander PDF.js…")
                webView?.loadUrl(url)
            }
        }
    }

    /**
     * Probe a SAF Uri once. Some providers report UNKNOWN_LENGTH on the descriptor
     * while exposing OpenableColumns.SIZE; use range loading only when the descriptor
     * is also genuinely seekable.
     */
    private fun probeDocument(uri: Uri): Triple<Long, Boolean, Boolean> {
        val descriptor = try {
            contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        } ?: return Triple(queryDocumentLength(uri), false, false)

        return descriptor.use { afd ->
            val length = afd.length.takeIf { it > 0L } ?: queryDocumentLength(uri)
            val seekable = if (length >= RANGE_THRESHOLD_BYTES) {
                try {
                    Os.lseek(afd.fileDescriptor, afd.startOffset, OsConstants.SEEK_SET)
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
            val encrypted = if (seekable && length > 0L) {
                runCatching { containsPdfEncryptionMarker(afd, length) }.getOrDefault(false)
            } else {
                false
            }
            Triple(length, seekable, encrypted)
        }
    }

    /**
     * Detect the PDF trailer's /Encrypt entry without reading or retaining document
     * content. The first and last 256 KiB cover the normal encryption dictionary and
     * trailer/xref area, including PDFs whose encryption dictionary is indirect.
     *
     * Positional reads leave the provider descriptor's current position unchanged.
     */
    private fun containsPdfEncryptionMarker(
        afd: android.content.res.AssetFileDescriptor,
        length: Long
    ): Boolean {
        if (length <= 0L) return false
        val scanBytes = minOf(length, 256L * 1024L).toInt()
        if (scanBytes <= 0) return false

        val head = ByteArray(scanBytes)
        val headRead = Os.pread(afd.fileDescriptor, head, 0, head.size, afd.startOffset)
        if (containsEncryptToken(head, headRead)) return true

        val tailOffset = maxOf(0L, length - scanBytes)
        val tail = ByteArray(scanBytes)
        val tailRead = Os.pread(
            afd.fileDescriptor,
            tail,
            0,
            tail.size,
            afd.startOffset + tailOffset
        )
        return containsEncryptToken(tail, tailRead)
    }

    private fun containsEncryptToken(bytes: ByteArray, count: Int): Boolean {
        if (count < 8) return false
        val text = String(bytes, 0, count, Charsets.ISO_8859_1)
        return Regex("/Encrypt(?![A-Za-z])").containsMatchIn(text)
    }

    private fun queryDocumentLength(uri: Uri): Long = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                cursor.getLong(column).takeIf { it > 0L } ?: -1L
            } else {
                -1L
            }
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    private fun intercept(url: Uri, range: String?): WebResourceResponse {
        if (url.scheme != "https" || url.host != ASSET_HOST) return notFound()
        val path = url.path.orEmpty()
        if (path.isEmpty() || path.contains("..")) return notFound()
        if (path == DOCUMENT_PATH) return documentResponse(range)
        if (!path.startsWith("/assets/")) return notFound()

        val versionedPath = path.removePrefix("/assets/")
        val revisionPrefix = "$assetRevision/"
        if (!versionedPath.startsWith(revisionPrefix)) return notFound()
        val relative = versionedPath.removePrefix(revisionPrefix)
        if (!relative.startsWith("viewer/")) return notFound()
        return try {
            val input = assets.open(relative)
            WebResourceResponse(
                assetMime(relative),
                if (isTextAsset(relative)) "utf-8" else null,
                200,
                "OK",
                assetHeaders(),
                input
            )
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun documentResponse(range: String?): WebResourceResponse {
        val uri = documentUri ?: return notFound()
        return try {
            if (!useRanges || documentLength <= 0L || range.isNullOrBlank()) {
                val input = contentResolver.openInputStream(uri) ?: return notFound()
                val headers = commonHeaders()
                if (useRanges) headers["Accept-Ranges"] = "bytes"
                if (documentLength > 0) headers["Content-Length"] = documentLength.toString()
                return WebResourceResponse("application/pdf", null, 200, "OK", headers, input)
            }

            val requested = parseRange(range, documentLength) ?: return rangeNotSatisfiable()
            val (start, end) = requested
            val afd = contentResolver.openAssetFileDescriptor(uri, "r") ?: return notFound()
            val length = end - start + 1L
            if (length <= 0L || length > MAX_RANGE_BYTES) {
                afd.close()
                return rangeNotSatisfiable()
            }
            val stream = FileInputStream(afd.fileDescriptor)
            val absoluteStart = afd.startOffset + start
            try {
                stream.channel.position(absoluteStart)
            } catch (_: Exception) {
                skipFully(stream, absoluteStart)
            }
            var remainingBytes = length
            val limited = object : FilterInputStream(stream) {
                override fun read(): Int {
                    if (remainingBytes <= 0L) return -1
                    val value = super.read()
                    if (value >= 0) remainingBytes--
                    return value
                }

                override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                    if (remainingBytes <= 0L) return -1
                    val allowed = minOf(count.toLong(), remainingBytes).toInt()
                    val read = super.read(buffer, offset, allowed)
                    if (read > 0) remainingBytes -= read.toLong()
                    return read
                }

                override fun close() {
                    try { super.close() } finally { afd.close() }
                }
            }
            val headers = commonHeaders().apply {
                put("Accept-Ranges", "bytes")
                put("Content-Range", "bytes $start-$end/$documentLength")
                put("Content-Length", length.toString())
            }
            WebResourceResponse("application/pdf", null, 206, "Partial Content", headers, limited)
        } catch (_: Exception) {
            notFound()
        }
    }

    private fun parseRange(value: String, total: Long): Pair<Long, Long>? {
        if (total <= 0L) return null
        val match = Regex("^bytes=(\\d+)-(\\d*)$", RegexOption.IGNORE_CASE)
            .find(value.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val requestedEnd = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull()
            ?: (total - 1L)
        if (start < 0L || start >= total || requestedEnd < start) return null
        return start to requestedEnd.coerceAtMost(total - 1L)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("Could not seek to the requested PDF range.")
                remaining -= read
            }
        }
    }

    private fun rangeNotSatisfiable(): WebResourceResponse =
        WebResourceResponse(
            "application/pdf",
            null,
            416,
            "Range Not Satisfiable",
            commonHeaders().apply { put("Content-Range", "bytes */$documentLength") },
            ByteArrayInputStream(ByteArray(0))
        )

    private fun commonHeaders(): MutableMap<String, String> = hashMapOf(
        "X-Content-Type-Options" to "nosniff",
        "Cache-Control" to "no-store",
        "Content-Security-Policy" to CSP
    )

    private fun assetHeaders(): MutableMap<String, String> = hashMapOf(
        "X-Content-Type-Options" to "nosniff",
        "Cache-Control" to "public, max-age=31536000, immutable",
        "Content-Security-Policy" to CSP
    )

    private fun response(
        mime: String,
        encoding: String?,
        code: Int,
        reason: String,
        stream: InputStream
    ): WebResourceResponse =
        WebResourceResponse(mime, encoding, code, reason, commonHeaders(), stream)

    private fun notFound(): WebResourceResponse =
        response(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            ByteArrayInputStream(ByteArray(0))
        )

    private fun assetMime(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "html" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "text/javascript"
        "wasm" -> "application/wasm"
        "bcmap" -> "application/octet-stream"
        "md", "txt", "xml", "json" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun isTextAsset(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in
            setOf("html", "css", "js", "mjs", "md", "txt", "xml", "json")

    private fun isTrustedViewerPage(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return uri.scheme == "https" && uri.host == ASSET_HOST &&
            path.startsWith("/assets/$assetRevision/viewer/") &&
            path.endsWith(".html") && ".." !in path
    }

    private fun connectPageBridge() {
        val browser = webView ?: return
        if (appPort != null || !pageReady || closing) return
        val ports = try { browser.createWebMessageChannel() } catch (_: Exception) { return }
        val mine = ports[1]
        val page = ports[0]
        mine.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
            override fun onMessage(port: WebMessagePort, message: WebMessage) {
                handlePageMessage(message.data.orEmpty())
            }
        })
        appPort = mine
        try {
            browser.postWebMessage(WebMessage("fz-pdf-tools", arrayOf(page)), Uri.parse(ASSET_ORIGIN))
            val queued = pendingCommands.toList()
            pendingCommands.clear()
            queued.forEach(::postCommand)
        } catch (_: Exception) {
            runCatching { mine.close() }
            runCatching { page.close() }
            appPort = null
        }
    }

    private fun handlePageMessage(raw: String) {
        val message = raw.trim()
        if (message.startsWith("shrunk ")) {
            val shrunk = message.substringAfter(' ').trim() == "1"
            runOnUiThread { doubleTapZoom?.leftToPage = shrunk }
            return
        }
        if (message == "locked") {
            locked = true
            runOnUiThread { dashboard?.setStatus("Password-protected PDF · Printing unavailable") }
            return
        }
        if (message.startsWith("page ")) {
            val fields = message.split(Regex("\\s+"))
            val page = fields.getOrNull(1)?.toIntOrNull() ?: return
            val total = fields.getOrNull(2)?.toIntOrNull() ?: return
            runOnUiThread {
                currentPage = page.coerceAtLeast(1)
                totalPages = total.coerceAtLeast(1)
                dashboard?.setStatus("PDF ready · Page $currentPage of $totalPages")
            }
            return
        }
        val fields = message.split(Regex("\\s+"))
        if (fields.size == 3) {
            val active = fields[0].toIntOrNull() ?: return
            val total = fields[1].toIntOrNull() ?: return
            val ready = fields[2].toIntOrNull() == 1
            runOnUiThread {
                if (ready && !searchIndexReady) {
                    searchIndexReady = true
                    val queued = pendingFindNavigation.toList()
                    pendingFindNavigation.clear()
                    queued.forEach(::postCommand)
                }
                dashboard?.setStatus(
                    when {
                        !ready -> "Searching PDF…"
                        total == 0 -> "No matches found"
                        else -> "PDF match $active of $total"
                    }
                )
            }
        }
    }

    private fun queueFindNavigation(command: String) {
        if (searchIndexReady) postCommand(command)
        else pendingFindNavigation.add(command)
    }

    private fun postCommand(command: String) {
        val port = appPort
        if (port == null) {
            pendingCommands.add(command)
            return
        }
        try {
            port.postMessage(WebMessage(command))
        } catch (_: Exception) {
            pendingCommands.add(command)
        }
    }

    private inner class PdfJsSurface(
        override val view: WebView
    ) : DocumentViewerSurface {
        override val supportsFind: Boolean = true
        override val supportsScrollNavigation: Boolean = true
        override val supportsZoom: Boolean = true
        override val supportsPageNavigation: Boolean get() = totalPages > 1
        override val supportsPrint: Boolean get() = !locked

        override fun find(query: String, forward: Boolean) {
            val value = query.trim()
            if (value.isEmpty()) return
            if (value != lastQuery) {
                lastQuery = value
                searchIndexReady = false
                pendingFindNavigation.clear()
                postCommand("q$value")
                // PDF.js builds its full-document text index asynchronously. Do not
                // send Previous until that index is ready: otherwise stepTo() sees
                // zero matches and the later first hit resets the search to page one.
                if (!forward) queueFindNavigation("p")
            } else {
                queueFindNavigation(if (forward) "n" else "p")
            }
        }

        override fun clearFind() {
            lastQuery = ""
            searchIndexReady = false
            pendingFindNavigation.clear()
            pendingCommands.clear()
            postCommand("c")
        }

        override fun scrollBackward() { view.pageUp(true) }
        override fun scrollForward() { view.pageDown(true) }
        override fun scrollToStart() {
            view.evaluateJavascript("window.scrollTo(0,0);", null)
        }
        override fun scrollToEnd() {
            view.evaluateJavascript(
                "window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));",
                null
            )
        }
        override fun zoomIn() {
            view.evaluateJavascript("window.vwPdfShrink ? window.vwPdfShrink() : 1") { raw ->
                val shrink = raw?.trim('"')?.toFloatOrNull() ?: 1f
                if (shrink < 0.999f) {
                    val next = (shrink * 1.2f).coerceAtMost(1f)
                    view.evaluateJavascript(
                        "window.vwPdfShrink($next, window.innerHeight / 2);",
                        null
                    )
                } else {
                    view.zoomIn()
                }
            }
        }

        override fun zoomOut() {
            val start = baseScale
            val current = view.scale
            if (start > 0f && current > start * 1.02f) {
                view.zoomOut()
                return
            }
            view.evaluateJavascript("window.vwPdfShrink ? window.vwPdfShrink() : 1") { raw ->
                val shrink = raw?.trim('"')?.toFloatOrNull() ?: 1f
                val next = (shrink / 1.2f).coerceAtLeast(0.2f)
                view.evaluateJavascript(
                    "window.vwPdfShrink($next, window.innerHeight / 2);",
                    null
                )
            }
        }

        override fun fitToWidth() {
            val targetScale = baseScale
            view.evaluateJavascript(
                "window.vwPdfShrink && window.vwPdfShrink(1, window.innerHeight / 2);"
            ) {
                // Wait until PDF.js has committed the fit-width layout before changing
                // WebView's native zoom. Running both scale systems in parallel can
                // produce a second correction against a stale view.scale.
                if (closing || targetScale <= 0f) return@evaluateJavascript
                view.post {
                    if (closing) return@post
                    val current = view.scale
                    if (current > 0f) {
                        val factor = targetScale / current
                        if (factor.isFinite() && factor > 0f && kotlin.math.abs(factor - 1f) > 0.001f) {
                            view.zoomBy(factor)
                        }
                    }
                }
            }
        }
        override fun pageCount(onResult: (Int?) -> Unit) {
            onResult(totalPages.takeIf { it > 0 })
        }
        override fun goToPage(index: Int) {
            if (totalPages > 0) postCommand("g" + (index.coerceIn(0, totalPages - 1) + 1))
        }
        override fun createPrintAdapter(jobName: String) =
            documentUri?.let { UriPdfPrintAdapter(this@PdfJsViewerActivity, it, jobName) }

        override fun dispose() {
            runCatching { appPort?.close() }
            appPort = null
            pendingCommands.clear()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pdf_page", currentPage.coerceAtLeast(1))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        closing = true
        surface?.dispose()
        surface = null
        doubleTapZoom?.cancel()
        doubleTapZoom = null
        runCatching { webView?.stopLoading() }
        webView?.apply {
            webChromeClient = null
            webViewClient = WebViewClient()
            removeAllViews()
            destroy()
        }
        webView = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
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
}


/** Minimal Gander-style double-tap zoom anchored under the tapped position. */
private interface PdfJsZoomSurface {
    fun viewWidth(): Int
    fun viewHeight(): Int
    fun scrolledX(): Int
    fun scrolledY(): Int
    fun rangeX(): Int
    fun rangeY(): Int
    fun pageScale(): Float
    fun fitScale(): Float
    fun applyZoom(factor: Float)
    fun scrollDocumentTo(x: Int, y: Int)
}

private class PdfJsDoubleTapZoom(
    context: Activity,
    private val surface: PdfJsZoomSurface
) {
    companion object {
        private const val TARGET_ZOOM = 2f
        private const val DURATION_MS = 250L
        private const val UNDER_FIT = 0.9f
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var still = false
    private var multiTouchStream = false
    var leftToPage: Boolean = false

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTapEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        still = !event.isFromSource(InputDevice.SOURCE_MOUSE)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                            still = false
                        }
                    }
                    MotionEvent.ACTION_UP -> if (still) zoomAbout(event.x, event.y)
                }
                return false
            }
        }
    )

    fun touched(event: MotionEvent) {
        val action = event.actionMasked
        val isMultiTouch = event.pointerCount > 1 ||
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_POINTER_UP

        if (isMultiTouch) {
            if (!multiTouchStream) cancelDetectorSequence(event)
            multiTouchStream = true
            return
        }

        // Once a second finger has participated, do not let GestureDetector reinterpret
        // the end of the pinch as a tap/double-tap. Resume only on the next fresh gesture.
        if (multiTouchStream) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                multiTouchStream = false
            }
            return
        }

        if (action == MotionEvent.ACTION_DOWN) cancel()
        detector.onTouchEvent(event)
    }

    private fun cancelDetectorSequence(source: MotionEvent) {
        cancel()
        val cancelEvent = MotionEvent.obtain(source)
        try {
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            detector.onTouchEvent(cancelEvent)
        } finally {
            cancelEvent.recycle()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    private fun currentZoom(): Float? {
        val current = surface.pageScale()
        val fitted = surface.fitScale()
        if (!current.isFinite() || !fitted.isFinite() || current <= 0f || fitted <= 0f) {
            return null
        }
        return (current / fitted).takeIf { it.isFinite() && it > 0f }
    }

    private fun zoomAbout(x: Float, y: Float) {
        if (leftToPage) return
        val from = currentZoom() ?: return
        val target = if (from < sqrt(TARGET_ZOOM)) TARGET_ZOOM else 1f
        val start = PdfJsZoomStart(surface)
        cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { frame ->
                val fraction = frame.animatedFraction
                val zoom = from * (target / from).pow(fraction)
                step(zoom, start, x, y)
            }
            start()
        }
    }

    private fun step(target: Float, start: PdfJsZoomStart, x: Float, y: Float) {
        val now = currentZoom() ?: return
        val factor = if (target <= 1f && now > 1f) UNDER_FIT / now else target / now
        if (factor != 1f) surface.applyZoom(factor.coerceIn(0.02f, 50f))
        val grown = start.grown() ?: return
        surface.scrollDocumentTo(
            anchored(start.x + x, grown, x, surface.rangeX(), surface.viewWidth()),
            anchored(start.y + y, grown, y, surface.rangeY(), surface.viewHeight())
        )
    }

    private fun anchored(place: Float, grown: Float, at: Float, range: Int, extent: Int): Int =
        (place * grown - at).roundToInt().coerceIn(0, maxOf(0, range - extent))
}

private class PdfJsZoomStart(private val surface: PdfJsZoomSurface) {
    val x = surface.scrolledX().toFloat()
    val y = surface.scrolledY().toFloat()
    private val initialScale = surface.pageScale()
    private val initialWidth = surface.rangeX()
    private val initialHeight = surface.rangeY()

    fun grown(): Float? {
        val currentScale = surface.pageScale()
        if (initialScale.isFinite() && currentScale.isFinite() &&
            initialScale > 0f && currentScale > 0f
        ) {
            return (currentScale / initialScale).takeIf { it.isFinite() && it > 0f }
        }

        // Keep a guarded geometry fallback for WebView builds that report no usable scale.
        val viewHeight = surface.viewHeight()
        val currentHeight = surface.rangeY()
        val currentWidth = surface.rangeX()
        return when {
            initialHeight > viewHeight && currentHeight > viewHeight ->
                currentHeight.toFloat() / initialHeight
            currentWidth > 0 ->
                currentWidth.toFloat() / initialWidth.coerceAtLeast(1)
            else -> null
        }
    }
}


/** Exposes the protected scroll ranges used by the anchored double-tap gesture. */
private class PdfJsWebView(context: Activity) : WebView(context), PdfJsZoomSurface {
    private var initialFitScale = 1f

    fun setFitScale(value: Float) {
        if (value.isFinite() && value > 0f) initialFitScale = value
    }

    override fun viewWidth(): Int = width
    override fun viewHeight(): Int = height
    override fun scrolledX(): Int = scrollX
    override fun scrolledY(): Int = scrollY
    override fun rangeX(): Int = computeHorizontalScrollRange()
    override fun rangeY(): Int = computeVerticalScrollRange()
    override fun pageScale(): Float = scale
    override fun fitScale(): Float = initialFitScale
    override fun applyZoom(factor: Float) { zoomBy(factor) }
    override fun scrollDocumentTo(x: Int, y: Int) { scrollTo(x, y) }
}
