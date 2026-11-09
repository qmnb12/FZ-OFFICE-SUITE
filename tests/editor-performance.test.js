"use strict";
const assert = require("assert");
const fs = require("fs");
const vm = require("vm");
const read = (p) => fs.readFileSync(p, "utf8");
const html = read("app/src/main/assets/writer/editor.html");
const editor = read("app/src/main/java/com/fz/office/writer/WriterEditorActivity.java");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.java");
const fonts = read("app/src/main/java/com/fz/office/fonts/FontManager.java");
const codec = read("app/src/main/java/com/fz/office/writer/DocxCodec.java");

// Parse the real inline editor program so syntax errors fail CI before Android build.
const scriptMatch = html.match(/<script>\s*([\s\S]*?)<\/script>/);
assert(scriptMatch, "Writer inline script must exist");
new vm.Script(scriptMatch[1], { filename: "writer/editor.html" });

const addPage = html.slice(html.indexOf("function addPageAfter"), html.indexOf("function wirePage"));
assert(addPage.includes("applyPageLayout"), "New pages must receive their own geometry");
assert(!addPage.includes("applyLayout()"), "Adding one page must not relayout every existing page");

const pagination = html.slice(html.indexOf("function paginate(compactInitialLayout, onComplete)"),
  html.indexOf("function handleBoundaryKey"));
assert(pagination.includes("requestAnimationFrame(runSlice)"),
  "Pagination must yield between bounded work slices");
assert(pagination.includes("operations < 8") && pagination.includes("paginationNow() - started < 7"),
  "Pagination slices must have a bounded operation/time budget");
assert(pagination.includes("pages.splice"), "Pagination must maintain its page index without repeated full DOM queries");
assert(html.includes('pagesRoot.setAttribute("aria-busy", "true")'),
  "Initial pagination must expose a preparation state and prevent edits during DOM moves");

const load = editor.slice(editor.indexOf("private void load()"), editor.indexOf("private void initialize()"));
const fontLoad = editor.slice(editor.indexOf("private void loadDocumentFontsAsync()"), editor.indexOf("private void run(String js)"));
assert(!load.includes("FontManager.importBundledFontArchives"),
  "Large legacy Hindi archive import must not block initial editor preparation");
assert(fontLoad.includes("FontManager.importBundledFontArchives"),
  "Legacy Hindi fonts must still be imported after initial layout");
assert(fontLoad.includes("getCustomFontCssForFamilies"),
  "Document font CSS must be collected in one pass");

const bulkFonts = fonts.slice(fonts.indexOf("public static String getCustomFontCssForFamilies"),
  fonts.indexOf("public static String getCustomFontCssForFamily"));
assert(bulkFonts.includes("directory.listFiles()") && !bulkFonts.includes("for (String family"),
  "Bulk font resolution must scan the font directory once, not once per family");

const inspect = codec.slice(codec.indexOf("private static List<String> inspectFontNames"),
  codec.indexOf("private static void addFontName"));
assert(!inspect.includes("fontTable.xml") && !inspect.includes("word/header") && !inspect.includes("word/footer"),
  "Unused font table, header and footer families must not be loaded into the editor's font scan");

assert(writer.includes("getCustomFontCssForFamilies") || writer.includes("registerDocumentFonts"),
  "The DOCX viewer must retain document-font registration support");
console.log("Writer startup, pagination and font-scan performance checks passed.");
