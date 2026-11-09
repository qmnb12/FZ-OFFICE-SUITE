"use strict";
const assert = require("assert");
const fs = require("fs");
const read = (p) => fs.readFileSync(p, "utf8");
const editor = read("app/src/main/java/com/fz/office/writer/WriterEditorActivity.java");
const html = read("app/src/main/assets/writer/editor.html");
const viewer = read("app/src/main/java/com/fz/office/writer/WriterActivity.java");
const codec = read("app/src/main/java/com/fz/office/writer/DocxCodec.java");
assert(viewer.includes('control("Edit"'), "Edit action must be visible in DOCX viewer");
assert(viewer.includes("startActivity(intent)"), "Editor must open only after explicit Edit tap");
assert(viewer.includes("loadedDocumentForEdit") && viewer.includes("setPreloadedDocument"),
  "Viewer must hand its parsed DOCX package to Edit without reading it twice");
assert(editor.includes("takePreloadedDocument(source)"),
  "Editor must consume the viewer's parsed DOCX package before opening the source again");
assert(viewer.includes('contains("<img")'), "Image-bearing documents must be protected from lossy editing");
assert(html.includes('contenteditable="true"'), "Writer editor page must be editable");
assert(html.includes("function paginate(compactInitialLayout)"),
  "Initial pagination must support skipping the expensive page-compaction pass");
assert(html.includes("compactInitialLayout !== false"),
  "Normal edits may compact pages, but the initial load can skip the redundant repack pass");
const setHtmlBlock = html.slice(html.indexOf("function setHtml(html)"), html.indexOf("function registerFonts(css)"));
assert(!setHtmlBlock.includes("paginate();"),
  "Initial DOCX load must not synchronously paginate twice");
assert(setHtmlBlock.includes("document.fonts.ready.then(layoutInitialDocument, layoutInitialDocument)"),
  "Initial pagination must wait for document fonts and run once");
assert(setHtmlBlock.includes("paginate(false)"), "Initial pagination must skip page repacking");
assert(editor.includes("@JavascriptInterface public void onLayoutReady()"),
  "Editor must report ready only after initial page layout completes");
assert(editor.includes("initializationStarted"),
  "WebView page-finished and JS-ready callbacks must not initialize the document twice");
for (const command of ["bold", "italic", "underline", "insertUnorderedList", "insertOrderedList",
  "justifyLeft", "justifyCenter", "justifyRight", "undo", "redo"]) {
  assert(editor.includes('"'+command+'"'), "Missing Writer command: " + command);
}
assert(html.includes("function insertTable()"), "Writer must support inserting tables");
assert(editor.includes("ACTION_CREATE_DOCUMENT"), "Save must create a separate copy");
assert(editor.includes("documentFonts") && editor.includes("getCustomFontCssForFamily"),
  "Writer edit mode must register the fonts declared by the source DOCX");
assert(editor.includes("FontManager.importBundledFontArchives"),
  "Writer edit mode must import the same bundled Hindi fonts as the reader");
assert(!editor.includes("FLAG_GRANT_WRITE_URI_PERMISSION"), "Editor must not request source write permission");
assert(editor.includes("DocxCodec.writeDocx(editedHtml, packageEntries, layout)"), "Save must preserve page geometry");
assert(codec.includes("entries.putAll(originalEntries)"), "Original DOCX package parts must be retained");
console.log("FZ Writer edit-only-on-demand and safe Save As checks passed.");
