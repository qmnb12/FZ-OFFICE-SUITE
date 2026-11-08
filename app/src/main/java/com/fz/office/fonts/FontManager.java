package com.fz.office.fonts;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FontManager {
    private static final String PREFS = "fz_hindi_fonts";
    private static final String KEY_DEFAULT = "default_family";
    private static final int MAX_CUSTOM_FONT_BYTES = 12 * 1024 * 1024;
    private static final int MAX_ARCHIVE_FONTS = 1200;
    private static final long MAX_ARCHIVE_EXPANDED_BYTES = 180L * 1024L * 1024L;

    public static final String HINDI_SAMPLE =
            "हिन्दी भाषा — क्षत्रिय, प्रार्थना, दृष्टि, विद्यालय, श्रेय, कृपा, "
            + "क़लम, ज़रूरी, फ़र्क़, हिंदी और भारत।";
    public static final String SHAPING_SAMPLE =
            "कि की कु कू कृ के कै को कौ | र्क र्क्ष प्र त्र श्र स्त्र ज्ञ क्ष | "
            + "क़ ख़ ग़ ज़ ड़ ढ़ फ़ | अँ अं अः ँ ं ः";
    public static final String JOINER_SAMPLE =
            "क्‍ष (ZWJ)  ·  क्‌ष (ZWNJ)  ·  क्ष  ·  श्र  ·  त्र";
    public static final String NUMERAL_SAMPLE =
            "० १ २ ३ ४ ५ ६ ७ ८ ९   ₹ १२,५००.००   ॥";

    public static final class FontRecord {
        public final String name;
        public final String category;
        public final String description;
        public final String cssFamily;
        public final String assetFile;
        public final File customFile;
        public final boolean bundled;
        public final int fontWeight;
        public final String fontStyle;
        public final String fullName;

        FontRecord(String name, String category, String description,
                   String cssFamily, String assetFile, File customFile, boolean bundled,
                   int fontWeight, String fontStyle, String fullName) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.cssFamily = cssFamily;
            this.assetFile = assetFile;
            this.customFile = customFile;
            this.bundled = bundled;
            this.fontWeight = fontWeight;
            this.fontStyle = fontStyle;
            this.fullName = fullName;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<FontRecord> BUILT_IN = Collections.unmodifiableList(Arrays.asList(
        bundled("Noto Sans Devanagari", "Sans serif · Variable",
                "Recommended default for everyday Hindi documents.", "NotoSansDevanagari-VF.ttf"),
        bundled("Noto Serif Devanagari", "Serif · Variable",
                "Traditional long-form reading and formal documents.", "NotoSerifDevanagari-VF.ttf"),
        bundled("Mukta", "Sans serif · Humanist",
                "Clean multi-weight family for documents and headings.", "Mukta-Regular.ttf"),
        bundled("Hind", "Sans serif · Text",
                "Compact and readable text family.", "Hind-Regular.ttf"),
        bundled("Tiro Devanagari Hindi", "Serif · Editorial",
                "Designed with Hindi conjunct forms and book typography in mind.", "TiroDevanagariHindi-Regular.ttf"),
        bundled("Kalam", "Handwriting",
                "Handwritten look for informal notes and headings.", "Kalam-Regular.ttf"),
        bundled("Sahitya", "Serif · Book",
                "Literary and book-style Hindi typography.", "Sahitya-Regular.ttf"),
        bundled("Martel", "Serif · Text",
                "Serif family for headings and reading.", "Martel-Regular.ttf"),
        bundled("Baloo 2", "Rounded · Display · Variable",
                "Rounded display style for titles and creative documents.", "Baloo2-VF.ttf"),
        bundled("Khand", "Display · Condensed",
                "Strong condensed Devanagari for short headings.", "Khand-Regular.ttf"),
        bundled("Gotu", "Sans serif · Rounded",
                "Distinctive rounded Devanagari text style.", "Gotu-Regular.ttf"),
        bundled("Glegoo", "Serif · Display",
                "Bold, high-contrast style for titles and short text.", "Glegoo-Regular.ttf")
    ));

    private FontManager() { }

    private static FontRecord bundled(String name, String category, String description, String file) {
        return new FontRecord(name, category, description, name, file, null, true,
                400, "normal", name);
    }

    public static List<FontRecord> getBundledFonts() {
        return new ArrayList<>(BUILT_IN);
    }

    public static List<FontRecord> getAvailableFonts(Context context) {
        List<FontRecord> result = getBundledFonts();
        File directory = customDirectory(context);
        File[] files = directory.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                if (!file.isFile() || !isSupportedExtension(file.getName())) continue;
                FontMetadata metadata = readFontMetadata(file);
                String family = metadata.family.isEmpty() ? customCssFamily(file) : metadata.family;
                String style = metadata.subfamily.isEmpty() ? "Regular" : metadata.subfamily;
                String full = metadata.fullName.isEmpty() ? family + " " + style : metadata.fullName;
                result.add(new FontRecord(
                        full + "  ·  " + file.getName(), "Imported · " + style,
                        "Font family: " + family + ". Stored privately on this device; redistribution rights are your responsibility.",
                        family, null, file, false, metadata.weight, metadata.italic ? "italic" : "normal", full
                ));
            }
        }
        return result;
    }

    public static String getDefaultFont(Context context) {
        String value = preferences(context).getString(KEY_DEFAULT, "Noto Sans Devanagari");
        if (value == null || value.trim().isEmpty()) return "Noto Sans Devanagari";
        return value;
    }

    public static void setDefaultFont(Context context, FontRecord record) {
        preferences(context).edit().putString(KEY_DEFAULT, record.cssFamily).apply();
    }

    public static FontRecord findByFamily(Context context, String family) {
        String wanted = normalizeFamily(family);
        for (FontRecord record : getAvailableFonts(context)) {
            if (normalizeFamily(record.cssFamily).equals(wanted)) return record;
        }
        return getBundledFonts().get(0);
    }

    public static String getBundledCss() {
        StringBuilder css = new StringBuilder();
        face(css, "Noto Sans Devanagari", "NotoSansDevanagari-VF.ttf", "100 900", "normal");
        face(css, "Noto Serif Devanagari", "NotoSerifDevanagari-VF.ttf", "100 900", "normal");
        face(css, "Mukta", "Mukta-Regular.ttf", "400", "normal");
        face(css, "Mukta", "Mukta-Bold.ttf", "700", "normal");
        face(css, "Hind", "Hind-Regular.ttf", "400", "normal");
        face(css, "Hind", "Hind-Bold.ttf", "700", "normal");
        face(css, "Tiro Devanagari Hindi", "TiroDevanagariHindi-Regular.ttf", "400", "normal");
        face(css, "Tiro Devanagari Hindi", "TiroDevanagariHindi-Italic.ttf", "400", "italic");
        face(css, "Kalam", "Kalam-Regular.ttf", "400", "normal");
        face(css, "Sahitya", "Sahitya-Regular.ttf", "400", "normal");
        face(css, "Sahitya", "Sahitya-Bold.ttf", "700", "normal");
        face(css, "Martel", "Martel-Regular.ttf", "400", "normal");
        face(css, "Baloo 2", "Baloo2-VF.ttf", "100 900", "normal");
        face(css, "Khand", "Khand-Regular.ttf", "400", "normal");
        face(css, "Gotu", "Gotu-Regular.ttf", "400", "normal");
        face(css, "Glegoo", "Glegoo-Regular.ttf", "400", "normal");
        return css.toString();
    }

    private static void face(StringBuilder css, String family, String file,
                             String weight, String style) {
        css.append("@font-face{font-family:")
                .append(cssQuote(family))
                .append(";src:url('fonts/").append(file).append("') format('truetype');")
                .append("font-weight:").append(weight).append(";font-style:").append(style)
                .append(";font-display:swap;}");
    }

    public static String getCustomFontCss(FontRecord record) throws IOException {
        if (record == null || record.bundled || record.customFile == null) return "";
        byte[] bytes = readFile(record.customFile);
        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String format = record.customFile.getName().toLowerCase(Locale.ROOT).endsWith(".otf")
                ? "opentype" : "truetype";
        return "@font-face{font-family:" + cssQuote(record.cssFamily)
                + ";src:url(data:font/" + format + ";base64," + encoded + ") format('"
                + format + "');font-weight:" + record.fontWeight
                + ";font-style:" + record.fontStyle + ";font-display:swap;}";
    }

    public static Typeface loadTypeface(Context context, FontRecord record) {
        try {
            if (record.bundled) return Typeface.createFromAsset(
                    context.getAssets(), "writer/fonts/" + record.assetFile);
            return Typeface.createFromFile(record.customFile);
        } catch (Exception ignored) {
            return Typeface.DEFAULT;
        }
    }

    public static File importFont(Context context, Uri uri, String displayName) throws Exception {
        if (uri == null) throw new IOException("No font file was selected.");
        String extension = extension(displayName);
        if (!".ttf".equals(extension) && !".otf".equals(extension)) {
            throw new IOException("Only TTF and OTF font files are supported.");
        }
        File directory = customDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the private font folder.");
        }

        String base = safeFileName(displayName);
        if (base.length() < extension.length()) base = "Imported-Devanagari-Font";
        base = base.substring(0, base.length() - extension.length());
        File destination = new File(directory, base + extension);
        int suffix = 2;
        while (destination.exists()) {
            destination = new File(directory, base + "-" + suffix + extension);
            suffix++;
        }

        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("Could not read this font file.");
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            ByteArrayOutputStream prefix = new ByteArrayOutputStream();
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_CUSTOM_FONT_BYTES) {
                    throw new IOException("Font file is too large. Maximum allowed size is 12 MB.");
                }
                if (prefix.size() < 16) {
                    int keep = Math.min(count, 16 - prefix.size());
                    prefix.write(buffer, 0, keep);
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            if (!hasFontSignature(prefix.toByteArray())) {
                throw new IOException("This file does not have a supported TTF/OTF signature.");
            }
        } catch (Exception exception) {
            destination.delete();
            throw exception;
        }

        try {
            Typeface.createFromFile(destination);
        } catch (Exception exception) {
            destination.delete();
            throw new IOException("Android could not read this font. The file may be damaged or unsupported.");
        }
        return destination;
    }


    public static final class ImportResult {
        public int imported;
        public int duplicates;
        public int invalid;
        public int ignored;
        public long bytesImported;

        public int totalFontEntries() {
            return imported + duplicates + invalid;
        }
    }

    private static final class FontMetadata {
        final String family;
        final String subfamily;
        final String fullName;
        final int weight;
        final boolean italic;

        FontMetadata(String family, String subfamily, String fullName, int weight, boolean italic) {
            this.family = family;
            this.subfamily = subfamily;
            this.fullName = fullName;
            this.weight = weight;
            this.italic = italic;
        }
    }

    /**
     * Imports every valid TTF/OTF in a user-selected ZIP into app-private storage.
     * ZIP paths are discarded; no archive entry can write outside the font folder.
     */
    public static ImportResult importFontArchive(Context context, Uri uri) throws Exception {
        if (uri == null) throw new IOException("No font archive was selected.");
        InputStream raw = context.getContentResolver().openInputStream(uri);
        if (raw == null) throw new IOException("Could not read the selected ZIP archive.");
        return importFontArchiveStream(context, raw);
    }

    /**
     * Installs the project-supplied legacy Hindi font pack from APK assets.
     * The archive is processed directly from assets and fonts remain in app-private storage.
     */
    public static synchronized ImportResult importBundledFontArchives(Context context) throws Exception {
        final String archivePath = "font-packs/HINDI FONTS.zip";
        final String versionKey = "bundled_legacy_pack_version";
        final String filesKey = "bundled_legacy_pack_files";
        SharedPreferences prefs = preferences(context);
        int appVersion;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            appVersion = android.os.Build.VERSION.SDK_INT >= 28
                    ? (int) info.getLongVersionCode() : info.versionCode;
        } catch (Exception exception) {
            throw new IOException("Could not determine the installed app version.", exception);
        }
        if (prefs.getInt(versionKey, -1) == appVersion) return new ImportResult();

        File directory = customDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the private font folder.");
        }

        // Remove only files installed by the previous bundled pack. User-imported fonts stay untouched.
        for (String name : prefs.getStringSet(filesKey, java.util.Collections.<String>emptySet())) {
            File oldFile = new File(directory, name);
            if (oldFile.isFile() && oldFile.getParentFile().equals(directory)) oldFile.delete();
        }

        Set<String> before = new HashSet<>();
        File[] current = directory.listFiles();
        if (current != null) for (File file : current) if (file.isFile()) before.add(file.getName());

        InputStream raw = context.getAssets().open(archivePath);
        ImportResult result = importFontArchiveStream(context, raw);

        Set<String> installed = new HashSet<>();
        File[] after = directory.listFiles();
        if (after != null) {
            for (File file : after) {
                if (file.isFile() && isSupportedExtension(file.getName())
                        && !before.contains(file.getName())) installed.add(file.getName());
            }
        }
        prefs.edit().putStringSet(filesKey, installed)
                .putInt(versionKey, appVersion).apply();
        return result;
    }

    private static ImportResult importFontArchiveStream(Context context, InputStream raw) throws Exception {
        File directory = customDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            raw.close();
            throw new IOException("Could not create the private font folder.");
        }

        ImportResult result = new ImportResult();
        long expanded = 0;
        int entriesSeen = 0;
        Set<String> existingHashes = new HashSet<>();
        File[] existingFiles = directory.listFiles();
        if (existingFiles != null) {
            for (File file : existingFiles) {
                if (file.isFile() && isSupportedExtension(file.getName())) {
                    existingHashes.add(sha256(readFile(file)));
                }
            }
        }

        try (InputStream source = raw;
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entriesSeen++;
                if (entriesSeen > 20000) throw new IOException("Archive contains too many entries.");
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                String entryName = entry.getName() == null ? "" : entry.getName();
                String baseName = entryName.replace('\\', '/');
                int slash = baseName.lastIndexOf('/');
                if (slash >= 0) baseName = baseName.substring(slash + 1);
                if (!isSupportedExtension(baseName)) {
                    result.ignored++;
                    expanded += skipZipEntryLimited(zip, MAX_ARCHIVE_EXPANDED_BYTES - expanded);
                    zip.closeEntry();
                    continue;
                }

                if (result.totalFontEntries() >= MAX_ARCHIVE_FONTS) {
                    throw new IOException("Font archive exceeds " + MAX_ARCHIVE_FONTS + " font files.");
                }

                byte[] bytes = readZipEntryLimited(zip, MAX_CUSTOM_FONT_BYTES);
                zip.closeEntry();
                expanded += bytes.length;
                if (expanded > MAX_ARCHIVE_EXPANDED_BYTES) {
                    throw new IOException("Expanded font data exceeds the 180 MB safety limit.");
                }

                if (!hasFontSignature(bytes)) {
                    result.invalid++;
                    continue;
                }

                try {
                    boolean stored = storeFontBytes(directory, bytes, baseName, existingHashes);
                    if (stored) {
                        result.imported++;
                        result.bytesImported += bytes.length;
                    } else {
                        result.duplicates++;
                    }
                } catch (Exception invalidFont) {
                    result.invalid++;
                }
            }
        }
        return result;
    }

    public static String getCustomFontCssForFamily(Context context, String family) throws IOException {
        String wanted = normalizeFamily(family);
        if (wanted.isEmpty()) return "";
        File directory = customDirectory(context);
        File[] files = directory.listFiles();
        if (files == null) return "";
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        StringBuilder css = new StringBuilder();
        for (File file : files) {
            if (!file.isFile() || !isSupportedExtension(file.getName())) continue;
            FontMetadata metadata = readFontMetadata(file);
            if (!normalizeFamily(metadata.family).equals(wanted)) continue;
            byte[] bytes = readFile(file);
            String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String format = extension(file.getName()).equals(".otf") ? "opentype" : "truetype";
            css.append("@font-face{font-family:")
                    .append(cssQuote(metadata.family))
                    .append(";src:url(data:font/").append(format).append(";base64,")
                    .append(encoded).append(") format('").append(format).append("');")
                    .append("font-weight:").append(metadata.weight)
                    .append(";font-style:").append(metadata.italic ? "italic" : "normal")
                    .append(";font-display:swap;}");
        }
        return css.toString();
    }

    /** Directory intended for the future LibreOfficeKit profile/fontconfig integration. */
    public static File getEngineFontDirectory(Context context) {
        return customDirectory(context);
    }

    /**
     * Prepares a Fontconfig configuration that points at the app-private font folder.
     * The native engine must set FONTCONFIG_FILE to this path before LOK initialization.
     */
    public static File prepareEngineFontConfig(Context context) throws IOException {
        File root = new File(context.getFilesDir(), "libreoffice/fontconfig");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Could not create fontconfig directory.");
        File cache = new File(context.getCacheDir(), "libreoffice/fontconfig");
        if (!cache.exists() && !cache.mkdirs()) throw new IOException("Could not create fontconfig cache.");
        File config = new File(root, "fonts.conf");
        String fontPath = xmlEscape(customDirectory(context).getAbsolutePath());
        String cachePath = xmlEscape(cache.getAbsolutePath());
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<fontconfig><dir>" + fontPath + "</dir><cachedir>" + cachePath
                + "</cachedir></fontconfig>\n";
        try (FileOutputStream output = new FileOutputStream(config, false)) {
            output.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
        }
        return config;
    }

    /**
     * Drains an ignored ZIP entry without buffering it in memory, while still
     * enforcing the total expanded-archive limit.
     */
    private static long skipZipEntryLimited(InputStream input, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new IOException("Expanded ZIP data exceeds the 180 MB safety limit.");
            }
        }
        return total;
    }

    private static byte[] readZipEntryLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("Font file exceeds the size limit.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean storeFontBytes(File directory, byte[] bytes, String originalName,
                                          Set<String> existingHashes) throws Exception {
        String hash = sha256(bytes);
        if (existingHashes.contains(hash)) return false;

        String extension = extension(originalName);
        String safe = safeFileName(originalName);
        String base = safe.length() > extension.length()
                ? safe.substring(0, safe.length() - extension.length()) : "Imported-Font";
        File destination = new File(directory, base + extension);
        int suffix = 2;
        while (destination.exists()) {
            destination = new File(directory, base + "-" + suffix + extension);
            suffix++;
        }
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(bytes);
            output.flush();
        }
        try {
            Typeface.createFromFile(destination);
            existingHashes.add(hash);
        } catch (Exception exception) {
            destination.delete();
            throw new IOException("Android cannot read font " + originalName, exception);
        }
        return true;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte b : digest) value.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private static String normalizeFamily(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static FontMetadata readFontMetadata(File file) {
        try {
            byte[] bytes = readFile(file);
            ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            if (bytes.length < 12) return emptyMetadata(file);
            int tables = unsignedShort(data, 4);
            int nameOffset = -1;
            int nameLength = 0;
            for (int i = 0; i < tables; i++) {
                int record = 12 + i * 16;
                if (record + 16 > bytes.length) break;
                String tag = new String(bytes, record, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if ("name".equals(tag)) {
                    long offset = unsignedInt(data, record + 8);
                    long length = unsignedInt(data, record + 12);
                    if (offset <= bytes.length && length <= bytes.length - offset) {
                        nameOffset = (int) offset;
                        nameLength = (int) length;
                    }
                    break;
                }
            }
            if (nameOffset < 0 || nameLength < 6) return emptyMetadata(file);
            int count = unsignedShort(data, nameOffset + 2);
            int stringOffset = unsignedShort(data, nameOffset + 4);
            int recordsStart = nameOffset + 6;
            String family16 = "", family1 = "", sub17 = "", sub2 = "", full4 = "";
            for (int i = 0; i < count; i++) {
                int at = recordsStart + i * 12;
                if (at + 12 > nameOffset + nameLength || at + 12 > bytes.length) break;
                int platform = unsignedShort(data, at);
                int language = unsignedShort(data, at + 4);
                int nameId = unsignedShort(data, at + 6);
                int length = unsignedShort(data, at + 8);
                int offset = unsignedShort(data, at + 10);
                int start = nameOffset + stringOffset + offset;
                if (start < nameOffset || start + length > nameOffset + nameLength || start + length > bytes.length) continue;
                String decoded = decodeName(bytes, start, length, platform).trim();
                if (decoded.isEmpty()) continue;
                boolean preferred = platform == 3 && (language == 0x0409 || language == 0);
                if (nameId == 16 && (family16.isEmpty() || preferred)) family16 = decoded;
                else if (nameId == 1 && (family1.isEmpty() || preferred)) family1 = decoded;
                else if (nameId == 17 && (sub17.isEmpty() || preferred)) sub17 = decoded;
                else if (nameId == 2 && (sub2.isEmpty() || preferred)) sub2 = decoded;
                else if (nameId == 4 && (full4.isEmpty() || preferred)) full4 = decoded;
            }
            String family = !family16.isEmpty() ? family16 : family1;
            String style = !sub17.isEmpty() ? sub17 : sub2;
            if (family.isEmpty()) family = customCssFamily(file);
            if (style.isEmpty()) style = "Regular";
            String full = full4.isEmpty() ? family + " " + style : full4;
            String lower = style.toLowerCase(Locale.ROOT);
            int weight = lower.contains("black") || lower.contains("heavy") ? 900
                    : lower.contains("bold") ? 700
                    : lower.contains("semibold") || lower.contains("demi") ? 600
                    : lower.contains("medium") ? 500
                    : lower.contains("thin") || lower.contains("light") ? 300 : 400;
            boolean italic = lower.contains("italic") || lower.contains("oblique");
            return new FontMetadata(family, style, full, weight, italic);
        } catch (Exception ignored) {
            return emptyMetadata(file);
        }
    }

    private static FontMetadata emptyMetadata(File file) {
        String family = customCssFamily(file);
        return new FontMetadata(family, "Regular", family, 400, false);
    }

    private static int unsignedShort(ByteBuffer data, int offset) {
        return data.getShort(offset) & 0xffff;
    }

    private static long unsignedInt(ByteBuffer data, int offset) {
        return data.getInt(offset) & 0xffffffffL;
    }

    private static String decodeName(byte[] data, int start, int length, int platform) {
        try {
            if (platform == 0 || platform == 3) {
                return new String(data, start, length, java.nio.charset.StandardCharsets.UTF_16BE);
            }
            return new String(data, start, length, Charset.forName("windows-1252"));
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void removeCustomFont(FontRecord record) {
        if (record != null && !record.bundled && record.customFile != null) {
            record.customFile.delete();
        }
    }

    private static boolean hasFontSignature(byte[] bytes) {
        if (bytes.length < 4) return false;
        String tag = new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return "OTTO".equals(tag) || "true".equals(tag) || "typ1".equals(tag)
                || (bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0);
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static File customDirectory(Context context) {
        // Match Collabora Android's Fontconfig path: <dataDir>/user/fonts.
        // This is app-private storage, not shared public storage.
        return new File(new File(context.getApplicationInfo().dataDir, "user"), "fonts");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String customCssFamily(File file) {
        String name = displayName(file.getName()).replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (name.isEmpty()) name = "Imported Font";
        return name;
    }

    private static String displayName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.replace('_', ' ').replace('-', ' ');
    }

    private static String safeFileName(String value) {
        if (value == null) return "Imported-Devanagari-Font";
        String clean = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.length() > 80 ? clean.substring(clean.length() - 80) : clean;
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean isSupportedExtension(String value) {
        String ext = extension(value);
        return ".ttf".equals(ext) || ".otf".equals(ext);
    }

    private static String cssQuote(String value) {
        try {
            return JSONObject.quote(value);
        } catch (Exception ignored) {
            return "\"" + value.replace("\"", "") + "\"";
        }
    }
}
