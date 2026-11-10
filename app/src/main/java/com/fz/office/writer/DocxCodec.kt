package com.fz.office.writer

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * FZ Office's read-only Kotlin OOXML reader.
 *
 * It handles a documented subset of WordprocessingML and converts document,
 * style, image and page-geometry data into the viewer's internal representation.
 * The original package is never rewritten or retained after parsing.
 */
object DocxCodec {
    private const val WORD_NS =
        "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val MAX_ENTRY_BYTES = 20 * 1024 * 1024
    private const val MAX_PACKAGE_BYTES = 80 * 1024 * 1024

    class LoadedDocument internal constructor(
        val html: String,
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
        return LoadedDocument(html, fonts, legacyProfile, legacyFont, pageLayout)
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
        var inFirstSection = false
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(documentXml), "UTF-8")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.name == "sectPr" && !inFirstSection) {
                    inFirstSection = true
                } else if (inFirstSection) {
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
            } else if (parser.eventType == XmlPullParser.END_TAG &&
                parser.name == "sectPr" && inFirstSection
            ) {
                break
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
        styleFonts: Map<String, String>,
        endTag: String = "body"
    ) {
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.name == endTag) return
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "p" -> parseParagraph(parser, html, imageRelationships, styleFonts)
                "tbl" -> parseTable(parser, html, imageRelationships, styleFonts)
                // Word frequently wraps real body blocks in content controls,
                // custom XML and tracked insertions. Recurse instead of dropping
                // every paragraph/table inside those containers.
                "sdt", "sdtContent", "customXml", "ins", "moveTo" ->
                    parseBody(parser, html, imageRelationships, styleFonts, parser.name)
                // Deleted/moved-from text is not part of the accepted document view.
                "del", "moveFrom" -> skipElement(parser)
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
        var paragraphStyleId: String? = null
        var properties = ParagraphProperties()
        val content = StringBuilder()

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name == "p") break
            if (event != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "pPr" -> {
                    properties = readParagraphProperties(parser)
                    paragraphStyleId = properties.styleId
                }
                "r" -> content.append(
                    readRun(
                        parser, imageRelationships,
                        resolveParagraphFont(styleFonts, paragraphStyleId)
                    )
                )
                "hyperlink", "sdt", "sdtContent", "smartTag", "customXml", "ins", "moveTo", "fldSimple" -> parseInlineContainer(
                    parser, parser.name, content, imageRelationships,
                    resolveParagraphFont(styleFonts, paragraphStyleId)
                )
                "bookmarkStart", "bookmarkEnd", "proofErr" -> skipElement(parser)
                else -> skipElement(parser)
            }
        }

        val pageBreakBefore = properties.pageBreakBefore
            ?: resolveParagraphFlag(styleFonts, paragraphStyleId, "pageBreak:")
        val keepNext = properties.keepNext
            ?: resolveParagraphFlag(styleFonts, paragraphStyleId, "keepNext:")
        val keepLines = properties.keepLines
            ?: resolveParagraphFlag(styleFonts, paragraphStyleId, "keepLines:")
        if (pageBreakBefore) {
            html.append("<div class='fz-manual-break' data-fz-page-break='true'></div>")
        }

        val css = StringBuilder(resolveParagraphCss(styleFonts, paragraphStyleId))
            .append(properties.css)
        properties.alignment?.let { css.append("text-align:").append(it).append(";") }
        html.append("<p")
        if (css.isNotEmpty()) {
            html.append(" style=\"").append(escapeHtml(css.toString())).append("\"")
        }
        if (keepNext) html.append(" data-fz-keep-next=\"true\"")
        if (keepLines) html.append(" data-fz-keep-lines=\"true\"")
        html.append(">")
        if (content.isEmpty()) html.append("<br/>") else html.append(content)
        html.append("</p>")
        if (properties.sectionBreakAfter) {
            html.append("<div class='fz-manual-break' data-fz-section-break='true'></div>")
        }
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
                "hyperlink", "sdt", "sdtContent", "smartTag", "customXml",
                "ins", "moveTo", "fldSimple" ->
                    parseInlineContainer(parser, parser.name, html, imageRelationships, inheritedFont)
                "del", "moveFrom" -> skipElement(parser)
                else -> skipElement(parser)
            }
        }
    }

    private data class ParagraphProperties(
        var alignment: String? = null,
        var styleId: String? = null,
        var pageBreakBefore: Boolean? = null,
        var keepNext: Boolean? = null,
        var keepLines: Boolean? = null,
        var sectionBreakAfter: Boolean = false,
        var css: String = ""
    )

    @Throws(Exception::class)
    private fun readParagraphProperties(parser: XmlPullParser): ParagraphProperties {
        val properties = ParagraphProperties()
        val css = StringBuilder()
        fun twips(raw: String?): Float? =
            raw?.toFloatOrNull()?.takeIf { it.isFinite() && it in -144000f..144000f }
                ?.div(15f)

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) {
                properties.css = css.toString()
                return properties
            }
            if (event == XmlPullParser.END_TAG && parser.name == "pPr") {
                properties.css = css.toString()
                return properties
            }
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "jc" -> {
                    properties.alignment = when (attribute(parser, "val")) {
                        "center" -> "center"
                        "right", "end" -> "right"
                        "both", "distribute" -> "justify"
                        else -> "left"
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
                "keepNext" -> {
                    properties.keepNext = enabled(attribute(parser, "val"))
                    skipElement(parser)
                }
                "keepLines" -> {
                    properties.keepLines = enabled(attribute(parser, "val"))
                    skipElement(parser)
                }
                "sectPr" -> {
                    // A section properties element inside pPr marks the end of this
                    // section. nextPage/oddPage/evenPage require a new physical page;
                    // continuous sections keep flowing on the current page.
                    var sectionType = "nextPage"
                    var depth = 1
                    while (depth > 0) {
                        val sectionEvent = parser.next()
                        if (sectionEvent == XmlPullParser.END_DOCUMENT) break
                        if (sectionEvent == XmlPullParser.START_TAG) {
                            depth++
                            if (parser.name == "type") {
                                sectionType = attribute(parser, "val") ?: "nextPage"
                                skipElement(parser)
                                depth--
                            }
                        } else if (sectionEvent == XmlPullParser.END_TAG) {
                            depth--
                        }
                    }
                    properties.sectionBreakAfter = sectionType != "continuous"
                }
                "spacing" -> {
                    twips(attribute(parser, "before"))?.let {
                        css.append("margin-top:").append(it).append("px;")
                    }
                    twips(attribute(parser, "after"))?.let {
                        css.append("margin-bottom:").append(it).append("px;")
                    }
                    val line = attribute(parser, "line")?.toFloatOrNull()
                        ?.takeIf { it.isFinite() && it > 0f }
                    if (line != null) {
                        when (attribute(parser, "lineRule") ?: "auto") {
                            "auto" -> css.append("line-height:").append(line / 240f).append(";")
                            "exact" -> css.append("line-height:").append(line / 15f).append("px;")
                            "atLeast" -> css.append("line-height:max(1.15em,")
                                .append(line / 15f).append("px);")
                        }
                    }
                    skipElement(parser)
                }
                "ind" -> {
                    twips(attribute(parser, "left"))?.let {
                        css.append("margin-left:").append(it).append("px;")
                    }
                    twips(attribute(parser, "right"))?.let {
                        css.append("margin-right:").append(it).append("px;")
                    }
                    val first = twips(attribute(parser, "firstLine"))
                    val hanging = twips(attribute(parser, "hanging"))
                    when {
                        first != null -> css.append("text-indent:").append(first).append("px;")
                        hanging != null -> css.append("text-indent:-").append(hanging).append("px;")
                    }
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
        var propertyContext: String? = null
        var inDocDefaults = false

        fun appendCss(key: String, declaration: String) {
            if (declaration.isBlank()) return
            result[key] = (result[key] ?: "") + declaration
        }
        fun twips(raw: String?): Float? =
            raw?.toFloatOrNull()?.takeIf { it.isFinite() }?.div(15f)
        fun safeFamily(value: String): String =
            value.replace("\\", "\\\\").replace("'", "\\'")

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "docDefaults" -> inDocDefaults = true
                    "style" -> {
                        currentStyle = attribute(parser, "styleId")
                        if (attribute(parser, "type") == "paragraph" &&
                            attribute(parser, "default")?.let {
                                it == "1" || it.equals("true", true) || it.equals("on", true)
                            } == true
                        ) {
                            currentStyle?.let { result["defaultParagraphStyle"] = it }
                        }
                    }
                    "pPrDefault" -> {
                        currentStyle = "__default__"
                        propertyContext = "p"
                    }
                    "rPrDefault" -> {
                        currentStyle = "__default__"
                        propertyContext = "r"
                    }
                    "pPr" -> propertyContext = "p"
                    "rPr" -> propertyContext = "r"
                    "basedOn" -> {
                        if (currentStyle != null && currentStyle != "__default__") {
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
                            if (currentStyle != null && currentStyle != "__default__") {
                                result["font:" + currentStyle] = font
                            } else if (inDocDefaults) {
                                result["default"] = font
                            }
                            if (propertyContext == "r" || propertyContext == "p") {
                                val key = (if (propertyContext == "p") "pCss:" else "rCss:") +
                                    (currentStyle ?: "__default__")
                                appendCss(key, "font-family:'" + safeFamily(font) +
                                    "','Noto Sans Devanagari',Arial,sans-serif;")
                            }
                        }
                        skipElement(parser)
                    }
                    "sz" -> {
                        val size = attribute(parser, "val")?.toFloatOrNull()
                        if (size != null && size in 2f..288f && propertyContext != null) {
                            val key = (if (propertyContext == "p") "pCss:" else "rCss:") +
                                (currentStyle ?: "__default__")
                            appendCss(key, "font-size:" + (size / 2f) + "pt;")
                        }
                        skipElement(parser)
                    }
                    "b", "i", "u", "strike", "dstrike" -> {
                        if (propertyContext != null) {
                            val key = (if (propertyContext == "p") "pCss:" else "rCss:") +
                                (currentStyle ?: "__default__")
                            val value = when (parser.name) {
                                "b" -> "font-weight:" + (if (enabled(attribute(parser, "val"))) "700" else "400") + ";"
                                "i" -> "font-style:" + (if (enabled(attribute(parser, "val"))) "italic" else "normal") + ";"
                                "u" -> if (attribute(parser, "val") == "none") "text-decoration:none;" else "text-decoration:underline;"
                                else -> if (enabled(attribute(parser, "val"))) "text-decoration:line-through;" else ""
                            }
                            appendCss(key, value)
                        }
                        skipElement(parser)
                    }
                    "color" -> {
                        val color = attribute(parser, "val")
                        if (propertyContext != null && color != null &&
                            Regex("(?i)[0-9a-f]{6}").matches(color)
                        ) {
                            val key = (if (propertyContext == "p") "pCss:" else "rCss:") +
                                (currentStyle ?: "__default__")
                            appendCss(key, "color:#" + color + ";")
                        }
                        skipElement(parser)
                    }
                    "jc" -> {
                        val value = when (attribute(parser, "val")) {
                            "center" -> "center"
                            "right", "end" -> "right"
                            "both", "distribute" -> "justify"
                            else -> "left"
                        }
                        if (propertyContext == "p") {
                            appendCss("pCss:" + (currentStyle ?: "__default__"),
                                "text-align:" + value + ";")
                        }
                        skipElement(parser)
                    }
                    "spacing" -> {
                        if (propertyContext == "p") {
                            val key = "pCss:" + (currentStyle ?: "__default__")
                            twips(attribute(parser, "before"))?.let {
                                appendCss(key, "margin-top:" + it + "px;")
                            }
                            twips(attribute(parser, "after"))?.let {
                                appendCss(key, "margin-bottom:" + it + "px;")
                            }
                            val line = attribute(parser, "line")?.toFloatOrNull()
                            if (line != null && line > 0f) {
                                val rule = attribute(parser, "lineRule") ?: "auto"
                                val lineCss = when (rule) {
                                    "auto" -> "line-height:" + (line / 240f) + ";"
                                    "exact" -> "line-height:" + (line / 15f) + "px;"
                                    "atLeast" -> "line-height:max(1.15em," + (line / 15f) + "px);"
                                    else -> ""
                                }
                                appendCss(key, lineCss)
                            }
                        }
                        skipElement(parser)
                    }
                    "ind" -> {
                        if (propertyContext == "p") {
                            val key = "pCss:" + (currentStyle ?: "__default__")
                            twips(attribute(parser, "left"))?.let {
                                appendCss(key, "margin-left:" + it + "px;")
                            }
                            twips(attribute(parser, "right"))?.let {
                                appendCss(key, "margin-right:" + it + "px;")
                            }
                            val first = twips(attribute(parser, "firstLine"))
                            val hanging = twips(attribute(parser, "hanging"))
                            when {
                                first != null -> appendCss(key, "text-indent:" + first + "px;")
                                hanging != null -> appendCss(key, "text-indent:-" + hanging + "px;")
                            }
                        }
                        skipElement(parser)
                    }
                    "pageBreakBefore" -> {
                        if (propertyContext == "p") {
                            result["pageBreak:" + (currentStyle ?: "__default__")] =
                                enabled(attribute(parser, "val")).toString()
                        }
                        skipElement(parser)
                    }
                    "keepNext" -> {
                        if (propertyContext == "p") {
                            result["keepNext:" + (currentStyle ?: "__default__")] =
                                enabled(attribute(parser, "val")).toString()
                        }
                        skipElement(parser)
                    }
                    "keepLines" -> {
                        if (propertyContext == "p") {
                            result["keepLines:" + (currentStyle ?: "__default__")] =
                                enabled(attribute(parser, "val")).toString()
                        }
                        skipElement(parser)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "pPr" -> if (propertyContext == "p") propertyContext = null
                    "rPr" -> if (propertyContext == "r") propertyContext = null
                    "pPrDefault", "rPrDefault" -> {
                        propertyContext = null
                        currentStyle = null
                    }
                    "style" -> currentStyle = null
                    "docDefaults" -> inDocDefaults = false
                }
            }
        }
        return result
    }

    private fun styleChain(styles: Map<String, String>, styleId: String?): List<String> {
        var current = styleId ?: styles["defaultParagraphStyle"]
        val chain = ArrayList<String>()
        val visited = HashSet<String>()
        for (depth in 0 until 24) {
            if (current.isNullOrEmpty() || current == "__default__" ||
                !visited.add(current)
            ) break
            chain.add(current)
            current = styles["parent:" + current]
        }
        return chain.asReversed()
    }

    private fun resolveParagraphFont(styles: Map<String, String>?, styleId: String?): String? {
        if (styles == null) return null
        val chain = styleChain(styles, styleId)
        for (id in chain.asReversed()) {
            styles["font:" + id]?.let { return it }
        }
        return styles["default"]
    }

    private fun resolveParagraphCss(styles: Map<String, String>?, styleId: String?): String {
        if (styles == null) return ""
        val css = StringBuilder()
        styles["rCss:__default__"]?.let { css.append(it) }
        styles["pCss:__default__"]?.let { css.append(it) }
        for (id in styleChain(styles, styleId)) {
            styles["rCss:" + id]?.let { css.append(it) }
            styles["pCss:" + id]?.let { css.append(it) }
        }
        return css.toString()
    }

    private fun resolveParagraphFlag(
        styles: Map<String, String>?,
        styleId: String?,
        prefix: String
    ): Boolean {
        if (styles == null) return false
        var value = styles[prefix + "__default__"]?.toBooleanStrictOrNull()
        for (id in styleChain(styles, styleId)) {
            styles[prefix + id]?.toBooleanStrictOrNull()?.let { value = it }
        }
        return value ?: false
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
            } else if (event == XmlPullParser.START_TAG &&
                parser.name in setOf("sdt", "sdtContent", "customXml", "ins", "moveTo")
            ) {
                parseBody(parser, html, imageRelationships, styleFonts, parser.name)
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

    /**
     * Extracts readable text from the small, internally generated HTML subset
     * used by the read-only Word viewer. This is not a general-purpose browser
     * HTML parser and never interprets or executes markup.
     */
    fun htmlToPlainText(html: String?): String {
        val source = html.orEmpty()
        val output = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            when (source[index]) {
                '<' -> {
                    if (source.startsWith("<!--", index)) {
                        val end = source.indexOf("-->", index + 4)
                        index = if (end >= 0) end + 3 else source.length
                        continue
                    }
                    val tagEnd = findHtmlTagEnd(source, index)
                    if (tagEnd < 0) {
                        output.append('<')
                        index++
                        continue
                    }
                    val rawTag = source.substring(index + 1, tagEnd).trim()
                    val closing = rawTag.startsWith("/")
                    var nameStart = if (closing) 1 else 0
                    while (nameStart < rawTag.length && rawTag[nameStart].isWhitespace()) nameStart++
                    var nameEnd = nameStart
                    while (nameEnd < rawTag.length && rawTag[nameEnd].isLetterOrDigit()) nameEnd++
                    val tagName = rawTag.substring(nameStart, nameEnd).lowercase(Locale.ROOT)
                    if (!closing && (tagName == "script" || tagName == "style")) {
                        val closingStart = source.indexOf("</$tagName", tagEnd + 1, ignoreCase = true)
                        if (closingStart < 0) {
                            index = source.length
                        } else {
                            val closingEnd = findHtmlTagEnd(source, closingStart)
                            index = if (closingEnd >= 0) closingEnd + 1 else source.length
                        }
                        continue
                    }
                    when {
                        tagName == "br" || tagName == "hr" -> appendTextLineBreak(output)
                        closing && isHtmlBlockTag(tagName) -> appendTextLineBreak(output)
                    }
                    index = tagEnd + 1
                }
                '&' -> {
                    val semicolon = source.indexOf(';', index + 1)
                    if (semicolon > index + 1 && semicolon - index <= 12) {
                        val decoded = decodeHtmlEntity(source.substring(index + 1, semicolon))
                        if (decoded != null) {
                            output.append(decoded)
                            index = semicolon + 1
                            continue
                        }
                    }
                    output.append('&')
                    index++
                }
                '\r' -> {
                    if (index + 1 < source.length && source[index + 1] == '\n') index++
                    appendTextLineBreak(output)
                    index++
                }
                else -> {
                    output.append(source[index])
                    index++
                }
            }
        }
        while (output.isNotEmpty() && output[output.length - 1].isWhitespace()) {
            output.setLength(output.length - 1)
        }
        return output.toString()
    }

    private fun findHtmlTagEnd(source: String, start: Int): Int {
        var quote: Char? = null
        var index = start + 1
        while (index < source.length) {
            val current = source[index]
            if (quote != null) {
                if (current == quote) quote = null
            } else {
                when (current) {
                    '\'', '"' -> quote = current
                    '>' -> return index
                }
            }
            index++
        }
        return -1
    }

    private fun isHtmlBlockTag(tagName: String): Boolean = when (tagName) {
        "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "section", "article" -> true
        else -> false
    }

    private fun appendTextLineBreak(output: StringBuilder) {
        if (output.isNotEmpty() && output[output.length - 1] != '\n') output.append('\n')
    }

    private fun decodeHtmlEntity(entity: String): String? {
        if (entity.startsWith("#")) {
            val hexadecimal = entity.startsWith("#x", ignoreCase = true)
            val digits = entity.substring(if (hexadecimal) 2 else 1)
            val codePoint = digits.toIntOrNull(if (hexadecimal) 16 else 10) ?: return null
            if (codePoint <= 0 || codePoint > Character.MAX_CODE_POINT ||
                codePoint in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code
            ) return null
            return String(Character.toChars(codePoint))
        }
        return when (entity.lowercase(Locale.ROOT)) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> "\u00A0"
            "tab" -> "\t"
            "ensp" -> "\u2002"
            "emsp" -> "\u2003"
            "ndash" -> "\u2013"
            "mdash" -> "\u2014"
            "lsquo" -> "\u2018"
            "rsquo" -> "\u2019"
            "ldquo" -> "\u201C"
            "rdquo" -> "\u201D"
            else -> null
        }
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")

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

}