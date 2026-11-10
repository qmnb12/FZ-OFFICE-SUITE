package com.fz.office

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.fz.office.fonts.FontCenterActivity
import com.fz.office.library.RecentDocumentsStore
import com.fz.office.pdf.PdfViewerActivity
import com.fz.office.viewer.OfficeViewerActivity
import com.fz.office.writer.WriterActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_OPEN_DOCUMENT = 101
        private val BG = Color.rgb(15, 20, 22)
        private val SURFACE = Color.rgb(29, 37, 39)
        private val TEXT = Color.WHITE
        private val MUTED = Color.rgb(169, 181, 181)
        private val ACCENT = Color.rgb(49, 190, 174)
        private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        private const val MIME_PDF = "application/pdf"
    }

    private var libraryList: LinearLayout? = null
    private var librarySearch: EditText? = null
    private var showStarredOnly = false
    private var libraryQuery = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (openIncomingDocument(intent)) {
            finish()
            return
        }
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        buildHome()
    }

    private fun buildHome() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(28))
        }
        scroll.addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(scroll)
        applySafeAreaInsets(scroll)

        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        page.addView(brandRow, match())
        val mark = label("FZ", 17, BG, true).apply {
            gravity = Gravity.CENTER
            background = round(ACCENT, 14)
        }
        brandRow.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))

        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        val brand = label("FZ WORK SUITE", 17, TEXT, true).apply { letterSpacing = 0.07f }
        brandText.addView(brand, match())
        val subtitle = label("Hindi-first · Offline · Private", 11, MUTED, false).apply {
            setPadding(0, dp(3), 0, 0)
        }
        brandText.addView(subtitle, match())

        val headline = label("All your files. One reader.", 25, TEXT, true).apply {
            setPadding(0, dp(34), 0, dp(7))
        }
        page.addView(headline, match())
        val description = label(
            "Documents को साफ़ पढ़ें — Hindi और English दोनों में.", 13, MUTED, false
        ).apply { setPadding(0, 0, 0, dp(25)) }
        page.addView(description, match())

        val rowOne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        page.addView(rowOne, match())
        addTypeTile(rowOne, "DOCX", "Word", 0xff287bd1.toInt())
        addTypeTile(rowOne, "XLSX", "Excel", 0xff17865a.toInt())

        val rowTwo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rowTwoParams = match().apply { topMargin = dp(11) }
        page.addView(rowTwo, rowTwoParams)
        addTypeTile(rowTwo, "PPTX", "Slides", 0xffd45c3d.toInt())
        addTypeTile(rowTwo, "PDF", "PDF Reader", 0xffb94343.toInt())

        val supported = label("Word · Excel · PowerPoint · PDF · TXT", 11, MUTED, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(18))
        }
        page.addView(supported, match())

        val open = actionButton("Open a document", true)
        page.addView(open, match())
        open.setOnClickListener { openDocumentPicker() }

        val fonts = actionButton("अ   Hindi Font Center", false)
        val fontsParams = match().apply { topMargin = dp(11) }
        page.addView(fonts, fontsParams)
        fonts.setOnClickListener { startActivity(Intent(this, FontCenterActivity::class.java)) }

        addLibrarySection(page)

        val footer = label(
            "Read-only viewer · Original files stay unchanged · No account or ads", 10, MUTED, false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(27), dp(10), 0)
        }
        page.addView(footer, match())
    }

    private fun addLibrarySection(page: LinearLayout) {
        val heading = label("Your Library", 20, TEXT, true).apply {
            setPadding(0, dp(30), 0, dp(5))
        }
        page.addView(heading, match())
        val hint = label(
            "Recent files stay on this device · Star important documents", 11, MUTED, false
        ).apply { setPadding(0, 0, 0, dp(12)) }
        page.addView(hint, match())

        librarySearch = EditText(this).apply {
            isSingleLine = true
            textSize = 14f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            this.hint = "Search recent documents"
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = round(SURFACE, 14)
        }
        page.addView(librarySearch, match())
        librarySearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                libraryQuery = s?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                renderLibrary()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabsParams = match().apply { topMargin = dp(12) }
        page.addView(tabs, tabsParams)
        val recent = libraryTabButton("Recent files", !showStarredOnly)
        val starred = libraryTabButton("Starred files", showStarredOnly)
        tabs.addView(recent, LinearLayout.LayoutParams(0, dp(42), 1f))
        val starredParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(9) }
        tabs.addView(starred, starredParams)
        recent.setOnClickListener {
            showStarredOnly = false
            updateLibraryTabs(tabs)
            renderLibrary()
        }
        starred.setOnClickListener {
            showStarredOnly = true
            updateLibraryTabs(tabs)
            renderLibrary()
        }

        libraryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val listParams = match().apply { topMargin = dp(10) }
        page.addView(libraryList, listParams)
        renderLibrary()
    }

    private fun libraryTabButton(value: String, active: Boolean): TextView =
        label(value, 13, if (active) BG else TEXT, true).apply {
            gravity = Gravity.CENTER
            isSingleLine = true
            includeFontPadding = false
            minWidth = 0
            minHeight = dp(42)
            setPadding(dp(8), 0, dp(8), 0)
            background = round(if (active) ACCENT else SURFACE, 14)
            isClickable = true
            isFocusable = true
            contentDescription = value
        }

    private fun updateLibraryTabs(tabs: LinearLayout) {
        if (tabs.childCount < 2) return
        val recent = tabs.getChildAt(0) as TextView
        val starred = tabs.getChildAt(1) as TextView
        recent.background = round(if (showStarredOnly) SURFACE else ACCENT, 14)
        recent.setTextColor(if (showStarredOnly) TEXT else BG)
        starred.background = round(if (showStarredOnly) ACCENT else SURFACE, 14)
        starred.setTextColor(if (showStarredOnly) BG else TEXT)
    }

    private fun renderLibrary() {
        val list = libraryList ?: return
        list.removeAllViews()
        val entries = RecentDocumentsStore.getAll(this)
        var shown = 0
        for (entry in entries) {
            if (showStarredOnly && !entry.starred) continue
            val searchable = (entry.name + " " + entry.mimeType).lowercase(Locale.ROOT)
            if (libraryQuery.isNotEmpty() && !searchable.contains(libraryQuery)) continue
            addLibraryItem(entry)
            shown++
        }
        if (shown == 0) {
            val message = if (libraryQuery.isEmpty()) {
                if (showStarredOnly) "No starred documents yet. Tap ☆ on a file to keep it here."
                else "Your opened documents will appear here."
            } else {
                "No documents match this search."
            }
            val empty = label(message, 12, MUTED, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(25), dp(18), dp(25))
                background = round(SURFACE, 15)
            }
            list.addView(empty, match())
        }
        if (!showStarredOnly && entries.isNotEmpty()) {
            val clear = label("Clear unstarred history", 11, ACCENT, true).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(13), dp(4), dp(7))
            }
            list.addView(clear, match())
            clear.setOnClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Clear recent history?")
                    .setMessage(
                        "This removes unstarred items from FZ Office history only. Original files will not be deleted."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ ->
                        RecentDocumentsStore.clearUnstarred(this)
                        renderLibrary()
                    }
                    .show()
            }
        }
    }

    private fun addLibraryItem(entry: RecentDocumentsStore.Entry) {
        val list = libraryList ?: return
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(9), dp(11))
            background = round(SURFACE, 15)
        }
        val cardParams = match().apply { bottomMargin = dp(8) }
        list.addView(card, cardParams)

        var extension = extensionOf(entry.name).uppercase(Locale.ROOT)
        if (extension.isEmpty()) extension = "FILE"
        val type = label(extension, 10, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = round(typeColor(extension), 10)
        }
        card.addView(type, LinearLayout.LayoutParams(dp(51), dp(45)))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(5), 0)
        }
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        val name = label(entry.name, 13, TEXT, true).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        details.addView(name, match())
        val opened = label("Opened " + relativeTime(entry.openedAt), 10, MUTED, false).apply {
            setPadding(0, dp(4), 0, 0)
        }
        details.addView(opened, match())

        val star = label(if (entry.starred) "★" else "☆", 21, if (entry.starred) ACCENT else MUTED, true).apply {
            gravity = Gravity.CENTER
        }
        card.addView(star, LinearLayout.LayoutParams(dp(34), dp(40)))
        star.setOnClickListener {
            RecentDocumentsStore.setStarred(this, entry.uri, !entry.starred)
            renderLibrary()
        }

        val share = label("↗", 19, TEXT, true).apply { gravity = Gravity.CENTER }
        card.addView(share, LinearLayout.LayoutParams(dp(34), dp(40)))
        share.setOnClickListener { shareDocument(entry) }

        val remove = label("×", 21, MUTED, false).apply { gravity = Gravity.CENTER }
        card.addView(remove, LinearLayout.LayoutParams(dp(30), dp(40)))
        remove.setOnClickListener {
            RecentDocumentsStore.remove(this, entry.uri)
            renderLibrary()
        }

        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { launchDocument(Uri.parse(entry.uri), entry.name, entry.mimeType) }
    }

    private fun shareDocument(entry: RecentDocumentsStore.Entry) {
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = if (entry.mimeType.isEmpty()) "*/*" else entry.mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(entry.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share document"))
        } catch (_: Exception) {
            Toast.makeText(
                this, "This file cannot be shared right now. Reopen it and try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun typeColor(extension: String): Int = when (extension) {
        "DOCX", "DOC" -> 0xff287bd1.toInt()
        "XLSX", "XLS", "CSV", "ODS" -> 0xff17865a.toInt()
        "PPTX", "PPT", "PPSX" -> 0xffd45c3d.toInt()
        "PDF" -> 0xffb94343.toInt()
        else -> 0xff687d8d.toInt()
    }

    private fun extensionOf(name: String?): String {
        if (name == null) return ""
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "" else name.substring(dot + 1)
    }

    private fun relativeTime(timestamp: Long): String {
        val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        if (minutes < 1) return "just now"
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours hr ago"
        val days = hours / 24
        if (days == 1L) return "yesterday"
        if (days < 30) return "$days days ago"
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
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

    private fun addTypeTile(row: LinearLayout, type: String, name: String, color: Int) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(17), dp(12), dp(17))
            background = round(SURFACE, 19)
        }
        val params = LinearLayout.LayoutParams(0, dp(122), 1f)
        if (row.childCount > 0) params.leftMargin = dp(11)
        row.addView(tile, params)
        val badge = label(type, 17, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = round(color, 13)
        }
        tile.addView(badge, LinearLayout.LayoutParams(dp(70), dp(53)))
        val title = label(name, 13, TEXT, true).apply { setPadding(0, dp(10), 0, 0) }
        tile.addView(title)
        tile.isClickable = true
        tile.isFocusable = true
        tile.setOnClickListener { openDocumentPicker() }
    }

    private fun actionButton(value: String, primary: Boolean): TextView =
        label(value, 14, if (primary) BG else TEXT, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = round(if (primary) ACCENT else SURFACE, 15)
            isClickable = true
            isFocusable = true
        }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                MIME_DOCX,
                MIME_XLSX, "application/vnd.ms-excel",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.oasis.opendocument.spreadsheet",
                "text/csv", "text/comma-separated-values",
                MIME_PPTX, "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.template",
                MIME_PDF, "text/plain"
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_DOCUMENT)
        } catch (_: Exception) {
            Toast.makeText(this, "No compatible file picker was found.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openIncomingDocument(incoming: Intent?): Boolean {
        if (incoming == null || incoming.action != Intent.ACTION_VIEW) return false
        val uri = incoming.data ?: return false
        val name = queryDisplayName(uri)
        val mime = incoming.type
        val incomingFlags = incoming.flags
        if (incomingFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, incomingFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
        }
        if (!isSupportedDocument(name, mime)) {
            Toast.makeText(
                this, "This format is not supported by the FZ read-only viewer.",
                Toast.LENGTH_LONG
            ).show()
            return true
        }
        launchDocument(uri, name, mime)
        return true
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the existing document picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val resultIntent = data ?: return
        val uri = resultIntent.data ?: return
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK) return
        val name = queryDisplayName(uri)
        val mime = contentResolver.getType(uri)
        if (!isSupportedDocument(name, mime)) {
            Toast.makeText(
                this, "Choose DOCX/TXT, supported spreadsheets, presentations or PDF.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val flags = resultIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }
        launchDocument(uri, name, mime)
    }

    private fun isSupportedDocument(name: String?, mime: String?): Boolean =
        isPdfDocument(name, mime) || isOfficeViewerDocument(name, mime) ||
            isFzWriterViewerDocument(name, mime)

    private fun isPdfDocument(name: String?, mime: String?): Boolean {
        val lower = name?.lowercase(Locale.ROOT).orEmpty()
        return lower.endsWith(".pdf") || MIME_PDF.equals(mime, ignoreCase = true)
    }

    private fun isOfficeViewerDocument(name: String?, mime: String?): Boolean {
        val lower = name?.lowercase(Locale.ROOT).orEmpty()
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".xlsm") ||
            lower.endsWith(".xlsb") || lower.endsWith(".xltx") || lower.endsWith(".csv") ||
            lower.endsWith(".ods") || lower.endsWith(".pptx") || lower.endsWith(".ppsx") ||
            lower.endsWith(".pptm") || lower.endsWith(".potx")
        ) return true
        val type = mime?.lowercase(Locale.ROOT) ?: return false
        return type == MIME_XLSX || type == "application/vnd.ms-excel" ||
            type == "application/vnd.ms-excel.sheet.macroenabled.12" ||
            type == "application/vnd.ms-excel.sheet.binary.macroenabled.12" ||
            type == "application/vnd.openxmlformats-officedocument.spreadsheetml.template" ||
            type == "application/vnd.oasis.opendocument.spreadsheet" ||
            type == "text/csv" || type == "text/comma-separated-values" ||
            type == MIME_PPTX ||
            type == "application/vnd.openxmlformats-officedocument.presentationml.slideshow" ||
            type == "application/vnd.ms-powerpoint.presentation.macroenabled.12" ||
            type == "application/vnd.openxmlformats-officedocument.presentationml.template"
    }

    private fun isFzWriterViewerDocument(name: String?, mime: String?): Boolean {
        val lower = name?.lowercase(Locale.ROOT).orEmpty()
        return lower.endsWith(".docx") || lower.endsWith(".txt") ||
            MIME_DOCX.equals(mime, ignoreCase = true) || "text/plain".equals(mime, ignoreCase = true)
    }

    private fun launchDocument(uri: Uri, name: String, mime: String?) {
        try {
            val open = when {
                isPdfDocument(name, mime) -> Intent(this, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_DOCUMENT_URI, uri.toString())
                    putExtra(PdfViewerActivity.EXTRA_DOCUMENT_NAME, name)
                }
                isOfficeViewerDocument(name, mime) -> Intent(this, OfficeViewerActivity::class.java).apply {
                    putExtra(OfficeViewerActivity.EXTRA_DOCUMENT_URI, uri.toString())
                    putExtra(OfficeViewerActivity.EXTRA_DOCUMENT_NAME, name)
                }
                isFzWriterViewerDocument(name, mime) -> Intent(this, WriterActivity::class.java).apply {
                    putExtra(WriterActivity.EXTRA_DOCUMENT_URI, uri.toString())
                }
                else -> {
                    Toast.makeText(
                        this, "This format is not supported by the FZ read-only viewer.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(open)
            RecentDocumentsStore.record(this, uri, name, mime)
        } catch (_: Exception) {
            Toast.makeText(
                this, "Could not open this document. Check that the file is still available.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.let { return it }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "Document"
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            setTypeface(Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL))
        }

    private fun round(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun match() = LinearLayout.LayoutParams(-1, -2)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
