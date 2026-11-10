package com.fz.office.fonts

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Button
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FontCenterActivity : Activity() {

    companion object {
        private const val REQUEST_IMPORT_FONT = 410
        private const val REQUEST_IMPORT_ARCHIVE = 411
        private const val INITIAL_FONT_LIMIT = 36
        private val INK = Color.rgb(26, 50, 42)
        private val MUTED = Color.rgb(101, 119, 109)
        private val PRIMARY = Color.rgb(23, 107, 91)
        private val BACKGROUND = Color.rgb(244, 247, 245)
    }

    private var visibleFontLimit = INITIAL_FONT_LIMIT
    private lateinit var searchInput: EditText
    private var countInfo: TextView? = null
    private lateinit var loadMoreButton: Button
    private val importer: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var fontList: LinearLayout
    private lateinit var previewInput: TextView
    private lateinit var selectedStatus: TextView
    // Keep the scanned library while the user filters/searches; refresh after changes.
    private var fontSnapshot: List<FontManager.FontRecord>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        renderFonts()
        importer.execute {
            val setupMessage = try {
                val legacy = FontManager.importBundledFontArchives(this@FontCenterActivity)
                "Font library ready · " + legacy.imported + " legacy fonts newly added."
            } catch (exception: Exception) {
                "Font import warning: " + (
                    exception.message ?: "Some bundled fonts could not be imported."
                )
            }
            runOnUiThread {
                if (!isFinishing) {
                    fontSnapshot = null
                    renderFonts()
                    selectedStatus.text = setupMessage
                }
            }
        }
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BACKGROUND)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        val brand = text("FZ OFFICE  /  TYPOGRAPHY", 10, PRIMARY, true).apply {
            letterSpacing = 0.12f
        }
        root.addView(brand)

        val title = text("Hindi Font Center", 25, INK, true).apply {
            setPadding(0, dp(7), 0, dp(3))
        }
        root.addView(title)

        val intro = text(
            "12 Unicode fonts के साथ DevLys, Kruti Dev और Mangal legacy fonts पहली बार अपने-आप install होते हैं। आप अतिरिक्त TTF/OTF या ZIP packs भी import कर सकते हैं। " +
                "Fonts केवल इस device के private storage में रहेंगे.",
            13, MUTED, false
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) }
        root.addView(intro)

        val importButton = button("+  Import TTF / OTF", PRIMARY, Color.WHITE)
        val importParams = wrapMatch().apply { topMargin = dp(15) }
        root.addView(importButton, importParams)
        importButton.setOnClickListener { chooseFontFile() }

        val archiveButton = button("+  Import ZIP font pack", 0xFFE3EFE9.toInt(), PRIMARY)
        val archiveParams = wrapMatch().apply { topMargin = dp(8) }
        root.addView(archiveButton, archiveParams)
        archiveButton.setOnClickListener { chooseFontArchive() }

        searchInput = EditText(this).apply {
            isSingleLine = true
            textSize = 14f
            setTextColor(INK)
            hint = "Search all fonts: family, style or file name"
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = roundRect(Color.WHITE, dp(11))
        }
        val searchParams = wrapMatch().apply { topMargin = dp(12) }
        root.addView(searchInput, searchParams)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                visibleFontLimit = INITIAL_FONT_LIMIT
                renderFonts()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        selectedStatus = text("", 12, PRIMARY, true).apply {
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(selectedStatus)

        addHeading(root, "Preview text", "SHAPING CHECK")
        previewInput = text(
            FontManager.HINDI_SAMPLE + "\n" +
                FontManager.SHAPING_SAMPLE + "\n" +
                FontManager.JOINER_SAMPLE + "\n" +
                FontManager.NUMERAL_SAMPLE,
            14, INK, false
        ).apply {
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = roundRect(Color.WHITE, dp(13))
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        root.addView(previewInput, wrapMatch())

        addHeading(root, "Available font library", "UNICODE + LEGACY")
        val bundledInfo = text(
            "Bundled Unicode fonts are open-license. Imported Kruti Dev, DevLys and Mangal files are stored locally; their source ZIPs did not include redistribution licences, so they are not uploaded to the public repository.",
            12, MUTED, false
        ).apply { setPadding(0, 0, 0, dp(10)) }
        root.addView(bundledInfo)

        fontList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(fontList, wrapMatch())
        loadMoreButton = button("Show more fonts", 0xFFE8F0EC.toInt(), PRIMARY)
        val moreParams = wrapMatch().apply { topMargin = dp(6) }
        root.addView(loadMoreButton, moreParams)
        loadMoreButton.setOnClickListener {
            visibleFontLimit += INITIAL_FONT_LIMIT
            renderFonts()
        }

        addHeading(root, "Legacy Hindi fonts", "LOCAL FONT LIBRARY")
        val legacyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundRect(Color.WHITE, dp(13))
        }
        root.addView(legacyCard, wrapMatch())

        legacyCard.addView(
            text(
                "Kruti Dev · DevLys · Chanakya · Shusha · Shree Lipi · Akruti · APS-DV",
                14, INK, true
            )
        )
        val legacyInfo = text(
            "आपके ZIP packs से DevLys 010, Kruti Dev variants और Mangal fonts import किए जा सकते हैं। " +
                "DOCX में matching legacy font मिलने पर viewer उस exact family को local font library से load करेगा; " +
                "font import अपने-आप text encoding को Unicode में convert नहीं करता.",
            12, MUTED, false
        ).apply {
            setPadding(0, dp(7), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        legacyCard.addView(legacyInfo)

        val converterInfo = button("How legacy Hindi fonts render", 0xFFE8F0EC.toInt(), PRIMARY)
        val infoParams = wrapMatch().apply { topMargin = dp(12) }
        legacyCard.addView(converterInfo, infoParams)
        converterInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Legacy Hindi font rendering")
                .setMessage(
                    "Legacy DOCX के लिए सबसे सही तरीका उसी exact font family/version को load करना है। " +
                        "DevLys 010, Kruti Dev और Mangal ZIP packs को Import ZIP font pack से private storage में जोड़ें। " +
                        "Viewer document में declared family को match करके original encoded characters को उसी font से render करेगा। " +
                        "Unicode conversion अलग tool है और document open होने पर automatic नहीं चलेगा."
                )
                .setPositiveButton("OK", null)
                .show()
        }

        val footer = text(
            "Unicode text + Devanagari shaping + font coverage are separate requirements.",
            11, MUTED, false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(footer)
    }

    private fun renderFonts() {
        if (!::fontList.isInitialized) return
        fontList.removeAllViews()
        val current = FontManager.getDefaultFont(this)
        selectedStatus.text = "Default font: " + current + " · Private font library"

        val allFonts = fontSnapshot ?: FontManager.getAvailableFonts(this).also {
            fontSnapshot = it
        }
        val query = searchInput.text.toString().trim().lowercase(Locale.ROOT)
        val filtered = allFonts.filter { record ->
            val haystack = (
                record.name + " " + record.category + " " +
                    record.cssFamily + " " + record.description
            ).lowercase(Locale.ROOT)
            query.isEmpty() || haystack.contains(query)
        }

        val info = countInfo ?: text("", 11, MUTED, false).also {
            val countParams = wrapMatch().apply { bottomMargin = dp(8) }
            fontList.addView(it, countParams)
            countInfo = it
        }
        if (info.parent == null) fontList.addView(info, wrapMatch())

        val bundledCount = FontManager.getBundledFonts().size
        val importedCount = (allFonts.size - bundledCount).coerceAtLeast(0)
        val visibleCount = minOf(filtered.size, visibleFontLimit)
        info.text = "Total " + allFonts.size + " font entries · Showing " + visibleCount +
            " of " + filtered.size + " matches\n" + bundledCount +
            " bundled Unicode families · " + importedCount + " local/imported entries"

        val limit = minOf(filtered.size, visibleFontLimit)
        for (index in 0 until limit) {
            val record = filtered[index]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = roundRect(Color.WHITE, dp(13))
            }
            val cardParams = wrapMatch().apply { bottomMargin = dp(9) }
            fontList.addView(card, cardParams)

            card.addView(text(record.name, 15, INK, true))
            val category = text(record.category, 10, PRIMARY, true).apply {
                setPadding(0, dp(2), 0, dp(7))
            }
            card.addView(category)

            val sample = text(previewSample(record), 16, INK, false).apply {
                typeface = FontManager.loadTypeface(this@FontCenterActivity, record)
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(0, 0, 0, dp(9))
            }
            card.addView(sample)

            val description = text(record.description, 11, MUTED, false).apply {
                setPadding(0, 0, 0, dp(9))
            }
            card.addView(description)

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            card.addView(actions, wrapMatch())

            val isDefault = record.cssFamily == current
            val use = button(
                if (isDefault) "Selected family" else "Use as display font",
                if (isDefault) 0xFFE3EFE9.toInt() else PRIMARY,
                if (isDefault) PRIMARY else Color.WHITE
            )
            actions.addView(use, LinearLayout.LayoutParams(0, dp(42), 1f))
            use.isEnabled = !isDefault
            use.setOnClickListener {
                FontManager.setDefaultFont(this, record)
                selectedStatus.text = "Default display font: " + record.cssFamily
                Toast.makeText(this, record.name + " selected.", Toast.LENGTH_SHORT).show()
                renderFonts()
            }

            if (!record.bundled) {
                actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                val remove = button("Remove", 0xFFF8E9E7.toInt(), 0xFF9D3D32.toInt())
                actions.addView(remove, LinearLayout.LayoutParams(dp(92), dp(42)))
                remove.setOnClickListener { confirmRemove(record) }
            }
        }

        if (::loadMoreButton.isInitialized) {
            val more = filtered.size > visibleFontLimit
            loadMoreButton.visibility = if (more) View.VISIBLE else View.GONE
            loadMoreButton.text = "Show " +
                minOf(INITIAL_FONT_LIMIT, filtered.size - visibleFontLimit) + " more fonts"
        }
    }

    private fun previewSample(record: FontManager.FontRecord): String {
        val family = record.cssFamily.lowercase(Locale.ROOT)
        if (family.contains("devlys")) {
            return "Jheku 'kklu lfpo\nHkkjr jktLFkku\nfoHkkx vkSj izLrko"
        }
        if (family.contains("kruti dev") || family.contains("krutidev")) {
            return "Jheku 'kklu lfpo\nHkkjr jktLFkku\nfoHkkx vkSj izLrko"
        }
        return if (::previewInput.isInitialized) {
            previewInput.text.toString()
        } else {
            FontManager.HINDI_SAMPLE + "\n" + FontManager.SHAPING_SAMPLE
        }
    }

    private fun chooseFontArchive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT_ARCHIVE)
        } catch (_: Exception) {
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseFontFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype")
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FONT)
        } catch (_: Exception) {
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the existing document picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode != REQUEST_IMPORT_FONT && requestCode != REQUEST_IMPORT_ARCHIVE) ||
            resultCode != RESULT_OK
        ) return
        val uri = data?.data ?: return

        val name = queryDisplayName(uri)
        val archive = requestCode == REQUEST_IMPORT_ARCHIVE
        selectedStatus.text = if (archive) "Scanning ZIP font pack " + name + "…" else "Validating " + name + "…"
        importer.execute {
            try {
                if (archive) {
                    val result = FontManager.importFontArchive(this, uri)
                    runOnUiThread {
                        visibleFontLimit = INITIAL_FONT_LIMIT
                        fontSnapshot = null
                        renderFonts()
                        selectedStatus.text = "Imported " + result.imported + " fonts · " +
                            result.duplicates + " duplicates · " + result.invalid + " invalid"
                        AlertDialog.Builder(this)
                            .setTitle("Font pack processed")
                            .setMessage(
                                "Imported: " + result.imported +
                                    "\nAlready present: " + result.duplicates +
                                    "\nInvalid/unreadable: " + result.invalid +
                                    "\nOther archive files ignored: " + result.ignored +
                                    "\n\nAll fonts are stored only in this app's private storage. " +
                                    "No files were uploaded or added to the public repository."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    val imported = FontManager.importFont(this, uri, name)
                    runOnUiThread {
                        fontSnapshot = null
                        renderFonts()
                        Toast.makeText(
                            this, "Font imported: " + imported.name, Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    selectedStatus.text = "Font import failed."
                    AlertDialog.Builder(this)
                        .setTitle("Could not import font pack")
                        .setMessage(
                            exception.message ?: "The font file is invalid or unsupported."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
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
        return uri.lastPathSegment ?: "ImportedFont.ttf"
    }

    private fun confirmRemove(record: FontManager.FontRecord) {
        AlertDialog.Builder(this)
            .setTitle("Remove imported font?")
            .setMessage(record.name + " will be removed from this device's private font library.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                val wasDefault = FontManager.getDefaultFont(this) == record.cssFamily
                FontManager.removeCustomFont(this, record)
                fontSnapshot = null
                if (wasDefault) {
                    FontManager.setDefaultFont(this, FontManager.getBundledFonts()[0])
                }
                renderFonts()
            }
            .show()
    }

    private fun addHeading(parent: LinearLayout, heading: String, eyebrow: String) {
        val label = text(eyebrow, 9, PRIMARY, true).apply { letterSpacing = 0.12f }
        val labelParams = wrapMatch().apply {
            topMargin = dp(22)
            bottomMargin = dp(3)
        }
        parent.addView(label, labelParams)
        parent.addView(text(heading, 18, INK, true), wrapMatch())
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = true
        }

    private fun button(value: String, backgroundColor: Int, foreground: Int): Button =
        Button(this).apply {
            text = value
            textSize = 12f
            isAllCaps = false
            setTextColor(foreground)
            background = roundRect(backgroundColor, dp(11))
            setPadding(dp(10), 0, dp(10), 0)
            minHeight = dp(42)
        }

    private fun roundRect(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun wrapMatch() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        importer.shutdown()
        super.onDestroy()
    }
}
