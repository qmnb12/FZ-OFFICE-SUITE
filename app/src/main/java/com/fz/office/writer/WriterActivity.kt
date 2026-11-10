package com.fz.office.writer

import android.app.Activity
import android.app.AlertDialog
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.text.InputType
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import com.fz.office.fonts.FontManager
import com.fz.office.viewer.ReaderDashboard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Read-only document viewer.
 *
 * Legacy conversion is performed in the WebView presentation layer only.
 * No document write stream or Save action is exposed by this activity.
 */
class WriterActivity : Activity() {
    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
        private const val MAX_TEXT_BYTES = 8 * 1024 * 1024
        private const val MAX_DOCX_BYTES = 80 * 1024 * 1024
    }

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var sourceUri: Uri? = null
    private var documentName = "Document"
    private var plainText = false
    private var pageReady = false
    private var viewerInitialized = false
    private var documentLoaded = false
    private var documentFontsLoading = false
    private var documentFontsResolved = false

    private var pendingHtml = "<p></p>"
    private var pendingLegacyProfile = ""
    private var pendingLegacyFontName = ""
    private var pendingLegacyTextHint = false
    private var pendingLegacyFontLoaded = false
    private var pendingDocumentFonts: List<String> = emptyList()
    private var pendingPageLayout: DocxCodec.PageLayout? = null

    private var viewer: WebView? = null
    private var titleView: TextView? = null
    private var statusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriValue = intent.getStringExtra(EXTRA_DOCUMENT_URI)
        if (!uriValue.isNullOrEmpty()) {
            sourceUri = Uri.parse(uriValue)
            documentName = queryDisplayName(checkNotNull(sourceUri))
        }
        val sourceMime = sourceUri?.let { contentResolver.getType(it) }
        plainText = documentName.lowercase(Locale.ROOT).endsWith(".txt") ||
            "text/plain".equals(sourceMime, ignoreCase = true)
        buildViewerScreen()
        loadDocument()
    }

    private fun buildViewerScreen() {
        window.statusBarColor = 0xff151d1e.toInt()
        window.navigationBarColor = 0xff151d1e.toInt()

        val shell = ReaderDashboard(
            this,
            documentName,
            if (plainText) "TEXT · VIEW ONLY" else "WORD · VIEW ONLY"
        ) { finish() }
        titleView = shell.titleView
        statusView = shell.status
        shell.addToolbarAction("Find", "Find text in document") { showSearch() }

        val documentWebView = WebView(this).apply {
            setBackgroundColor(0xFFF1F4F2.toInt())
        }
        viewer = documentWebView
        val settings: WebSettings = documentWebView.settings
        settings.javaScriptEnabled = true
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.domStorageEnabled = false
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.textZoom = 100

        documentWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                initializeViewer()
            }
        }

        // Native WebView pinch and one-finger document scrolling are the only navigation controls.
        shell.setDocumentView(documentWebView)
        setContentView(shell.root)
        applySafeAreaInsets(shell.root)
        documentWebView.loadUrl("file:///android_asset/writer/viewer.html")
    }

    private fun showSearch() {
        val query = EditText(this).apply {
            isSingleLine = true
            hint = "Find text in this document"
        }
        AlertDialog.Builder(this)
            .setTitle("Find in document")
            .setView(query)
            .setNegativeButton("Close", null)
            .setPositiveButton("Find") { _, _ ->
                val value = query.text.toString().trim()
                if (value.isNotEmpty()) viewer?.findAllAsync(value)
            }
            .setNeutralButton("Clear") { _, _ -> viewer?.clearMatches() }
            .show()
    }

    private fun showGoToPage() {
        val web = viewer ?: return
        web.evaluateJavascript("FZViewer.pageCount()") { raw ->
            val count = raw?.trim()?.trim('"')?.toIntOrNull() ?: 0
            if (count < 1) {
                Toast.makeText(this, "Pages are still being prepared.", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "Page number (1-$count)"
                setText("1")
                setSelection(text.length)
            }
            AlertDialog.Builder(this)
                .setTitle("Go to page")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go") { _, _ ->
                    val requested = input.text.toString().toIntOrNull()
                    if (requested == null || requested !in 1..count) {
                        Toast.makeText(this, "Enter a page from 1 to $count.", Toast.LENGTH_SHORT).show()
                    } else {
                        runViewerScript("FZViewer.goToPage(" + (requested - 1) + ")")
                    }
                }
                .show()
        }
    }

    private fun loadDocument() {
        val uri = sourceUri
        if (uri == null) {
            showOpenError("No document was selected.")
            return
        }
        statusView?.text = "Opening document…"
        ioExecutor.execute {
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not read the selected document.")
                val data = input.use {
                    readLimited(it, if (plainText) MAX_TEXT_BYTES else MAX_DOCX_BYTES)
                }

                var html: String
                var legacyProfile = ""
                var legacyFontName = ""
                var legacyTextHint = false
                var pageLayout: DocxCodec.PageLayout? = null
                val documentFonts = ArrayList<String>()

                if (plainText) {
                    var text = String(data, StandardCharsets.UTF_8)
                    if (text.startsWith("\uFEFF")) text = text.substring(1)
                    html = DocxCodec.plainTextToHtml(text)
                } else if (documentName.lowercase(Locale.ROOT).endsWith(".docx")) {
                    val loaded = DocxCodec.readDocx(ByteArrayInputStream(data))
                    html = loaded.html
                    legacyProfile = loaded.legacyProfile
                    legacyFontName = loaded.legacyFontName
                    legacyTextHint = DocxCodec.looksLikeLegacyHindi(html)
                    pageLayout = loaded.pageLayout
                    documentFonts.addAll(loaded.fontNames)
                } else {
                    throw IOException("This viewer currently supports DOCX and TXT files.")
                }

                // Large legacy font archives are handled after first paint, on the IO thread.
                val loadedHtml = html
                val loadedProfile = legacyProfile
                val loadedFontName = legacyFontName
                val loadedHint = legacyTextHint
                val loadedPageLayout = pageLayout
                val loadedFonts = ArrayList(documentFonts)

                runOnUiThread {
                    pendingHtml = loadedHtml
                    documentLoaded = true
                    pendingLegacyProfile = loadedProfile
                    pendingLegacyFontName = loadedFontName
                    pendingLegacyTextHint = loadedHint
                    pendingPageLayout = loadedPageLayout
                    pendingDocumentFonts = loadedFonts
                    titleView?.text = documentName
                    updateStatus()
                    applyPendingDocument()
                }
            } catch (exception: Exception) {
                val message = exception.message
                    ?: "The file may be damaged or use unsupported document features."
                runOnUiThread { showOpenError(message) }
            }
        }
    }

    private fun initializeViewer() {
        val web = viewer
        if (!pageReady || web == null || viewerInitialized) return
        viewerInitialized = true
        try {
            val css = FontManager.getBundledCss()
            val family = FontManager.getDefaultFont(this)
            val script = "FZViewer.registerFonts(" + JSONObject.quote(css) + ");" +
                "FZViewer.setDefaultFont(" + JSONObject.quote(family) + ");"
            web.evaluateJavascript(script, null)
            // Custom font scans and Base64 encoding stay off the first-paint UI path.
            applyPendingDocument()
        } catch (_: Exception) {
            statusView?.text = "Some fonts could not be registered · View only"
            applyPendingDocument()
        }
    }

    private fun registerDocumentFontsAsync() {
        if (!pageReady || viewer == null || !documentLoaded ||
            documentFontsLoading || documentFontsResolved
        ) return
        documentFontsLoading = true

        val requested = ArrayList(pendingDocumentFonts)
        val defaultFamily = FontManager.getDefaultFont(this)
        if (defaultFamily.isNotBlank()) requested.add(defaultFamily)
        val legacyName = pendingLegacyFontName
        if (legacyName.isNotBlank()) requested.add(legacyName)

        ioExecutor.execute {
            var customCss = ""
            var legacyLoaded = false
            try {
                if (legacyName.isNotBlank()) {
                    FontManager.importBundledFontArchives(this@WriterActivity)
                }
                customCss = FontManager.getCustomFontCssForFamilies(
                    this@WriterActivity, requested
                )
                if (legacyName.isNotBlank()) {
                    val declaration = "font-family:" + JSONObject.quote(legacyName) + ";"
                    legacyLoaded = customCss.contains(declaration)
                }
            } catch (_: Exception) {
            }

            val loadedCss = customCss
            val loadedLegacy = legacyLoaded
            runOnUiThread {
                documentFontsLoading = false
                documentFontsResolved = true
                pendingLegacyFontLoaded = loadedLegacy
                val web = viewer
                if (isFinishing || web == null) return@runOnUiThread
                if (loadedCss.isNotEmpty()) {
                    web.evaluateJavascript(
                        "FZViewer.registerFonts(" + JSONObject.quote(loadedCss) + ");",
                        null
                    )
                }
                // Reapply once after fonts are registered to use the document's exact font.
                applyPendingDocument()
                updateStatus()
            }
        }
    }

    private fun applyPendingDocument() {
        val web = viewer
        if (!pageReady || web == null || !documentLoaded) return
        registerDocumentFontsAsync()
        updateStatus()

        val pageLayout = JSONObject()
        try {
            pendingPageLayout?.let { layout ->
                pageLayout.put("widthTwips", layout.widthTwips)
                pageLayout.put("heightTwips", layout.heightTwips)
                pageLayout.put("topTwips", layout.topTwips)
                pageLayout.put("rightTwips", layout.rightTwips)
                pageLayout.put("bottomTwips", layout.bottomTwips)
                pageLayout.put("leftTwips", layout.leftTwips)
            }
        } catch (_: Exception) {
        }

        val script = "FZViewer.setDocument(" +
            JSONObject.quote(pendingHtml) + "," +
            JSONObject.quote(pendingLegacyProfile) + "," +
            JSONObject.quote(pendingLegacyFontName) + "," +
            pendingLegacyTextHint + "," +
            pendingLegacyFontLoaded + "," + pageLayout.toString() + ");"
        web.evaluateJavascript(script, null)
    }

    private fun updateStatus() {
        val status = statusView ?: return
        status.text = when {
            documentFontsLoading && pendingLegacyFontName.isNotEmpty() ->
                "Loading matching font: $pendingLegacyFontName"
            pendingLegacyFontLoaded && pendingLegacyFontName.isNotEmpty() ->
                "Matching font loaded: $pendingLegacyFontName · Original DOCX text preserved"
            pendingLegacyFontName.isNotEmpty() ->
                "Missing font: $pendingLegacyFontName · Import it in Hindi Font Center"
            pendingLegacyTextHint ->
                "Possible legacy Hindi text · No verified font name"
            plainText ->
                "UTF-8 text · View only · Original file unchanged"
            else ->
                "DOCX viewer · Original file is never changed"
        }
    }

    private fun runViewerScript(script: String) {
        if (!pageReady) return
        viewer?.evaluateJavascript(script, null)
    }

    private fun showOpenError(message: String) {
        statusView?.text = "Could not open document"
        AlertDialog.Builder(this)
            .setTitle("Document could not be opened")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    private fun control(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            includeFontPadding = true
        }

    private fun queryDisplayName(uri: Uri): String {
        var name: String? = null
        val cursor: Cursor? = contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment ?: "Document"
        return name ?: "Document"
    }

    @Throws(IOException::class)
    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) {
                throw IOException("This file is larger than the supported limit.")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        viewer?.let {
            it.stopLoading()
            it.destroy()
        }
        viewer = null
        ioExecutor.shutdown()
        super.onDestroy()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().let {
            kotlin.math.round(value * resources.displayMetrics.density).toInt()
        }
}
