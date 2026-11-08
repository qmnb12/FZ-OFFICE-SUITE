package com.fz.office.fonts;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FontManager {
    private static final String PREFS = "fz_hindi_fonts";
    private static final String KEY_DEFAULT = "default_family";
    private static final int MAX_CUSTOM_FONT_BYTES = 5 * 1024 * 1024;

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

        FontRecord(String name, String category, String description,
                   String cssFamily, String assetFile, File customFile, boolean bundled) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.cssFamily = cssFamily;
            this.assetFile = assetFile;
            this.customFile = customFile;
            this.bundled = bundled;
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
        return new FontRecord(name, category, description, name, file, null, true);
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
                String display = displayName(file.getName());
                result.add(new FontRecord(
                        display + " (Imported)", "Imported font",
                        "Stored privately on this device. Redistribution rights are your responsibility.",
                        customCssFamily(file), null, file, false
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
        for (FontRecord record : getAvailableFonts(context)) {
            if (record.cssFamily.equals(family)) return record;
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
                + format + "');font-weight:100 900;font-style:normal;font-display:swap;}";
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
                    throw new IOException("Font file is too large. Maximum allowed size is 5 MB.");
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
        return new File(context.getFilesDir(), "hindi-fonts");
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
