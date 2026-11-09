"use strict";
const assert = require("assert");
const fs = require("fs");
const read = (p) => fs.readFileSync(p, "utf8");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.java");
const fonts = read("app/src/main/java/com/fz/office/fonts/FontManager.java");
const codec = read("app/src/main/java/com/fz/office/writer/DocxCodec.java");

const load = writer.slice(writer.indexOf("private void loadDocument()"), writer.indexOf("private void initializeViewer()"));
assert(!load.includes("FontManager.importBundledFontArchives"),
  "Large legacy Hindi font archive import must not block DOCX first paint");

const init = writer.slice(writer.indexOf("private void initializeViewer()"), writer.indexOf("private void registerDocumentFontsAsync()"));
assert(!init.includes("FontManager.findByFamily") && !init.includes("getCustomFontCss"),
  "Viewer first paint must not scan or Base64-encode custom fonts on the UI thread");

const fontLoad = writer.slice(writer.indexOf("private void registerDocumentFontsAsync()"), writer.indexOf("private void applyPendingDocument()"));
assert(fontLoad.includes("ioExecutor.execute"), "Document font scan must run off the UI thread");
assert(fontLoad.includes("getCustomFontCssForFamilies"),
  "DOCX fonts must be resolved in one bulk scan");
assert(fontLoad.includes("FontManager.importBundledFontArchives"),
  "Legacy fonts may be imported only in the deferred background pass");

const bulkFonts = fonts.slice(fonts.indexOf("public static String getCustomFontCssForFamilies"),
  fonts.indexOf("public static String getCustomFontCssForFamily"));
assert(bulkFonts.split("directory.listFiles()").length === 2,
  "Bulk font resolution must scan the custom font directory once");

const inspect = codec.slice(codec.indexOf("private static List<String> inspectFontNames"),
  codec.indexOf("private static void addFontName"));
assert(inspect.includes('if (!(path.equals("word/document.xml") || path.equals("word/styles.xml"))) continue;'),
  "Font discovery must stay limited to DOCX body and styles");
console.log("FZ DOCX viewer startup and font-loading performance checks passed.");
