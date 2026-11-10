package com.fz.office.writer

import android.util.Base64
import android.util.Xml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Small offline DOCX bridge.
 *
 * It deliberately handles a documented subset of WordprocessingML instead of
 * pretending to preserve features it does not understand. Existing package
 * parts are retained, while word/document.xml is rebuilt from the editable HTML.
 */
object DocxCodec {
    private const val WORD_NS =
        "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val MAX_ENTRY_BYTES = 20 * 1024 * 1024
    private const val MAX_PACKAGE_BYTES = 80 * 1024 * 1024

    class LoadedDocument internal constructor(
        val html: String,
        val packageEntries: LinkedHashMap<String, ByteArray>,
        val fontNames: List<String>,
        val legacyProfile: String,
        val legacyFontName: String,
        val pageLayout: PageLayout
    )

    /** Word page geometry is stored in twips (1/20 point). */
    class PageLayout internal constructor(
        val widthTwips: Int,
        val heightTwips: Int,
        val topTwips: Int,
        val rightTwips: Int,
        val bottomTwips: Int,
        val leftTwips: Int
    )

    @Throws(Exception::class)
    fun readDocx(input: InputStream): LoadedDocument {
        val entries = readPackage(input)
        val documentXml = entries["word/document.xml"]
            ?: throw IOException("This file is not a supported DOCX document.")
        val imageRelationships = readDocumentImageRelationships(entries)
        val styleFonts = readStyleFontMap(entries["word/styles.xml"])
        var html = parseDocumentXml(documentXml, imageRelationships, styleFonts)
        if (html.trim().isEmpty()) html = "<p></p>"
        val fonts = inspectFontNames(entries)
        val legacyFont = findLegacyFontName(fonts)
        val legacyProfile = detectLegacyProfile(fonts)
        val pageLayout = readPageLayout(documentXml)
        return LoadedDocument(html, entries, fonts, legacyProfile, legacyFont, pageLayout)
    }

    @Throws(IOException::class)
    fun readPackage(input: InputStream): LinkedHashMap<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val data = readCurrentEntry(zip, MAX_ENTRY_BYTES)
                    totalBytes += data.size
                    if (totalBytes > MAX_PACKAGE_BYTES) {
                        throw IOException("The DOCX package is too large to open safely.")
                    }
                    entries[entry.name] = data
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    /**
     * Reads font-family declarations from document and styles.
     * Word may store legacy fonts in styles.xml rather than directly on each run.
     */
    private fun findLegacyFontName(fonts: List<String>): String {
        for (font in fonts) {
            val lower = font.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            if (lower.contains("krutidev") || lower.contains("devlys") ||
                lower.contains("chanakya") || lower.contains("shreelipi") ||
                lower.contains("shusha") || lower.contains("apsdv")
            ) {
                return font
            }
        }
        return ""
    }

    /**
     * Returns a converter profile only when metadata gives sufficiently strong
     * evidence for the exact mapping implemented by the legacy converter.
     */
    private fun detectLegacyProfile(fonts: List<String>): String {
        // Content-only guessing caused valid Hindi/English paragraphs to be corrupted.
        // Only an explicit, exact DOCX font declaration may select this converter.
        for (font in fonts) {
            val normalized = font.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            if (normalized == "devlys010" || normalized == "devlys10") return "devlys010"
            if (normalized == "krutidev010" || normalized == "krutidev10") return "krutidev010"
        }
        return ""
    }

    @Throws(Exception::class)
    private fun inspectFontNames(entries: Map<String, ByteArray>): List<String> {
        val names = LinkedHashSet<String>()
        for ((path, bytes) in entries) {
            if (!path.startsWith("word/") || !path.endsWith(".xml")) continue
            // fontTable.xml lists unused installed fonts too; only body and styles are used.
            if (path != "word/document.xml" && path != "word/styles.xml") continue

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "rFonts" -> {
                        addFontName(names, attribute(parser, "ascii"))
                        addFontName(names, attribute(parser, "hAnsi"))
                        addFontName(names, attribute(parser, "eastAsia"))
                        addFontName(names, attribute(parser, "cs"))
                    }
                    "font" -> addFontName(names, attribute(parser, "name"))
                }
            }
        }
        return ArrayList(names)
    }

    private fun addFontName(names: LinkedHashSet<String>, value: String?) {
        val font = value?.trim() ?: return
        if (font.isEmpty() || font.startsWith("+")) return
        names.add(font)
    }

    /**
     * Conservative content-only hint for old Hindi keyboard-font text.
     * It never converts automatically; it only offers the user a preview action.
     */
    fun looksLikeLegacyHindi(html: String?): Boolean {
        val text = htmlToPlainText(html.orEmpty())
        if (text.trim().isEmpty()) return false
        for (ch in text) {
            if (ch in '\u0900'..'\u097F') return false
        }

        val words = text.split(Regex("\\s+"))
        var considered = 0
        var suspicious = 0
        for (original in words) {
            val word = original.replace(Regex("[^A-Za-z;]"), "")
            if (word.length < 3) continue
            considered++
            val mixedCase = Regex(".*[a-z][A-Z].*").matches(word)
            val semicolonLetter = Regex(".*[A-Za-z];[A-Za-z].*").matches(word) ||
                Regex(".*[A-Za-z];$").matches(word)
            val shortIMatra = Regex("(?i)^f[^aeiouyhlr].*").matches(word)
            var legacyChars = 0
            val common = "kedsglynufz"
            for (ch in word) {
                if (common.contains(ch.lowercaseChar())) legacyChars++
            }
            val ratio = if (word.isEmpty()) 0.0 else legacyChars.toDouble() / word.length
            if (mixedCase || semicolonLetter || shortIMatra || (word.length > 3 && ratio > 0.65)) {
                suspicious++
            }
        }
        return considered >= 4 && suspicious >= 2 &&
            suspicious.toDouble() / considered >= 0.35
    }

    @Throws(Exception::class)
    private fun readDocumentImageRelationships(
        entries: Map<String, ByteArray>
    ): Map<String, String> {
        val images = LinkedHashMap<String, String>()
        val relationshipXml = entries["word/_rels/document.xml.rels"] ?: return images

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(relationshipXml), "UTF-8")
        var totalImageBytes = 0
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "Relationship") continue

            val type = attribute(parser, "Type")
            val id = attribute(parser, "Id")
            val target = attribute(parser, "Target")
            val targetMode = attribute(parser, "TargetMode")
            if (id == null || target == null || targetMode.equals("External", ignoreCase = true)) {
                skipElement(parser)
                continue
            }
            if (type == null || !type.lowercase(Locale.ROOT).endsWith("/image")) {
                skipElement(parser)
                continue
            }

            val packagePath = normalizeRelationshipTarget("word", target)
            val imageBytes = entries[packagePath]
            if (imageBytes == null || imageBytes.isEmpty() || imageBytes.size > 12 * 1024 * 1024) {
                skipElement(parser)
                continue
            }
            totalImageBytes += imageBytes.size
            if (totalImageBytes > 32 * 1024 * 1024) {
                skipElement(parser)
                continue
            }

            val mime = imageMimeType(packagePath)
            if (mime == null) {
                skipElement(parser)
                continue
            }
            val dataUri = "data:" + mime + ";base64," +
                Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            images[id] = dataUri
            skipElement(parser)
        }
        return images
    }

    private fun normalizeRelationshipTarget(baseDirectory: String, target: String): String {
        val normalized = target.replace('\\', '/')
        val parts = ArrayList<String>()
        if (!normalized.startsWith("/")) {
            baseDirectory.split("/").filter { it.isNotEmpty() }.forEach { parts.add(it) }
        }
        for (part in normalized.split("/")) {
            when {
                part.isEmpty() || part == "." -> Unit
                part == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun imageMimeType(path: String): String? {
        val lower = path.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".jpe") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".bmp") || lower.endsWith(".dib") -> "image/bmp"
            lower.endsWith(".webp") -> "image/webp"
            else -> null
        }
    }

    @Throws(Exception::class)
    private fun readImageContainer(
        parser: XmlPullParser,
        imageRelationships: Map<String, String>
    ): String {
        var relationshipId: String? = null
        var widthEmu = 0L
        var heightEmu = 0L
        var depth = 1

        while (depth > 0) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG) {
                depth++
                when (parser.name) {
                    "blip" -> relationshipId = attribute(parser, "embed")
                    "imagedata" -> relationshipId = attribute(parser, "id")
                    "extent" -> {
                        try {
                            widthEmu = attribute(parser, "cx")?.toLong() ?: 0L
                            heightEmu = attribute(parser, "cy")?.toLong() ?: 0L
                        } catch (_: Exception) {
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                depth--
            }
        }

        val id = relationshipId ?: return ""
        val source = imageRelationships[id] ?: return ""
        val image = StringBuilder("<img src=\"")
            .append(source)
            .append("\" alt=\"Document image\" style=\"display:block;max-width:100%;height:auto;object-fit:contain;")
        if (widthEmu > 0) {
            val widthPx = (widthEmu / 9525).coerceIn(24L, 1400L)
            image.append("width:").append(widthPx).append("px;")
        }
        if (heightEmu > 0) {
            val heightPx = (heightEmu / 9525).coerceIn(16L, 1800L)
            image.append("max-height:").append(heightPx).append("px;")
        }
        image.append("\"/>")
        return image.toString()
    }

    @Throws(IOException::class)
    private fun readCurrentEntry(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) {
                throw IOException("A DOCX component is larger than the supported limit.")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    @Throws(Exception::class)
    private fun readPageLayout(documentXml: ByteArray): PageLayout {
        var width = 11906
        var height = 16838
        var top = 1440
        var right = 1440
        var bottom = 1440
        var left = 1440
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(documentXml), "UTF-8")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "pgSz" -> {
                    width = boundedTwips(attribute(parser, "w"), width, 7200, 31680)
                    height = boundedTwips(attribute(parser, "h"), height, 7200, 31680)
                    val orientation = attribute(parser, "orient")
                    if (orientation.equals("landscape", true) && width < height) {
                        val swap = width
                        width = height
                        height = swap
                    } else if (orientation.equals("portrait", true) && width > height) {
                        val swap = width
                        width = height
                        height = swap
                    }
                }
                "pgMar" -> {
                    top = boundedTwips(attribute(parser, "top"), top, 0, 7200)
                    right = boundedTwips(attribute(parser, "right"), right, 0, 7200)
                    bottom = boundedTwips(attribute(parser, "bottom"), bottom, 0, 7200)
                    left = boundedTwips(attribute(parser, "left"), left, 0, 7200)
                }
            }
        }
        left = minOf(left, (width / 2 - 360).coerceAtLeast(0))
        right = minOf(right, (width / 2 - 360).coerceAtLeast(0))
        top = minOf(top, (height / 2 - 360).coerceAtLeast(0))
        bottom = minOf(bottom, (height / 2 - 360).coerceAtLeast(0))
        return PageLayout(width, height, top, right, bottom, left)
    }

    private fun boundedTwips(raw: String?, fallback: Int, minimum: Int, maximum: Int): Int {
        if (raw == null) return fallback
        return try {
            raw.toLong().coerceIn(minimum.toLong(), maximum.toLong()).toInt()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    @Throws(Exception::class)
    private fun parseDocumentXml(
        xml: ByteArray,
        imageRelationships: Map<String, String>,
        styleFonts: Map<String, String>
    ): String {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val html = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "body") {
                parseBody(parser, html, imageRelationships, styleFonts)
                break
            }
        }
        return html.toString()
    }

    @Throws(Exception::class)
    private fun parseBody(
        parser: XmlPullParser,
        html: StringBuilder,
        imageRelationships: Map<String, String>,
        styleFonts: Map<String, String>
    ) {
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.name == "body") return
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "p" -> parseParagraph(parser, html, imageRelationships, styleFonts)
                "tbl" -> parseTable(parser, html, imageRelationships, styleFonts)
                else -> skipElement(parser)
            }
        }
    }

    @Throws(Exception::class)
    private fun parseParagraph(
        parser: XmlPullParser,
        html: StringBuilder,
        imageRelationships: Map<String, String>,
        styleFonts: Map<String, String>
    ) {
        var alignment = "left"
        var paragraphStyleId: String? = null
        var pageBreakBefore = false
        val content = StringBuilder()

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "p") break
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "pPr" -> {
                    val properties = readParagraphProperties(parser)
                    alignment = properties.alignment
                    paragraphStyleId = properties.styleId
                    pageBreakBefore = properties.pageBreakBefore
                }
                "r" -> content.append(
                    readRun(
                        parser, imageRelationships,
                        resolveParagraphFont(styleFonts, paragraphStyleId)
                    )
                )
                "hyperlink", "sdtContent", "smartTag" -> parseInlineContainer(
                    parser, parser.name, content, imageRelationships,
                    resolveParagraphFont(styleFonts, paragraphStyleId)
                )
                "bookmarkStart", "bookmarkEnd", "proofErr" -> skipElement(parser)
                else -> skipElement(parser)
            }
        }

        if (pageBreakBefore) {
            html.append("<div class='fz-manual-break' data-fz-page-break='true'></div>")
        }
        html.append("<p")
        if (alignment != "left") {
            html.append(" style=\"text-align:").append(alignment).append("\"")
        }
        html.append(">")
        if (content.isEmpty()) html.append("<br/>") else html.append(content)
        html.append("</p>")
    }

    @Throws(Exception::class)
    private fun parseInlineContainer(
        parser: XmlPullParser,
        endTag: String,
        html: StringBuilder,
        imageRelationships: Map<String, String>,
        inheritedFont: String?
    ) {
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && endTag == parser.name) return
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "r" -> html.append(readRun(parser, imageRelationships, inheritedFont))
                "hyperlink", "sdtContent", "smartTag" ->
                    parseInlineContainer(parser, parser.name, html, imageRelationships, inheritedFont)
                else -> skipElement(parser)
            }
        }
    }

    private data class ParagraphProperties(
        var alignment: String = "left",
        var styleId: String? = null,
        var pageBreakBefore: Boolean = false
    )

    @Throws(Exception::class)
    private fun readParagraphProperties(parser: XmlPullParser): ParagraphProperties {
        val properties = ParagraphProperties()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return properties
            if (event == XmlPullParser.END_TAG && parser.name == "pPr") return properties
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "jc" -> {
                    val value = attribute(parser, "val")
                    properties.alignment = when (value) {
                        "center" -> "center"
                        "right", "end" -> "right"
                        "both", "distribute" -> "justify"
                        else -> properties.alignment
                    }
                    skipElement(parser)
                }
                "pStyle" -> {
                    properties.styleId = attribute(parser, "val")
                    skipElement(parser)
                }
                "pageBreakBefore" -> {
                    properties.pageBreakBefore = enabled(attribute(parser, "val"))
                    skipElement(parser)
                }
                else -> skipElement(parser)
            }
        }
    }

    /** Resolves paragraph style fonts and basedOn inheritance from styles.xml. */
    @Throws(Exception::class)
    private fun readStyleFontMap(stylesXml: ByteArray?): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        if (stylesXml == null) return result
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(stylesXml), "UTF-8")
        var currentStyle: String? = null
        var inDocDefaults = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "docDefaults" -> inDocDefaults = true
                    "style" -> currentStyle = attribute(parser, "styleId")
                    "basedOn" -> {
                        if (currentStyle != null) {
                            val parent = attribute(parser, "val")
                            if (parent != null) result["parent:" + currentStyle] = parent
                        }
                        skipElement(parser)
                    }
                    "rFonts" -> {
                        val font = chooseFontFamily(
                            attribute(parser, "ascii"),
                            attribute(parser, "hAnsi"),
                            attribute(parser, "eastAsia"),
                            attribute(parser, "cs")
                        )
                        if (font != null) {
                            if (currentStyle != null) result["font:" + currentStyle] = font
                            else if (inDocDefaults) result["default"] = font
                        }
                        skipElement(parser)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "style" -> currentStyle = null
                    "docDefaults" -> inDocDefaults = false
                }
            }
        }
        return result
    }

    private fun resolveParagraphFont(styleFonts: Map<String, String>?, styleId: String?): String? {
        if (styleFonts == null) return null
        var current = styleId
        for (depth in 0 until 24) {
            if (current.isNullOrEmpty()) break
            val font = styleFonts["font:" + current]
            if (!font.isNullOrEmpty()) return font
            current = styleFonts["parent:" + current]
        }
        return styleFonts["default"]
    }

    private fun chooseFontFamily(
        ascii: String?,
        hAnsi: String?,
        eastAsia: String?,
        complex: String?
    ): String? {
        // Legacy Hindi text is commonly stored as Latin code points, so ASCII/HAnsi
        // is the font group that controls the glyph mapping in those runs.
        return listOf(ascii, hAnsi, eastAsia, complex)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }

    @Throws(Exception::class)
    private fun readRun(
        parser: XmlPullParser,
        imageRelationships: Map<String, String>,
        inheritedFont: String?
    ): String {
        val style = RunStyle().apply { fontFamily = inheritedFont }
        val output = StringBuilder()
        val text = StringBuilder()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "r") break
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "rPr" -> readRunProperties(parser, style)
                "t" -> text.append(parser.nextText())
                "tab" -> {
                    text.append('\t')
                    skipElement(parser)
                }
                "br" -> {
                    if (attribute(parser, "type").equals("page", true)) {
                        if (text.isNotEmpty()) {
                            output.append(runToHtml(text.toString(), style))
                            text.setLength(0)
                        }
                        output.append("<div class='fz-manual-break' data-fz-page-break='true'></div>")
                    } else {
                        text.append('\n')
                    }
                    skipElement(parser)
                }
                "cr" -> {
                    text.append('\n')
                    skipElement(parser)
                }
                "drawing", "pict" -> {
                    if (text.isNotEmpty()) {
                        output.append(runToHtml(text.toString(), style))
                        text.setLength(0)
                    }
                    output.append(readImageContainer(parser, imageRelationships))
                }
                else -> skipElement(parser)
            }
        }
        if (text.isNotEmpty()) output.append(runToHtml(text.toString(), style))
        return output.toString()
    }

    @Throws(Exception::class)
    private fun readRunProperties(parser: XmlPullParser, style: RunStyle) {
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.name == "rPr") return
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "b" -> {
                    style.bold = enabled(attribute(parser, "val"))
                    skipElement(parser)
                }
                "i" -> {
                    style.italic = enabled(attribute(parser, "val"))
                    skipElement(parser)
                }
                "u" -> {
                    style.underline = attribute(parser, "val") != "none"
                    skipElement(parser)
                }
                "strike", "dstrike" -> {
                    style.strike = enabled(attribute(parser, "val"))
                    skipElement(parser)
                }
                "color" -> {
                    val value = attribute(parser, "val")
                    if (value != null && Regex("(?i)[0-9a-f]{6}").matches(value)) {
                        style.color = "#" + value
                    }
                    skipElement(parser)
                }
                "highlight" -> {
                    style.highlight = highlightColor(attribute(parser, "val"))
                    skipElement(parser)
                }
                "sz" -> {
                    try {
                        style.fontSizePt = (attribute(parser, "val")?.toFloat() ?: 0f) / 2f
                    } catch (_: Exception) {
                    }
                    skipElement(parser)
                }
                "rFonts" -> {
                    val font = chooseFontFamily(
                        attribute(parser, "ascii"),
                        attribute(parser, "hAnsi"),
                        attribute(parser, "eastAsia"),
                        attribute(parser, "cs")
                    )
                    if (font != null) style.fontFamily = font
                    skipElement(parser)
                }
                "vertAlign" -> {
                    style.vertical = attribute(parser, "val")
                    skipElement(parser)
                }
                else -> skipElement(parser)
            }
        }
    }

    @Throws(Exception::class)
    private fun parseTable(
        parser: XmlPullParser,
        html: StringBuilder,
        imageRelationships: Map<String, String>,
        styleFonts: Map<String, String>
    ) {
        html.append("<table><tbody>")
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "tbl") break
            if (event == XmlPullParser.START_TAG && parser.name == "tr") {
                parseTableRow(parser, html, imageRelationships, styleFonts)
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser)
            }
        }
        html.append("</tbody></table>")
    }

    @Throws(Exception::class)
    private fun parseTableRow(
        parser: XmlPullParser,
        html: StringBuilder,
        imageRelationships: Map<String, String>,
        styleFonts: Map<String, String>
    ) {
        html.append("<tr>")
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "tr") break
            if (event == XmlPullParser.START_TAG && parser.name == "tc") {
                parseTableCell(parser, html, imageRelationships, styleFonts)
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser)
            }
        }
        html.append("</tr>")
    }

    @Throws(Exception::class)
    private fun parseTableCell(
        parser: XmlPullParser,
        html: StringBuilder,
        imageRelationships: Map<String, String>,
        styleFonts: Map<String, String>
    ) {
        html.append("<td>")
        var hasContent = false
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "tc") break
            if (event == XmlPullParser.START_TAG && parser.name == "p") {
                parseParagraph(parser, html, imageRelationships, styleFonts)
                hasContent = true
            } else if (event == XmlPullParser.START_TAG && parser.name == "tbl") {
                parseTable(parser, html, imageRelationships, styleFonts)
                hasContent = true
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser)
            }
        }
        if (!hasContent) html.append("<p><br/></p>")
        html.append("</td>")
    }

    @Throws(Exception::class)
    private fun skipElement(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.START_TAG) depth++
            else if (event == XmlPullParser.END_TAG) depth--
        }
    }

    private fun attribute(parser: XmlPullParser, name: String): String? {
        var value = parser.getAttributeValue(WORD_NS, name)
        if (value != null) return value
        value = parser.getAttributeValue(null, name)
        if (value != null) return value
        for (index in 0 until parser.attributeCount) {
            if (name == parser.getAttributeName(index)) return parser.getAttributeValue(index)
        }
        return null
    }

    private fun enabled(value: String?): Boolean =
        value == null || (value != "0" && !value.equals("false", true) && !value.equals("off", true))

    private fun highlightColor(value: String?): String? = when (value) {
        "yellow" -> "#ffff00"
        "green" -> "#00ff00"
        "cyan" -> "#00ffff"
        "magenta" -> "#ff00ff"
        "red" -> "#ff0000"
        "blue" -> "#0000ff"
        "darkBlue" -> "#00008b"
        "darkRed" -> "#8b0000"
        "darkGreen" -> "#006400"
        "darkGray" -> "#555555"
        "lightGray" -> "#dddddd"
        else -> null
    }

    private fun runToHtml(text: String, style: RunStyle): String {
        val out = StringBuilder()
        val css = StringBuilder()
        if (style.fontSizePt > 0f) css.append("font-size:").append(style.fontSizePt).append("pt;")
        style.color?.let { css.append("color:").append(it).append(";") }
        style.highlight?.let { css.append("background-color:").append(it).append(";") }
        style.fontFamily?.let { family ->
            val safeFamily = family.replace("\\", "\\\\").replace("'", "\\'")
            css.append("font-family:'").append(safeFamily)
                .append("','Noto Sans Devanagari','Noto Serif Devanagari',sans-serif;")
        }
        if (css.isNotEmpty()) out.append("<span style=\"").append(css).append("\">")
        if (style.bold) out.append("<strong>")
        if (style.italic) out.append("<em>")
        if (style.underline) out.append("<u>")
        if (style.strike) out.append("<s>")
        if (style.vertical == "superscript") out.append("<sup>")
        if (style.vertical == "subscript") out.append("<sub>")

        val lines = text.split("\n")
        for ((index, line) in lines.withIndex()) {
            if (index > 0) out.append("<br/>")
            out.append(escapeHtml(line).replace("\t", "&#9;"))
        }

        if (style.vertical == "superscript") out.append("</sup>")
        if (style.vertical == "subscript") out.append("</sub>")
        if (style.strike) out.append("</s>")
        if (style.underline) out.append("</u>")
        if (style.italic) out.append("</em>")
        if (style.bold) out.append("</strong>")
        if (css.isNotEmpty()) out.append("</span>")
        return out.toString()
    }

    @Throws(Exception::class)
    fun writeDocx(html: String?, originalEntries: Map<String, ByteArray>?): ByteArray =
        writeDocx(html, originalEntries, null)

    @Throws(Exception::class)
    fun writeDocx(
        html: String?,
        originalEntries: Map<String, ByteArray>?,
        pageLayout: PageLayout?
    ): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        if (originalEntries != null) entries.putAll(originalEntries)
        if (!entries.containsKey("[Content_Types].xml")) {
            entries["[Content_Types].xml"] = CONTENT_TYPES.toByteArray(StandardCharsets.UTF_8)
        }
        if (!entries.containsKey("_rels/.rels")) {
            entries["_rels/.rels"] = ROOT_RELS.toByteArray(StandardCharsets.UTF_8)
        }
        entries["word/document.xml"] =
            htmlToDocumentXml(html, pageLayout).toByteArray(StandardCharsets.UTF_8)
        if (!entries.containsKey("word/styles.xml")) {
            entries["word/styles.xml"] = STYLES.toByteArray(StandardCharsets.UTF_8)
        }
        if (!entries.containsKey("word/_rels/document.xml.rels")) {
            entries["word/_rels/document.xml.rels"] =
                DOCUMENT_RELS.toByteArray(StandardCharsets.UTF_8)
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.finish()
        }
        return output.toByteArray()
    }

    private fun htmlToDocumentXml(html: String?, pageLayout: PageLayout?): String {
        val pageWidth = pageLayout?.widthTwips ?: 11906
        val pageHeight = pageLayout?.heightTwips ?: 16838
        val marginTop = pageLayout?.topTwips ?: 1440
        val marginRight = pageLayout?.rightTwips ?: 1440
        val marginBottom = pageLayout?.bottomTwips ?: 1440
        val marginLeft = pageLayout?.leftTwips ?: 1440
        val parsed = Jsoup.parseBodyFragment(html.orEmpty())
        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        xml.append("<w:document xmlns:w=\"").append(WORD_NS)
            .append("\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        xml.append("<w:body>")
        for (node in parsed.body().childNodes()) appendBlock(node, xml)
        if (!xml.contains("<w:p")) xml.append("<w:p/>")
        xml.append("<w:sectPr><w:pgSz w:w=\"").append(pageWidth)
            .append("\" w:h=\"").append(pageHeight).append("\"/>")
            .append("<w:pgMar w:top=\"").append(marginTop)
            .append("\" w:right=\"").append(marginRight)
            .append("\" w:bottom=\"").append(marginBottom)
            .append("\" w:left=\"").append(marginLeft)
            .append("\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/>")
            .append("</w:sectPr></w:body></w:document>")
        return xml.toString()
    }

    private fun appendBlock(node: Node, xml: StringBuilder) {
        if (node is TextNode) {
            val text = node.wholeText
            if (text.trim().isNotEmpty()) appendParagraph(null, text, xml)
            return
        }
        if (node !is Element) return
        val element = node
        val tag = element.tagName().lowercase(Locale.ROOT)

        if (tag == "div" && (element.hasClass("fz-manual-break") ||
                element.attr("data-fz-page-break") == "true")
        ) {
            // Keep explicit page breaks when the edited HTML is exported back to DOCX.
            xml.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        } else if (tag == "table") {
            appendTable(element, xml)
        } else if (tag == "ul" || tag == "ol") {
            var index = 1
            for (item in element.children()) {
                if (item.tagName() == "li") {
                    val prefix = if (tag == "ul") "• " else (index++).toString() + ". "
                    appendParagraph(item, prefix, xml)
                } else {
                    appendBlock(item, xml)
                }
            }
        } else if (tag == "p" || tag == "div" || tag == "h1" || tag == "h2" ||
            tag == "h3" || tag == "h4" || tag == "li" || tag == "blockquote"
        ) {
            val hasNestedBlocks = element.children().any { child ->
                child.tagName().lowercase(Locale.ROOT) in setOf("p", "table", "ul", "ol")
            }
            if (hasNestedBlocks && tag != "p") {
                for (child in element.childNodes()) appendBlock(child, xml)
            } else {
                appendParagraph(element, if (tag == "li") "• " else "", xml)
            }
        } else {
            appendParagraph(element, "", xml)
        }
    }

    private fun appendParagraph(element: Element?, prefix: String, xml: StringBuilder) {
        xml.append("<w:p>")
        val alignment = element?.let { cssValue(it.attr("style"), "text-align") }.orEmpty()
        if (alignment.isNotEmpty()) {
            val value = alignment.lowercase(Locale.ROOT)
            if (value == "left" || value == "center" || value == "right" || value == "justify") {
                xml.append("<w:pPr><w:jc w:val=\"").append(value).append("\"/></w:pPr>")
            }
        }
        if (prefix.isNotEmpty()) appendRun(prefix, RunStyle(), xml)
        if (element != null) {
            val initial = RunStyle()
            val defaultFamily = firstCssFontFamily(cssValue(element.attr("style"), "font-family"))
            if (defaultFamily.isNotEmpty()) initial.fontFamily = defaultFamily
            when (element.tagName().lowercase(Locale.ROOT)) {
                "h1" -> { initial.bold = true; initial.fontSizePt = 20f }
                "h2" -> { initial.bold = true; initial.fontSizePt = 16f }
                "h3" -> { initial.bold = true; initial.fontSizePt = 14f }
            }
            for (child in element.childNodes()) appendInline(child, initial, xml)
        }
        xml.append("</w:p>")
    }

    private fun appendInline(node: Node, inherited: RunStyle, xml: StringBuilder) {
        if (node is TextNode) {
            appendRun(node.wholeText, inherited, xml)
            return
        }
        if (node !is Element) return
        val element = node
        val tag = element.tagName().lowercase(Locale.ROOT)
        if (tag == "br") {
            xml.append("<w:r><w:br/></w:r>")
            return
        }
        if (tag == "img") return

        val style = inherited.copy()
        if (tag == "b" || tag == "strong") style.bold = true
        if (tag == "i" || tag == "em") style.italic = true
        if (tag == "u") style.underline = true
        if (tag == "s" || tag == "strike" || tag == "del") style.strike = true
        if (tag == "sup") style.vertical = "superscript"
        if (tag == "sub") style.vertical = "subscript"
        if (tag == "font" && element.hasAttr("face")) style.fontFamily = element.attr("face")
        if (tag == "font" && element.hasAttr("size")) {
            val size = element.attr("size")
            val points = arrayOf("8", "10", "12", "14", "18", "24", "36")
            try {
                val index = size.toInt() - 1
                if (index in points.indices) style.fontSizePt = points[index].toFloat()
            } catch (_: Exception) {
            }
        }

        val css = element.attr("style")
        val weight = cssValue(css, "font-weight")
        val fontStyle = cssValue(css, "font-style")
        val decoration = cssValue(css, "text-decoration")
        if (weight.equals("bold", true) || weight == "700" || weight == "800" || weight == "900") {
            style.bold = true
        }
        if (fontStyle.equals("italic", true)) style.italic = true
        if (decoration.contains("underline")) style.underline = true
        if (decoration.contains("line-through")) style.strike = true

        val color = cssValue(css, "color")
        if (Regex("(?i)#[0-9a-f]{6}").matches(color)) style.color = color
        val background = cssValue(css, "background-color")
        if (Regex("(?i)#[0-9a-f]{6}").matches(background)) style.highlight = background
        val fontSize = cssValue(css, "font-size")
        if (fontSize.isNotEmpty()) style.fontSizePt = parseFontSize(fontSize, style.fontSizePt)
        val family = firstCssFontFamily(cssValue(css, "font-family"))
        if (family.isNotEmpty()) style.fontFamily = family

        for (child in element.childNodes()) appendInline(child, style, xml)
    }

    private fun appendTable(table: Element, xml: StringBuilder) {
        xml.append("<w:tbl><w:tblPr><w:tblBorders>")
            .append("<w:top w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
            .append("<w:left w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
            .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
            .append("<w:right w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
            .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
            .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
            .append("</w:tblBorders></w:tblPr>")
        for (row in table.select("tr")) {
            xml.append("<w:tr>")
            for (cell in row.children()) {
                if (cell.tagName() != "td" && cell.tagName() != "th") continue
                xml.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>")
                var hasParagraph = false
                for (child in cell.childNodes()) {
                    when {
                        child is Element && child.tagName() == "table" ->
                            appendTable(child, xml)
                        child is Element && child.tagName() == "p" -> {
                            appendParagraph(child, "", xml)
                            hasParagraph = true
                        }
                        child is TextNode -> {
                            val text = child.wholeText
                            if (text.trim().isNotEmpty()) {
                                appendParagraph(null, text, xml)
                                hasParagraph = true
                            }
                        }
                        child is Element -> {
                            appendParagraph(child, "", xml)
                            hasParagraph = true
                        }
                    }
                }
                if (!hasParagraph) xml.append("<w:p/>")
                xml.append("</w:tc>")
            }
            xml.append("</w:tr>")
        }
        xml.append("</w:tbl>")
    }

    private fun appendRun(text: String, style: RunStyle, xml: StringBuilder) {
        if (text.isEmpty()) return
        val lines = text.split("\n")
        for ((index, value) in lines.withIndex()) {
            if (index > 0) xml.append("<w:r><w:br/></w:r>")
            if (value.isEmpty()) continue
            xml.append("<w:r>")
            val hasProps = style.bold || style.italic || style.underline || style.strike ||
                style.fontSizePt > 0f || style.color != null || style.highlight != null ||
                style.fontFamily != null || style.vertical != null
            if (hasProps) {
                xml.append("<w:rPr>")
                if (style.bold) xml.append("<w:b/>")
                if (style.italic) xml.append("<w:i/>")
                if (style.underline) xml.append("<w:u w:val=\"single\"/>")
                if (style.strike) xml.append("<w:strike/>")
                if (style.fontSizePt > 0f) {
                    val halfPoints = (style.fontSizePt * 2).toInt().coerceIn(12, 144)
                    xml.append("<w:sz w:val=\"").append(halfPoints).append("\"/>")
                }
                style.color?.let {
                    xml.append("<w:color w:val=\"").append(it.replace("#", "")).append("\"/>")
                }
                style.highlight?.let {
                    xml.append("<w:shd w:val=\"clear\" w:fill=\"")
                        .append(it.replace("#", "")).append("\"/>")
                }
                style.fontFamily?.let { family ->
                    val font = xmlEscape(family)
                    xml.append("<w:rFonts w:ascii=\"").append(font)
                        .append("\" w:hAnsi=\"").append(font).append("\"/>")
                }
                when (style.vertical) {
                    "superscript" -> xml.append("<w:vertAlign w:val=\"superscript\"/>")
                    "subscript" -> xml.append("<w:vertAlign w:val=\"subscript\"/>")
                }
                xml.append("</w:rPr>")
            }
            xml.append("<w:t xml:space=\"preserve\">").append(xmlEscape(value)).append("</w:t></w:r>")
        }
    }

    fun plainTextToHtml(text: String?): String {
        val value = text.orEmpty().replace("\r\n", "\n").replace('\r', '\n')
        val html = StringBuilder()
        val lines = value.split("\n")
        for (line in lines) {
            html.append("<p>")
            html.append(escapeHtml(line).replace("\t", "&#9;"))
            if (line.isEmpty()) html.append("<br/>")
            html.append("</p>")
        }
        if (html.isEmpty()) html.append("<p></p>")
        return html.toString()
    }

    fun htmlToPlainText(html: String?): String {
        val parsed = Jsoup.parseBodyFragment(html.orEmpty())
        val output = StringBuilder()
        for (node in parsed.body().childNodes()) appendPlainText(node, output)
        while (output.isNotEmpty() && output[output.length - 1] == '\n') {
            output.setLength(output.length - 1)
        }
        return output.toString()
    }

    private fun appendPlainText(node: Node, output: StringBuilder) {
        if (node is TextNode) {
            output.append(node.wholeText)
            return
        }
        if (node !is Element) return
        val element = node
        val tag = element.tagName().lowercase(Locale.ROOT)
        if (tag == "br") {
            output.append('\n')
            return
        }
        val block = tag == "p" || tag == "div" || tag == "li" || tag == "tr" ||
            tag == "h1" || tag == "h2" || tag == "h3" || tag == "blockquote"
        for (child in element.childNodes()) appendPlainText(child, output)
        if (block && (output.isEmpty() || output[output.length - 1] != '\n')) {
            output.append('\n')
        }
    }

    /**
     * DOCX rFonts accepts one family name, not the complete CSS fallback list.
     * Keep only the first CSS family so values such as
     * "'Kruti Dev 010','Noto Sans Devanagari',sans-serif" round-trip correctly.
     */
    private fun firstCssFontFamily(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val family = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (ch in value) {
            if (escaped) {
                family.append(ch)
                escaped = false
                continue
            }
            if (quote != null && ch.code == 92) {
                escaped = true
                continue
            }
            if (quote != null) {
                if (ch == quote) quote = null else family.append(ch)
                continue
            }
            if (ch == '\'' || ch == '"') {
                quote = ch
                continue
            }
            if (ch == ',') break
            family.append(ch)
        }
        return family.toString().trim()
    }

    private fun cssValue(css: String?, property: String): String {
        if (css == null) return ""
        for (declaration in css.split(";")) {
            val separator = declaration.indexOf(':')
            if (separator < 0) continue
            if (property.equals(declaration.substring(0, separator).trim(), true)) {
                return declaration.substring(separator + 1).trim()
            }
        }
        return ""
    }

    private fun parseFontSize(value: String, fallback: Float): Float {
        return try {
            val clean = value.lowercase(Locale.ROOT).trim()
            when {
                clean.endsWith("pt") -> clean.dropLast(2).toFloat()
                clean.endsWith("px") -> clean.dropLast(2).toFloat() * 0.75f
                else -> clean.toFloat()
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private data class RunStyle(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strike: Boolean = false,
        var fontSizePt: Float = 0f,
        var color: String? = null,
        var highlight: String? = null,
        var fontFamily: String? = null,
        var vertical: String? = null
    )

    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "</Types>"

    private const val ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"

    private const val DOCUMENT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

    private const val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:styles xmlns:w=\"$WORD_NS\">" +
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Noto Sans Devanagari\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>" +
            "</w:styles>"
}
