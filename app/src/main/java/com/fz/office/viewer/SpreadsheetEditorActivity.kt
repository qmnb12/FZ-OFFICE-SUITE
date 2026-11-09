package com.fz.office.viewer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

class SpreadsheetEditorActivity : Activity() {
    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        private const val REQUEST_CREATE_COPY = 4102
        private const val MAX_BASE64_CHARS = 42 * 1024 * 1024
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val DOCUMENT_PATH = "/doc/file.xlsx"
        private const val CSP =
            "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
                "font-src 'self' data: blob:; connect-src 'self'; worker-src 'self'; " +
                "object-src 'none'; form-action 'none'; base-uri 'none'"
    }

    private var source: Uri? = null
    private var documentName = "Workbook.xlsx"
    private var pendingBytes: ByteArray? = null
    private var webView: WebView? = null
    private var status: TextView? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        intent.getStringExtra(EXTRA_DOCUMENT_URI)?.takeIf { it.isNotEmpty() }?.let {
            source = Uri.parse(it)
        }
        intent.getStringExtra(EXTRA_DOCUMENT_NAME)?.takeIf { it.isNotBlank() }?.let {
            documentName = it
        }
        if (source == null || !documentName.lowercase(Locale.ROOT).endsWith(".xlsx")) {
            Toast.makeText(
                this,
                "Editing is currently supported for .xlsx workbooks.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        buildUi()
    }

    private fun buildUi() {
        window.statusBarColor = 0xfff4f7f5.toInt()
        window.navigationBarColor = 0xfff4f7f5.toInt()
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xffeef3f0.toInt())
        }

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(5), dp(12), dp(5))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(54)))

        val back = label("‹", 26, 0xff1d5145.toInt(), true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(38), -1))

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, 0, 0)
        }
        toolbar.addView(titleColumn, LinearLayout.LayoutParams(0, -1, 1f))

        val title = label(documentName, 13, 0xff18352d.toInt(), true).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        titleColumn.addView(title)
        val subtitle = label("FZ SHEETS · EDIT A COPY", 9, 0xff60766d.toInt(), true).apply {
            letterSpacing = 0.06f
        }
        titleColumn.addView(subtitle)

        val editorWebView = WebView(this).apply {
            setBackgroundColor(0xffeef3f0.toInt())
        }
        webView = editorWebView
        val settings = editorWebView.settings
        settings.javaScriptEnabled = true
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
        settings.blockNetworkLoads = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.setSupportZoom(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.defaultTextEncodingName = "UTF-8"
        editorWebView.addJavascriptInterface(EditorBridge(), "AndroidBridge")
        editorWebView.webViewClient = object : WebViewClient() {
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
            ): Boolean = !isEditorPage(request.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !isEditorPage(Uri.parse(url))

            override fun onPageFinished(view: WebView, url: String) {
                setStatus("Opening spreadsheet…")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) setStatus("Could not load the spreadsheet editor")
            }
        }

        root.addView(editorWebView, LinearLayout.LayoutParams(-1, 0, 1f))
        val statusView = label("Preparing offline spreadsheet editor…", 10, 0xff60766d.toInt(), false)
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8))
        statusView.setBackgroundColor(Color.WHITE)
        status = statusView
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
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

        val page = "https://$ASSET_HOST/assets/writer/sheet-editor.html?name=" +
            Uri.encode(documentName)
        editorWebView.loadUrl(page)
    }

    private fun intercept(uri: Uri?): WebResourceResponse {
        if (uri == null || uri.scheme != "https" || uri.host != ASSET_HOST) return notFound()
        val path = uri.path ?: return notFound()
        if (path.contains("..")) return notFound()
        if (DOCUMENT_PATH == path) return documentResponse()
        if (!path.startsWith("/assets/")) return notFound()

        val relative = path.removePrefix("/assets/")
        return try {
            val input = assets.open(relative)
            response(assetMime(relative), if (isTextAsset(relative)) "utf-8" else null,
                200, "OK", input)
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun documentResponse(): WebResourceResponse {
        return try {
            val input = contentResolver.openInputStream(checkNotNull(source))
                ?: return notFound()
            val headers = hashMapOf(
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store"
            )
            WebResourceResponse(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                null, 200, "OK", headers, input
            )
        } catch (_: Exception) {
            notFound()
        }
    }

    private fun response(
        mime: String,
        encoding: String?,
        code: Int,
        reason: String,
        input: InputStream
    ): WebResourceResponse {
        val headers = hashMapOf(
            "X-Content-Type-Options" to "nosniff",
            "Cache-Control" to "no-store",
            "Content-Security-Policy" to CSP
        )
        return WebResourceResponse(mime, encoding, code, reason, headers, input)
    }

    private fun notFound(): WebResourceResponse =
        response("text/plain", "utf-8", 404, "Not Found", ByteArrayInputStream(ByteArray(0)))

    private fun isEditorPage(uri: Uri?): Boolean =
        uri != null && uri.scheme == "https" && uri.host == ASSET_HOST &&
            uri.path == "/assets/writer/sheet-editor.html"

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
        return lower.endsWith(".html") || lower.endsWith(".css") ||
            lower.endsWith(".js") || lower.endsWith(".json") || lower.endsWith(".txt")
    }

    private fun setStatus(message: String) {
        runOnUiThread { status?.text = message }
    }

    private fun saveWorkbook(base64: String?) {
        if (base64.isNullOrEmpty() || base64.length > MAX_BASE64_CHARS) {
            setStatus("Workbook is too large to save from this editor")
            return
        }
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
                setStatus("The edited workbook package is invalid")
                return
            }
            pendingBytes = bytes
            runOnUiThread { pickSaveLocation() }
        } catch (_: Exception) {
            setStatus("Could not prepare the edited workbook")
        }
    }

    private fun pickSaveLocation() {
        if (pendingBytes == null) return
        val base = documentName.substring(0, documentName.length - 5)
        val create = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_TITLE, "$base - edited.xlsx")
        }
        try {
            startActivityForResult(create, REQUEST_CREATE_COPY)
        } catch (_: Exception) {
            setStatus("Could not open the Save As picker")
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the existing document picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CREATE_COPY) return
        val target = data?.data
        val bytes = pendingBytes
        if (resultCode != RESULT_OK || target == null || bytes == null) {
            pendingBytes = null
            setStatus("Save cancelled · Original unchanged")
            return
        }

        pendingBytes = null
        setStatus("Saving copy…")
        try {
            val output = contentResolver.openOutputStream(target, "w")
                ?: throw IOException("Could not open the selected save location.")
            output.use {
                it.write(bytes)
                it.flush()
            }
            setStatus("Edited workbook copy saved · Original unchanged")
            Toast.makeText(this, "Edited XLSX copy saved", Toast.LENGTH_LONG).show()
        } catch (exception: Exception) {
            setStatus("Could not save the edited workbook copy")
            Toast.makeText(
                this,
                exception.message ?: "Save failed.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private inner class EditorBridge {
        @JavascriptInterface
        fun saveWorkbook(base64: String?) {
            this@SpreadsheetEditorActivity.saveWorkbook(base64)
        }

        @JavascriptInterface
        fun setStatus(message: String?) {
            this@SpreadsheetEditorActivity.setStatus(message.orEmpty())
        }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        webView?.let {
            it.removeJavascriptInterface("AndroidBridge")
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }
}
