package com.fz.office.fonts

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Private, on-device Hindi font library.
 *
 * Bundled fonts remain APK assets. Imported fonts are validated and kept only in
 * app-private storage; ZIP entry paths are never used as destination paths.
 */
object FontManager {
    private const val PREFS = "fz_hindi_fonts"
    private const val KEY_DEFAULT = "default_family"
    private const val MAX_CUSTOM_FONT_BYTES = 12 * 1024 * 1024
    private const val MAX_ARCHIVE_FONTS = 1200
    private const val MAX_ARCHIVE_EXPANDED_BYTES = 180L * 1024L * 1024L
    private const val MAX_FONT_NAME_TABLE_BYTES = 1024 * 1024

    const val HINDI_SAMPLE =
        "हिन्दी भाषा — क्षत्रिय, प्रार्थना, दृष्टि, विद्यालय, श्रेय, कृपा, " +
            "क़लम, ज़रूरी, फ़र्क़, हिंदी और भारत।"
    const val SHAPING_SAMPLE =
        "कि की कु कू कृ के कै को कौ | र्क र्क्ष प्र त्र श्र स्त्र ज्ञ क्ष | " +
            "क़ ख़ ग़ ज़ ड़ ढ़ फ़ | अँ अं अः ँ ं ः"
    const val JOINER_SAMPLE =
        "क्‍ष (ZWJ)  ·  क्‌ष (ZWNJ)  ·  क्ष  ·  श्र  ·  त्र"
    const val NUMERAL_SAMPLE =
        "० १ २ ३ ४ ५ ६ ७ ८ ९   ₹ १२,५००.००   ॥"

    class FontRecord(
        val name: String,
        val category: String,
        val description: String,
        val cssFamily: String,
        val assetFile: String?,
        val customFile: File?,
        val bundled: Boolean,
        val fontWeight: Int,
        val fontStyle: String,
        val fullName: String
    ) {
        override fun toString(): String = name
    }

    // Ignore font copies left by older preview builds; assets remain bundled in the APK.
    private val bundledFontAssetCopies = setOf(
        "NotoSansDevanagari-VF.ttf", "NotoSerifDevanagari-VF.ttf",
        "Mukta-Regular.ttf", "Mukta-Bold.ttf", "Hind-Regular.ttf", "Hind-Bold.ttf",
        "TiroDevanagariHindi-Regular.ttf", "TiroDevanagariHindi-Italic.ttf",
        "Kalam-Regular.ttf", "Sahitya-Regular.ttf", "Sahitya-Bold.ttf",
        "Martel-Regular.ttf", "Baloo2-VF.ttf", "Khand-Regular.ttf",
        "Gotu-Regular.ttf", "Glegoo-Regular.ttf"
    )

    private val builtIn: List<FontRecord> = listOf(
        bundled(
            "Noto Sans Devanagari", "Sans serif · Variable",
            "Recommended default for everyday Hindi documents.", "NotoSansDevanagari-VF.ttf"
        ),
        bundled(
            "Noto Serif Devanagari", "Serif · Variable",
            "Traditional long-form reading and formal documents.", "NotoSerifDevanagari-VF.ttf"
        ),
        bundled(
            "Mukta", "Sans serif · Humanist",
            "Clean multi-weight family for documents and headings.", "Mukta-Regular.ttf"
        ),
        bundled(
            "Hind", "Sans serif · Text",
            "Compact and readable text family.", "Hind-Regular.ttf"
        ),
        bundled(
            "Tiro Devanagari Hindi", "Serif · Editorial",
            "Designed with Hindi conjunct forms and book typography in mind.",
            "TiroDevanagariHindi-Regular.ttf"
        ),
        bundled(
            "Kalam", "Handwriting",
            "Handwritten look for informal notes and headings.", "Kalam-Regular.ttf"
        ),
        bundled(
            "Sahitya", "Serif · Book",
            "Literary and book-style Hindi typography.", "Sahitya-Regular.ttf"
        ),
        bundled(
            "Martel", "Serif · Text",
            "Serif family for headings and reading.", "Martel-Regular.ttf"
        ),
        bundled(
            "Baloo 2", "Rounded · Display · Variable",
            "Rounded display style for titles and creative documents.", "Baloo2-VF.ttf"
        ),
        bundled(
            "Khand", "Display · Condensed",
            "Strong condensed Devanagari for short headings.", "Khand-Regular.ttf"
        ),
        bundled(
            "Gotu", "Sans serif · Rounded",
            "Distinctive rounded Devanagari text style.", "Gotu-Regular.ttf"
        ),
        bundled(
            "Glegoo", "Serif · Display",
            "Bold, high-contrast style for titles and short text.", "Glegoo-Regular.ttf"
        )
    )

    private fun bundled(
        name: String,
        category: String,
        description: String,
        file: String
    ) = FontRecord(name, category, description, name, file, null, true, 400, "normal", name)

    fun getBundledFonts(): List<FontRecord> = ArrayList(builtIn)

    private fun isBundledFontAssetCopy(fileName: String?): Boolean =
        fileName != null && bundledFontAssetCopies.any { it.equals(fileName, ignoreCase = true) }

    fun getAvailableFonts(context: Context): List<FontRecord> {
        val result = getBundledFonts()
        val directory = customDirectory(context)
        val files = directory.listFiles()
        if (files != null) {
            files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            for (file in files) {
                if (!file.isFile || !isSupportedExtension(file.name) ||
                    isBundledFontAssetCopy(file.name)
                ) continue

                val metadata = readFontMetadataCached(file)
                val family = metadata.family.ifEmpty { customCssFamily(file) }
                val style = metadata.subfamily.ifEmpty { "Regular" }
                val full = metadata.fullName.ifEmpty { family + " " + style }
                val bundledFiles = preferences(context).getStringSet(
                    "bundled_legacy_pack_files", emptySet()
                ) ?: emptySet()
                val bundledLegacy = bundledFiles.contains(file.name)

                result.add(
                    FontRecord(
                        full + "  ·  " + file.name,
                        (if (bundledLegacy) "Bundled · " else "Imported · ") + style,
                        if (bundledLegacy) {
                            "Bundled legacy Hindi font. Installed and maintained by FZ Work Suite."
                        } else {
                            "Font family: " + family +
                                ". Stored privately on this device; redistribution rights are your responsibility."
                        },
                        family, null, file, bundledLegacy, metadata.weight,
                        if (metadata.italic) "italic" else "normal", full
                    )
                )
            }
        }
        return result
    }

    fun getDefaultFont(context: Context): String {
        val value = preferences(context).getString(KEY_DEFAULT, "Noto Sans Devanagari")
        return if (value.isNullOrBlank()) "Noto Sans Devanagari" else value
    }

    fun setDefaultFont(context: Context, record: FontRecord) {
        preferences(context).edit().putString(KEY_DEFAULT, record.cssFamily).apply()
    }

    fun findByFamily(context: Context, family: String?): FontRecord {
        val wanted = normalizeFamily(family)
        if (wanted.isEmpty()) return builtIn[0]

        // Most documents use one of the APK fonts. Resolve those without scanning
        // every user-imported TTF/OTF in the private font directory.
        builtIn.firstOrNull { normalizeFamily(it.cssFamily) == wanted }?.let { return it }
        getAvailableFonts(context).firstOrNull {
            normalizeFamily(it.cssFamily) == wanted
        }?.let { return it }
        return builtIn[0]
    }

    fun getBundledCss(): String {
        val css = StringBuilder()
        face(css, "Noto Sans Devanagari", "NotoSansDevanagari-VF.ttf", "100 900", "normal")
        face(css, "Noto Serif Devanagari", "NotoSerifDevanagari-VF.ttf", "100 900", "normal")
        face(css, "Mukta", "Mukta-Regular.ttf", "400", "normal")
        face(css, "Mukta", "Mukta-Bold.ttf", "700", "normal")
        face(css, "Hind", "Hind-Regular.ttf", "400", "normal")
        face(css, "Hind", "Hind-Bold.ttf", "700", "normal")
        face(css, "Tiro Devanagari Hindi", "TiroDevanagariHindi-Regular.ttf", "400", "normal")
        face(css, "Tiro Devanagari Hindi", "TiroDevanagariHindi-Italic.ttf", "400", "italic")
        face(css, "Kalam", "Kalam-Regular.ttf", "400", "normal")
        face(css, "Sahitya", "Sahitya-Regular.ttf", "400", "normal")
        face(css, "Sahitya", "Sahitya-Bold.ttf", "700", "normal")
        face(css, "Martel", "Martel-Regular.ttf", "400", "normal")
        face(css, "Baloo 2", "Baloo2-VF.ttf", "100 900", "normal")
        face(css, "Khand", "Khand-Regular.ttf", "400", "normal")
        face(css, "Gotu", "Gotu-Regular.ttf", "400", "normal")
        face(css, "Glegoo", "Glegoo-Regular.ttf", "400", "normal")
        return css.toString()
    }

    private fun face(
        css: StringBuilder,
        family: String,
        file: String,
        weight: String,
        style: String
    ) {
        css.append("@font-face{font-family:")
            .append(cssQuote(family))
            .append(";src:url('fonts/").append(file).append("') format('truetype');")
            .append("font-weight:").append(weight).append(";font-style:").append(style)
            .append(";font-display:swap;}")
    }

    @Throws(IOException::class)
    fun getCustomFontCss(record: FontRecord?): String {
        if (record == null || record.bundled || record.customFile == null) return ""
        val bytes = readFile(record.customFile)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val format = if (record.customFile.name.lowercase(Locale.ROOT).endsWith(".otf")) {
            "opentype"
        } else {
            "truetype"
        }
        return "@font-face{font-family:" + cssQuote(record.cssFamily) +
            ";src:url(data:font/" + format + ";base64," + encoded + ") format('" +
            format + "');font-weight:" + record.fontWeight +
            ";font-style:" + record.fontStyle + ";font-display:swap;}"
    }

    fun loadTypeface(context: Context, record: FontRecord): Typeface {
        return try {
            if (record.bundled) {
                Typeface.createFromAsset(context.assets, "writer/fonts/" + record.assetFile)
            } else {
                Typeface.createFromFile(record.customFile)
            }
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    @Throws(Exception::class)
    fun importFont(context: Context, uri: Uri?, displayName: String): File {
        if (uri == null) throw IOException("No font file was selected.")
        val extension = extension(displayName)
        if (extension != ".ttf" && extension != ".otf") {
            throw IOException("Only TTF and OTF font files are supported.")
        }

        val directory = customDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private font folder.")
        }

        var base = safeFileName(displayName)
        if (base.length < extension.length) base = "Imported-Devanagari-Font"
        base = base.substring(0, base.length - extension.length)
        var destination = File(directory, base + extension)
        var suffix = 2
        while (destination.exists()) {
            destination = File(directory, base + "-" + suffix + extension)
            suffix++
        }

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not read this font file.")
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    val prefix = ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val count = source.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > MAX_CUSTOM_FONT_BYTES) {
                            throw IOException("Font file is too large. Maximum allowed size is 12 MB.")
                        }
                        if (prefix.size() < 16) {
                            val keep = minOf(count, 16 - prefix.size())
                            prefix.write(buffer, 0, keep)
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    if (!hasFontSignature(prefix.toByteArray())) {
                        throw IOException("This file does not have a supported TTF/OTF signature.")
                    }
                }
            }
        } catch (exception: Exception) {
            destination.delete()
            throw exception
        }

        try {
            Typeface.createFromFile(destination)
        } catch (_: Exception) {
            destination.delete()
            throw IOException(
                "Android could not read this font. The file may be damaged or unsupported."
            )
        }
        return destination
    }

    class ImportResult {
        var imported: Int = 0
        var duplicates: Int = 0
        var invalid: Int = 0
        var ignored: Int = 0
        var bytesImported: Long = 0L

        fun totalFontEntries(): Int = imported + duplicates + invalid
    }

    private data class FontMetadata(
        val family: String,
        val subfamily: String,
        val fullName: String,
        val weight: Int,
        val italic: Boolean
    )

    private data class CachedFontMetadata(
        val lastModified: Long,
        val length: Long,
        val metadata: FontMetadata
    )

    private val fontMetadataCacheLock = Any()
    private val fontMetadataCache = HashMap<String, CachedFontMetadata>()

    /**
     * Imports every valid TTF/OTF in a user-selected ZIP into app-private storage.
     * ZIP paths are discarded; no archive entry can write outside the font folder.
     */
    @Throws(Exception::class)
    fun importFontArchive(context: Context, uri: Uri?): ImportResult {
        if (uri == null) throw IOException("No font archive was selected.")
        val raw = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not read the selected ZIP archive.")
        return importFontArchiveStream(context, raw)
    }

    /**
     * Installs the project-supplied legacy Hindi font pack from APK assets.
     * The archive is processed directly from assets and fonts remain in app-private storage.
     */
    @Synchronized
    @Throws(Exception::class)
    fun importBundledFontArchives(context: Context): ImportResult {
        val archivePath = "font-packs/HINDI FONTS.zip"
        val versionKey = "bundled_legacy_pack_version"
        val filesKey = "bundled_legacy_pack_files"
        val prefs = preferences(context)
        val appVersion = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (exception: Exception) {
            throw IOException("Could not determine the installed app version.", exception)
        }
        if (prefs.getInt(versionKey, -1) == appVersion) return ImportResult()

        val directory = customDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private font folder.")
        }

        // Remove only files installed by the previous bundled pack. User-imported fonts stay untouched.
        val oldNames = prefs.getStringSet(filesKey, emptySet()) ?: emptySet()
        for (name in oldNames) {
            val oldFile = File(directory, name)
            if (oldFile.isFile && oldFile.parentFile == directory) oldFile.delete()
        }

        val before = HashSet<String>()
        directory.listFiles()?.forEach { file ->
            if (file.isFile) before.add(file.name)
        }

        val raw = context.assets.open(archivePath)
        val result = importFontArchiveStream(context, raw)

        val installed = HashSet<String>()
        directory.listFiles()?.forEach { file ->
            if (file.isFile && isSupportedExtension(file.name) && !before.contains(file.name)) {
                installed.add(file.name)
            }
        }
        prefs.edit().putStringSet(filesKey, installed)
            .putInt(versionKey, appVersion).apply()
        return result
    }

    @Throws(Exception::class)
    private fun importFontArchiveStream(context: Context, raw: InputStream): ImportResult {
        val directory = customDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            raw.close()
            throw IOException("Could not create the private font folder.")
        }

        val result = ImportResult()
        var expanded = 0L
        var entriesSeen = 0
        val existingHashes = HashSet<String>()
        directory.listFiles()?.forEach { file ->
            if (file.isFile && isSupportedExtension(file.name)) {
                existingHashes.add(sha256(readFile(file)))
            }
        }

        raw.use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entriesSeen++
                    if (entriesSeen > 20000) {
                        throw IOException("Archive contains too many entries.")
                    }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val entryName = entry.name ?: ""
                    var baseName = entryName.replace('\\', '/')
                    val slash = baseName.lastIndexOf('/')
                    if (slash >= 0) baseName = baseName.substring(slash + 1)

                    if (!isSupportedExtension(baseName)) {
                        result.ignored++
                        expanded += skipZipEntryLimited(zip, MAX_ARCHIVE_EXPANDED_BYTES - expanded)
                        zip.closeEntry()
                        continue
                    }

                    if (result.totalFontEntries() >= MAX_ARCHIVE_FONTS) {
                        throw IOException("Font archive exceeds " + MAX_ARCHIVE_FONTS + " font files.")
                    }

                    val bytes = readZipEntryLimited(zip, MAX_CUSTOM_FONT_BYTES)
                    zip.closeEntry()
                    expanded += bytes.size.toLong()
                    if (expanded > MAX_ARCHIVE_EXPANDED_BYTES) {
                        throw IOException("Expanded font data exceeds the 180 MB safety limit.")
                    }

                    if (!hasFontSignature(bytes)) {
                        result.invalid++
                        continue
                    }

                    try {
                        val stored = storeFontBytes(directory, bytes, baseName, existingHashes)
                        if (stored) {
                            result.imported++
                            result.bytesImported += bytes.size.toLong()
                        } else {
                            result.duplicates++
                        }
                    } catch (_: Exception) {
                        result.invalid++
                    }
                }
            }
        }
        return result
    }

    /**
     * Builds CSS for several requested families using one directory scan.
     * DOCX font tables can declare many unused fonts; scanning the same folder
     * once per declaration made document opening increasingly expensive.
     */
    @Throws(IOException::class)
    fun getCustomFontCssForFamilies(context: Context, families: Iterable<String>?): String {
        val wanted = HashSet<String>()
        families?.forEach { family ->
            val normalized = normalizeFamily(family)
            if (normalized.isNotEmpty()) wanted.add(normalized)
        }
        if (wanted.isEmpty()) return ""

        builtIn.forEach { wanted.remove(normalizeFamily(it.cssFamily)) }
        if (wanted.isEmpty()) return ""

        val directory = customDirectory(context)
        val files = directory.listFiles()
        if (files.isNullOrEmpty()) return ""
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val css = StringBuilder()
        for (file in files) {
            if (!file.isFile || !isSupportedExtension(file.name)) continue
            val metadata = readFontMetadataCached(file)
            if (!wanted.contains(normalizeFamily(metadata.family))) continue
            val bytes = readFile(file)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val format = if (extension(file.name) == ".otf") "opentype" else "truetype"
            css.append("@font-face{font-family:")
                .append(cssQuote(metadata.family))
                .append(";src:url(data:font/").append(format).append(";base64,")
                .append(encoded).append(") format('").append(format).append("');")
                .append("font-weight:").append(metadata.weight)
                .append(";font-style:").append(if (metadata.italic) "italic" else "normal")
                .append(";font-display:swap;}")
        }
        return css.toString()
    }

    @Throws(IOException::class)
    fun getCustomFontCssForFamily(context: Context, family: String): String =
        getCustomFontCssForFamilies(context, listOf(family))

    /**
     * Drains an ignored ZIP entry without buffering it in memory, while still
     * enforcing the total expanded-archive limit.
     */
    @Throws(IOException::class)
    private fun skipZipEntryLimited(input: InputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) {
                throw IOException("Expanded ZIP data exceeds the 180 MB safety limit.")
            }
        }
        return total
    }

    @Throws(IOException::class)
    private fun readZipEntryLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) throw IOException("Font file exceeds the size limit.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    @Throws(Exception::class)
    private fun storeFontBytes(
        directory: File,
        bytes: ByteArray,
        originalName: String,
        existingHashes: MutableSet<String>
    ): Boolean {
        val hash = sha256(bytes)
        if (existingHashes.contains(hash)) return false

        val extension = extension(originalName)
        val safe = safeFileName(originalName)
        val base = if (safe.length > extension.length) {
            safe.substring(0, safe.length - extension.length)
        } else {
            "Imported-Font"
        }
        var destination = File(directory, base + extension)
        var suffix = 2
        while (destination.exists()) {
            destination = File(directory, base + "-" + suffix + extension)
            suffix++
        }

        FileOutputStream(destination).use { output ->
            output.write(bytes)
            output.flush()
        }
        try {
            Typeface.createFromFile(destination)
            existingHashes.add(hash)
        } catch (exception: Exception) {
            destination.delete()
            throw IOException("Android cannot read font " + originalName, exception)
        }
        return true
    }

    @Throws(Exception::class)
    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val value = StringBuilder()
        for (byte in digest) {
            value.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff))
        }
        return value.toString()
    }

    private fun normalizeFamily(value: String?): String =
        value?.lowercase(Locale.ROOT)?.replace(Regex("[^a-z0-9]"), "") ?: ""

    private fun readFontMetadataCached(file: File): FontMetadata {
        val path = file.absolutePath
        val lastModified = file.lastModified()
        val length = file.length()
        synchronized(fontMetadataCacheLock) {
            val cached = fontMetadataCache[path]
            if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                return cached.metadata
            }
        }

        val metadata = readFontMetadata(file)
        synchronized(fontMetadataCacheLock) {
            if (fontMetadataCache.size >= MAX_ARCHIVE_FONTS * 4) {
                fontMetadataCache.clear()
            }
            fontMetadataCache[path] = CachedFontMetadata(lastModified, length, metadata)
        }
        return metadata
    }

    private fun invalidateFontMetadata(file: File?) {
        if (file == null) return
        synchronized(fontMetadataCacheLock) {
            fontMetadataCache.remove(file.absolutePath)
        }
    }

    private fun readFontMetadata(file: File): FontMetadata {
        // A font's family/style names live in the small OpenType "name" table.
        // Do not read the entire TTF/OTF file just to inspect that table.
        return try {
            RandomAccessFile(file, "r").use { input ->
                val fileLength = input.length()
                if (fileLength < 12) return emptyMetadata(file)

                val header = ByteArray(12)
                input.readFully(header)
                val headerData = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                val tables = unsignedShort(headerData, 4)
                if (tables <= 0 || tables > 4096 || 12L + tables * 16L > fileLength) {
                    return emptyMetadata(file)
                }

                var nameOffset = -1L
                var nameLength = 0L
                val tableRecord = ByteArray(16)
                for (index in 0 until tables) {
                    input.seek(12L + index * 16L)
                    input.readFully(tableRecord)
                    val tag = String(tableRecord, 0, 4, StandardCharsets.US_ASCII)
                    if (tag == "name") {
                        val recordData = ByteBuffer.wrap(tableRecord).order(ByteOrder.BIG_ENDIAN)
                        nameOffset = unsignedInt(recordData, 8)
                        nameLength = unsignedInt(recordData, 12)
                        break
                    }
                }

                if (nameOffset < 0 || nameLength < 6 ||
                    nameLength > MAX_FONT_NAME_TABLE_BYTES ||
                    nameOffset > fileLength - nameLength
                ) {
                    return emptyMetadata(file)
                }

                val bytes = ByteArray(nameLength.toInt())
                input.seek(nameOffset)
                input.readFully(bytes)
                val data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val count = unsignedShort(data, 2)
                val stringOffset = unsignedShort(data, 4)
                val recordsStart = 6
                var family16 = ""
                var family1 = ""
                var sub17 = ""
                var sub2 = ""
                var full4 = ""

                for (index in 0 until count) {
                    val at = recordsStart + index * 12
                    if (at + 12 > bytes.size) break
                    val platform = unsignedShort(data, at)
                    val language = unsignedShort(data, at + 4)
                    val nameId = unsignedShort(data, at + 6)
                    val length = unsignedShort(data, at + 8)
                    val offset = unsignedShort(data, at + 10)
                    val valueStart = stringOffset.toLong() + offset
                    if (valueStart < stringOffset || valueStart + length > bytes.size) continue
                    val decoded = decodeName(bytes, valueStart.toInt(), length, platform).trim()
                    if (decoded.isEmpty()) continue
                    val preferred = platform == 3 && (language == 0x0409 || language == 0)
                    when (nameId) {
                        16 -> if (family16.isEmpty() || preferred) family16 = decoded
                        1 -> if (family1.isEmpty() || preferred) family1 = decoded
                        17 -> if (sub17.isEmpty() || preferred) sub17 = decoded
                        2 -> if (sub2.isEmpty() || preferred) sub2 = decoded
                        4 -> if (full4.isEmpty() || preferred) full4 = decoded
                    }
                }

                val family = family16.ifEmpty { family1 }.ifEmpty { customCssFamily(file) }
                val style = sub17.ifEmpty { sub2 }.ifEmpty { "Regular" }
                val full = full4.ifEmpty { family + " " + style }
                val lower = style.lowercase(Locale.ROOT)
                val weight = when {
                    lower.contains("black") || lower.contains("heavy") -> 900
                    lower.contains("bold") -> 700
                    lower.contains("semibold") || lower.contains("demi") -> 600
                    lower.contains("medium") -> 500
                    lower.contains("thin") || lower.contains("light") -> 300
                    else -> 400
                }
                val italic = lower.contains("italic") || lower.contains("oblique")
                FontMetadata(family, style, full, weight, italic)
            }
        } catch (_: Exception) {
            emptyMetadata(file)
        }
    }

    private fun emptyMetadata(file: File): FontMetadata {
        val family = customCssFamily(file)
        return FontMetadata(family, "Regular", family, 400, false)
    }

    private fun unsignedShort(data: ByteBuffer, offset: Int): Int =
        data.getShort(offset).toInt() and 0xffff

    private fun unsignedInt(data: ByteBuffer, offset: Int): Long =
        data.getInt(offset).toLong() and 0xffffffffL

    private fun decodeName(data: ByteArray, start: Int, length: Int, platform: Int): String {
        return try {
            if (platform == 0 || platform == 3) {
                String(data, start, length, StandardCharsets.UTF_16BE)
            } else {
                String(data, start, length, Charset.forName("windows-1252"))
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun removeCustomFont(record: FontRecord?) {
        if (record != null && !record.bundled && record.customFile != null) {
            record.customFile.delete()
            invalidateFontMetadata(record.customFile)
        }
    }

    fun removeCustomFont(context: Context, record: FontRecord?) {
        removeCustomFont(record)
    }

    private fun hasFontSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val tag = String(bytes, 0, 4, StandardCharsets.US_ASCII)
        return tag == "OTTO" || tag == "true" || tag == "typ1" ||
            (bytes[0] == 0.toByte() && bytes[1] == 1.toByte() &&
                bytes[2] == 0.toByte() && bytes[3] == 0.toByte())
    }

    @Throws(IOException::class)
    private fun readFile(file: File): ByteArray {
        FileInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        }
    }

    private fun customDirectory(context: Context): File {
        // Imported fonts stay in this app-private folder.
        // This is app-private storage, not shared public storage.
        return File(File(context.applicationInfo.dataDir, "user"), "fonts")
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun customCssFamily(file: File): String {
        var name = displayName(file.name).replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
        if (name.isEmpty()) name = "Imported Font"
        return name
    }

    private fun displayName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        return base.replace('_', ' ').replace('-', ' ')
    }

    private fun safeFileName(value: String?): String {
        if (value == null) return "Imported-Devanagari-Font"
        val clean = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (clean.length > 80) clean.substring(clean.length - 80) else clean
    }

    private fun extension(name: String?): String {
        if (name == null) return ""
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "" else name.substring(dot).lowercase(Locale.ROOT)
    }

    private fun isSupportedExtension(value: String?): Boolean {
        val ext = extension(value)
        return ext == ".ttf" || ext == ".otf"
    }

    private fun cssQuote(value: String): String =
        try {
            JSONObject.quote(value)
        } catch (_: Exception) {
            "\"" + value.replace("\"", "") + "\""
        }
}
