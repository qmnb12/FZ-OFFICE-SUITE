const fs = require("fs");
const assert = require("assert");

const manager = fs.readFileSync("app/src/main/java/com/fz/office/fonts/FontManager.java", "utf8");
const nativePatch = fs.readFileSync("scripts/patch-collabora-hindi.py", "utf8");
const fontCenter = fs.readFileSync(
  "app/src/main/java/com/fz/office/fonts/FontCenterActivity.java",
  "utf8"
);
const fontconfigPatch = fs.readFileSync(
  "patches/collabora/0001-android-fontconfig-hindi-font-directory.patch",
  "utf8"
);

// Ignored ZIP entries must be drained using a fixed-size buffer, never copied
// into a potentially very large byte array just to discard them.
const ignoredEntryMatch = manager.match(
  /if\s*\(!isSupportedExtension\(baseName\)\)\s*\{([\s\S]*?)\n\s*\}/
);
assert(ignoredEntryMatch, "FontManager must keep an explicit non-font ZIP-entry branch.");
assert(
  ignoredEntryMatch[1].includes("skipZipEntryLimited(zip, MAX_ARCHIVE_EXPANDED_BYTES - expanded)"),
  "Non-font ZIP entries must be streamed and drained."
);
assert(
  !ignoredEntryMatch[1].includes("readZipEntryLimited"),
  "Non-font ZIP entries must not be buffered in memory."
);
assert(
  /private static long skipZipEntryLimited\(InputStream input, long limit\)[\s\S]*?byte\[\] buffer = new byte\[8192\]/.test(manager),
  "The streaming drain must use a fixed-size buffer."
);
assert(
  manager.includes("Maximum allowed size is 12 MB."),
  "Single-font import error text must match the 12 MB limit."
);

// The native integration runs against upstream source, so keep this class
// reference fully qualified instead of depending on upstream imports.
assert(
  nativePatch.includes("java.io.ByteArrayOutputStream bytes = isFont ? new java.io.ByteArrayOutputStream() : null;"),
  "Native font ZIP importer must use the fully qualified ByteArrayOutputStream."
);
assert(
  fontconfigPatch.includes("<dir>/data/data/@@APPLICATION_ID@@/user/fonts</dir>"),
  "Fontconfig must scan the app-private imported-font directory."
);
assert(
  nativePatch.includes("MAX_HINDI_FONT_ARCHIVE_BYTES = 180L * 1024L * 1024L")
    && nativePatch.includes("MAX_HINDI_FONT_FILE_BYTES = 12 * 1024 * 1024")
    && nativePatch.includes("MAX_HINDI_FONTS = 1200"),
  "Native ZIP import must retain its archive, per-font and font-count bounds."
);

assert(
  /List<FontManager\.FontRecord> allFonts = fontSnapshot;[\s\S]*?if \(allFonts == null\) \{[\s\S]*?FontManager\.getAvailableFonts\(this\)/.test(fontCenter),
  "Font Center search must reuse a scanned font snapshot instead of reopening every font file on each keystroke."
);
assert(
  (fontCenter.match(/fontSnapshot = null;/g) || []).length >= 3,
  "Font Center must invalidate its cached list after import and removal."
);

console.log("Font import safety, Font Center caching and native integration tests passed.");
