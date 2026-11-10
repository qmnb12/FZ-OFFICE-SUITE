package com.fz.office.viewer

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.fz.office.fonts.FontManager
import com.fz.office.viewer.ReaderDashboard
import com.fz.office.viewer.DocumentViewerController
import com.fz.office.viewer.WebViewDocumentSurface
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.HashMap
import java.util.Locale

class OfficeViewerActivity : Activity() {

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val CSP =
            "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
                "font-src 'self' data: blob:; media-src 'self' data: blob:; " +
                "connect-src 'self'; worker-src 'self'; frame-src 'none'; " +
                "object-src 'none'; form-action 'none'; base-uri 'none'"
    }

    private var documentUri: Uri? = null
    private var documentName = "Office document"
    private var extension = ""
    private var pageName = "xlsx.html"
    private var documentPath = ""
    private var presentation = false
    private var editableXlsx = false
    private var webView: WebView? = null
    private var documentSurface: WebViewDocumentSurface? = null
    private var initialPresentationFitScale: Float? = null
    private var progress: ProgressBar? = null
    private var status: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_DOCUMENT_URI)?.takeIf { it.isNotEmpty() }?.let {
            documentUri = Uri.parse(it)
        }
        val suppliedName = intent.getStringExtra(EXTRA_DOCUMENT_NAME)
        documentName = if (suppliedName.isNullOrBlank()) {
            documentUri?.let { queryDisplayName(it) } ?: "Office document"
        } else {
            suppliedName
        }
        extension = extensionOf(documentName)
        val mime = documentUri?.let { contentResolver.getType(it) }
        if (extension.isEmpty()) {
            val type = mime?.lowercase(Locale.ROOT).orEmpty()
            extension = when {
                type.contains("presentation") -> "pptx"
                type.contains("excel") || type.contains("spreadsheet") -> "xlsx"
                else -> extension
            }
        }
        presentation = isPresentationExtension(extension) || isPresentationMime(mime)
        // Resolve the final extension first: providers sometimes omit the filename suffix
        // while still supplying the XLSX MIME type.
        editableXlsx = !presentation && extension == "xlsx"
        if (documentUri == null ||
            (!presentation && !isSpreadsheetExtension(extension) && !isSpreadsheetMime(mime))
        ) {
            Toast.makeText(this, "This file type is not supported by the reader.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (presentation) {
            pageName = "pptx.html"
            documentPath = "/doc/file.pptx"
        } else {
            pageName = "xlsx.html"
            if (extension.isEmpty()) extension = "xlsx"
            documentPath = "/doc/file." + extension
        }
        buildViewer()
    }

    private fun buildViewer() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()

        val subtitle = when {
            presentation -> "POWERPOINT · VIEW ONLY"
            editableXlsx -> "SPREADSHEET · EDITABLE COPY"
            else -> "SPREADSHEET · VIEW ONLY"
        }
        val shell = ReaderDashboard(this, documentName, subtitle) { finish() }
        progress = shell.progress
        status = shell.status

        if (editableXlsx && !presentation) {
            shell.addToolbarAction("Edit", "Edit a copy of this workbook") {
                val uri = documentUri ?: return@addToolbarAction
                val editorIntent = Intent(this, SpreadsheetEditorActivity::class.java).apply {
                    putExtra(SpreadsheetEditorActivity.EXTRA_DOCUMENT_URI, uri.toString())
                    putExtra(SpreadsheetEditorActivity.EXTRA_DOCUMENT_NAME, documentName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(editorIntent)
            }
        }

        val browser = WebView(this).apply {
            setBackgroundColor(0xffece8df.toInt())
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        webView = browser
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
        settings.minimumFontSize = if (presentation) 8 else 1
        settings.defaultTextEncodingName = "UTF-8"
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        browser.setFindListener { _, numberOfMatches, _ ->
            if (numberOfMatches > 0) {
                status?.text = "Found " + numberOfMatches + " match(es)"
            }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = intercept(request.url)

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                intercept(Uri.parse(url))

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = !isTrustedViewerPage(request.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !isTrustedViewerPage(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                status?.text = "Opening document…"
                progress?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress?.visibility = View.GONE
                status?.text = "Read only · Original file is never changed"
                val family = FontManager.getDefaultFont(this@OfficeViewerActivity)
                val script =
                    "document.documentElement.style.setProperty('--fz-hindi-default'," +
                        JSONObject.quote(family) + ");window.dispatchEvent(new Event('load'));"
                view.evaluateJavascript(script, null)
                documentSurface?.connectFindBridge()
                if (presentation) capturePresentationFitScale(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progress?.visibility = View.GONE
                    status?.text = "Could not load this document"
                    Toast.makeText(
                        this@OfficeViewerActivity,
                        "The viewer page could not be loaded.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Native WebView pinch zoom and finger scrolling; no redundant navigation buttons.
        shell.setDocumentView(browser)
        val surface = WebViewDocumentSurface(
            browser,
            pageCountScript = if (presentation) {
                "document.querySelectorAll('#result .slide').length"
            } else null,
            pageCommand = if (presentation) { page ->
                "(function(){var slides=document.querySelectorAll('#result .slide');" +
                    "var target=slides[" + page + "];" +
                    "if(target)target.scrollIntoView({behavior:'smooth',block:'start',inline:'nearest'});})()"
            } else null,
            zoomInScript = if (!presentation) {
                "(function(){if(typeof window.vwSheetZoom!=='function')return;" +
                    "var z=window.vwSheetZoom();" +
                    "window.vwSheetZoom(z*1.2,window.innerWidth/2,window.innerHeight/2);})()"
            } else null,
            zoomOutScript = if (!presentation) {
                "(function(){if(typeof window.vwSheetZoom!=='function')return;" +
                    "var z=window.vwSheetZoom();" +
                    "window.vwSheetZoom(z/1.2,window.innerWidth/2,window.innerHeight/2);})()"
            } else null,
            fitZoomScript = if (!presentation) {
                "if(typeof window.vwSheetFitWidth==='function')window.vwSheetFitWidth();"
            } else null,
            findBridgeOrigin = Uri.parse("https://" + ASSET_HOST),
            onFindStatus = { active, total, ready ->
                status?.text = when {
                    !ready -> "Searching document…"
                    total <= 0 -> "No matches found"
                    else -> "Match " + active + " of " + total
                }
            },
            fitScaleProvider = if (presentation) {
                { initialPresentationFitScale }
            } else null,
            preparePrintScript = if (!presentation) {
                "(function(){return typeof window.vwSheetPreparePrint==='function' && " +
                    "window.vwSheetPreparePrint()===true;})()"
            } else null,
            printLabel = if (presentation) "Print" else "Print current sheet"
        )
        documentSurface = surface
        DocumentViewerController(
            this, shell, surface, documentUri, documentName,
            documentUri?.let { contentResolver.getType(it) }
        )
        setContentView(shell.root)
        applySafeAreaInsets(shell.root)

        val viewerUrl = "https://" + ASSET_HOST + "/assets/viewer/" + pageName +
            "?name=" + Uri.encode(documentName) +
            "&ext=" + Uri.encode(extension) +
            "&ranged=0"
        browser.loadUrl(viewerUrl)
    }

    private fun capturePresentationFitScale(view: WebView, attempt: Int = 0) {
        if (!presentation || initialPresentationFitScale != null || isFinishing || isDestroyed) return
        view.evaluateJavascript("document.querySelectorAll('#result .slide').length") { raw ->
            val slides = raw?.trim()?.trim('"')?.toIntOrNull() ?: 0
            if (slides > 0) {
                val scale = view.scale
                if (scale > 0f && initialPresentationFitScale == null) initialPresentationFitScale = scale
            } else if (attempt < 80 && !isFinishing && !isDestroyed) {
                view.postDelayed({ capturePresentationFitScale(view, attempt + 1) }, 250L)
            }
        }
    }

    private fun fitViewer() {
        documentSurface?.fitToWidth()
    }

    private fun intercept(uri: Uri?): WebResourceResponse {
        if (uri == null || uri.scheme != "https" || uri.host != ASSET_HOST) return notFound()
        val path = uri.path
        if (path.isNullOrEmpty() || path.contains("..")) return notFound()
        if (documentPath == path) return documentResponse()
        if (!path.startsWith("/assets/")) return notFound()

        var relative = path.substring("/assets/".length)
        if (relative == "viewer/hindi-fonts.css") return hindiFontCss()
        if (relative.startsWith("viewer/fonts/")) {
            relative = "writer/fonts/" + relative.substring("viewer/fonts/".length)
        }
        return try {
            val input = assets.open(relative)
            response(
                assetMime(relative),
                if (isTextAsset(relative)) "utf-8" else null,
                200, "OK", input, true
            )
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun documentResponse(): WebResourceResponse {
        return try {
            val uri = documentUri ?: return notFound()
            val input = contentResolver.openInputStream(uri) ?: return notFound()
            val headers = HashMap<String, String>()
            headers["X-Content-Type-Options"] = "nosniff"
            headers["Cache-Control"] = "no-store"
            var length = -1L
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    if (descriptor != null) length = descriptor.length
                }
            } catch (_: Exception) {
            }
            if (length > 0) headers["Content-Length"] = length.toString()
            WebResourceResponse(documentMime(), null, 200, "OK", headers, input)
        } catch (_: Exception) {
            notFound()
        }
    }

    private fun hindiFontCss(): WebResourceResponse {
        return try {
            val family = FontManager.getDefaultFont(this)
            val css = StringBuilder(FontManager.getBundledCss())
            val selected = FontManager.findByFamily(this, family)
            if (!selected.bundled) css.append(FontManager.getCustomFontCss(selected))
            css.append(":root{--fz-hindi-default:").append(cssString(family)).append(";}")
            response(
                "text/css", "utf-8", 200, "OK",
                ByteArrayInputStream(css.toString().toByteArray(Charsets.UTF_8)), true
            )
        } catch (_: Exception) {
            response(
                "text/css", "utf-8", 200, "OK",
                ByteArrayInputStream(ByteArray(0)), true
            )
        }
    }

    private fun response(
        mime: String,
        encoding: String?,
        code: Int,
        reason: String,
        input: InputStream,
        withPolicy: Boolean
    ): WebResourceResponse {
        val headers = HashMap<String, String>()
        headers["X-Content-Type-Options"] = "nosniff"
        headers["Cache-Control"] = "no-store"
        if (withPolicy) headers["Content-Security-Policy"] = CSP
        return WebResourceResponse(mime, encoding, code, reason, headers, input)
    }

    private fun notFound(): WebResourceResponse =
        response(
            "text/plain", "utf-8", 404, "Not Found",
            ByteArrayInputStream(ByteArray(0)), true
        )

    private fun isTrustedViewerPage(uri: Uri?): Boolean =
        uri != null && uri.scheme == "https" && uri.host == ASSET_HOST &&
            uri.path?.startsWith("/assets/viewer/") == true &&
            uri.path?.endsWith(".html") == true &&
            uri.path?.contains("..") == false

    private fun documentMime(): String {
        if (presentation) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        }
        return when (extension) {
            "csv" -> "text/csv"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "xls", "xlsm", "xlsb" -> "application/vnd.ms-excel"
            else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
    }

    private fun assetMime(path: String): String {
        val lower = path.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".html") -> "text/html"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".js") -> "application/javascript"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".otf") -> "font/otf"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun isTextAsset(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".js") ||
            lower.endsWith(".json") || lower.endsWith(".txt")
    }

    private fun cssString(value: String?): String {
        val safe = value.orEmpty().replace("\\", "\\\\")
            .replace("\"", "\\\"").replace("\n", "").replace("\r", "")
        return "\"" + safe + "\""
    }

    private fun isPresentationExtension(ext: String): Boolean =
        ext == "pptx" || ext == "ppsx" || ext == "pptm" || ext == "potx"

    private fun isSpreadsheetExtension(ext: String): Boolean =
        ext == "xlsx" || ext == "xls" || ext == "xlsm" || ext == "xlsb" ||
            ext == "xltx" || ext == "csv" || ext == "ods"

    private fun isPresentationMime(mime: String?): Boolean {
        val type = mime?.lowercase(Locale.ROOT) ?: return false
        return type.contains("presentation") || type.contains("powerpoint")
    }

    private fun isSpreadsheetMime(mime: String?): Boolean {
        val type = mime?.lowercase(Locale.ROOT) ?: return false
        return type.contains("spreadsheet") || type.contains("excel") || type == "text/csv"
    }

    private fun extensionOf(name: String?): String {
        if (name == null) return ""
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    private fun queryDisplayName(uri: Uri): String {
        try {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.let { return it }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "Office document"
    }

    private fun toolbarControl(value: String): TextView =
        control(value, 12, 0xff31534b.toInt()).apply {
            gravity = Gravity.CENTER
            background = round(0xffeef3f0.toInt(), 12)
        }

    private fun control(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = true
        }

    private fun round(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    @Deprecated("Deprecated in Android API; retained for compatibility")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        webView?.let {
            it.stopLoading()
            it.webViewClient = WebViewClient()
            it.destroy()
        }
        documentSurface?.dispose()
        documentSurface = null
        webView = null
        super.onDestroy()
    }
}
