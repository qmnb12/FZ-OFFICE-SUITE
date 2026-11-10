"use strict";
const assert = require("assert");
const fs = require("fs");
const read = (p) => fs.readFileSync(p, "utf8");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const fonts = read("app/src/main/java/com/fz/office/fonts/FontManager.kt");
const codec = read("app/src/main/java/com/fz/office/writer/DocxCodec.kt");
const viewerHtml = read("app/src/main/assets/writer/viewer.html");

const load = writer.slice(writer.indexOf("private fun loadDocument()"), writer.indexOf("private fun initializeViewer()"));
assert(!load.includes("FontManager.importBundledFontArchives"),
  "Large legacy Hindi font archive import must not block DOCX first paint");

const init = writer.slice(writer.indexOf("private fun initializeViewer()"), writer.indexOf("private fun registerDocumentFontsAsync()"));
assert(!init.includes("FontManager.findByFamily") && !init.includes("getCustomFontCss"),
  "Viewer first paint must not scan or Base64-encode custom fonts on the UI thread");

const fontLoad = writer.slice(writer.indexOf("private fun registerDocumentFontsAsync()"), writer.indexOf("private fun applyPendingDocument()"));
assert(fontLoad.includes("ioExecutor.execute"), "Document font scan must run off the UI thread");
assert(fontLoad.includes("getCustomFontCssForFamilies"),
  "DOCX fonts must be resolved in one bulk scan");
assert(fontLoad.includes("FontManager.importBundledFontArchives"),
  "Legacy fonts may be imported only in the deferred background pass");

const bulkFonts = fonts.slice(fonts.indexOf("fun getCustomFontCssForFamilies"),
  fonts.indexOf("fun getCustomFontCssForFamily"));
assert(bulkFonts.split("directory.listFiles()").length === 2,
  "Bulk font resolution must scan the custom font directory once");

const inspect = codec.slice(codec.indexOf("private fun inspectFontNames"),
  codec.indexOf("private fun addFontName"));
assert(inspect.includes('if (path != "word/document.xml" && path != "word/styles.xml") continue'),
  "Font discovery must stay limited to DOCX body and styles");
const pagination = viewerHtml.slice(
  viewerHtml.indexOf("function processPage"),
  viewerHtml.indexOf("function zoom(")
);
assert(pagination.includes("requestAnimationFrame(step)"),
  "DOCX pagination must yield between page batches instead of blocking the WebView");
assert(pagination.includes("fz-manual-break"),
  "Word explicit page breaks must be honoured as real page boundaries");
assert(viewerHtml.includes("function paginateWhenFontsReady(state)") &&
  viewerHtml.includes("state.fontAvailable&&state.fontName") &&
  viewerHtml.includes("document.fonts.load(fontSpec)") &&
  viewerHtml.includes("if(lastDocument===state)paginate()"),
  "Pagination must wait for the actual DOCX font and default font before page measurements");
assert(!viewerHtml.includes("renderDocument(state,false)"),
  "Font completion must not paginate or rebuild an already-fragmented page DOM");
assert(!viewerHtml.includes("document.fonts.ready.then(function(){paginate()})"),
  "Font completion must not paginate already-fragmented page DOM");

assert(fontLoad.includes("if (loadedCss.isNotEmpty())") &&
  fontLoad.includes("applyPendingDocument()"),
  "A matching custom font may trigger one rebuild after registration");
assert(fontLoad.includes("else {\n                    updateStatus()"),
  "A font lookup with no match must not reload and repaginate the whole document");

console.log("FZ DOCX viewer startup, Hindi font loading, and pagination checks passed");
