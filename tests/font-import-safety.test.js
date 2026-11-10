const fs = require("fs");
const assert = require("assert");

const manager = fs.readFileSync("app/src/main/java/com/fz/office/fonts/FontManager.kt", "utf8");
const fontCenter = fs.readFileSync(
  "app/src/main/java/com/fz/office/fonts/FontCenterActivity.kt",
  "utf8"
);

// Ignored ZIP entries are drained with a bounded buffer, never buffered in memory.
const ignoredEntryMatch = manager.match(
  /if\s*\(!isSupportedExtension\(baseName\)\)\s*\{([\s\S]*?)\n\s*\}/
);
assert(ignoredEntryMatch, "FontManager must keep an explicit non-font ZIP-entry branch.");
assert(
  ignoredEntryMatch[1].includes("skipZipEntryLimited(zip, MAX_ARCHIVE_EXPANDED_BYTES - expanded)"),
  "Non-font ZIP entries must be streamed and drained."
);
assert(!ignoredEntryMatch[1].includes("readZipEntryLimited"),
  "Non-font ZIP entries must not be buffered in memory.");
assert(
  /private fun skipZipEntryLimited\(input: InputStream, limit: Long\): Long[\s\S]*?ByteArray\(8192\)/.test(manager),
  "The streaming drain must use a fixed-size buffer."
);

// Keep strict size/count limits and validate font file formats before import.
assert(manager.includes("MAX_CUSTOM_FONT_BYTES = 12 * 1024 * 1024"),
  "Single-font import must keep its 12 MB limit.");
assert(manager.includes("MAX_ARCHIVE_FONTS = 1200"),
  "ZIP font import must keep its file-count bound.");
assert(manager.includes("MAX_ARCHIVE_EXPANDED_BYTES = 180L * 1024L * 1024L"),
  "ZIP font import must keep its expanded-size bound.");
assert(manager.includes('ext == ".ttf" || ext == ".otf"'),
  "Only TTF/OTF font files should be accepted.");
assert(manager.includes("safeFileName(originalName)"),
  "Imported archive paths must be reduced to safe file names.");
assert(manager.includes("Typeface.createFromFile(destination)"),
  "Android must validate each imported font file.");

// The library scan must be cached while searching and invalidated after mutations.
assert(
  /val allFonts = fontSnapshot \?: FontManager\.getAvailableFonts\(this\)\.also \{[\s\S]*?fontSnapshot = it/.test(fontCenter),
  "Font Center search must reuse a scanned font snapshot."
);
assert(
  (fontCenter.match(/fontSnapshot = null;/g) || []).length >= 3,
  "Font Center must invalidate its cached list after import and removal."
);
assert(fontCenter.includes("Search all fonts") && fontCenter.includes("Total "),
  "Font Center must keep all-font search and total counts.");
assert(!manager.includes("LibreOfficeKit") && !manager.includes("Collabora")
  && !manager.includes("fontconfig"),
  "Font import must not depend on a native engine.");

console.log("Font import safety and view-only Font Center tests passed.");
