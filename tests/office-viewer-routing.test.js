"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.kt");
const viewer = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.kt");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const wordHtml = read("app/src/main/assets/writer/viewer.html");
const fontManager = read("app/src/main/java/com/fz/office/fonts/FontManager.kt");
const fontCenter = read("app/src/main/java/com/fz/office/fonts/FontCenterActivity.kt");
const docxCodec = read("app/src/main/java/com/fz/office/writer/DocxCodec.kt");
const manifest = read("app/src/main/AndroidManifest.xml");
const gradle = read("app/build.gradle.kts");

for (const ext of [".pdf", ".docx", ".txt", ".xlsx", ".xls", ".xlsm", ".xlsb",
  ".xltx", ".csv", ".ods", ".pptx", ".ppsx", ".pptm", ".potx"]) {
  assert(main.includes(ext), "Missing read-only route for " + ext);
}
assert(main.includes("OfficeViewerActivity"), "Spreadsheet/presentation reader is not routed");
assert(main.includes("isFzWriterViewerDocument"), "DOCX/TXT must use the FZ Writer reader");
assert(main.includes("PdfViewerActivity::class.java"), "PDF reader is not routed");
assert(main.includes("Choose DOCX/TXT, supported spreadsheets, presentations or PDF."),
  "Picker guidance must match supported view-only formats");
assert(main.includes("FLAG_GRANT_READ_URI_PERMISSION"), "Document open must request read access");
assert(!main.includes("FLAG_GRANT_WRITE_URI_PERMISSION"), "Read-only mode must not request write access");
assert(!main.includes("org.libreoffice.androidlib.LOActivity"), "Native LibreOffice launch must be removed");
assert(!main.includes("launchNativeOfficeDocument"), "No native fallback should remain");
assert(!main.includes("OfficeEditorLauncher"), "Main activity must not expose an editor route");
assert(!writer.includes("OfficeEditorLauncher") && !writer.includes('control("Edit"'),
  "DOCX reader must remain read-only with no Writer edit action");
assert(!viewer.includes("OfficeEditorLauncher"), "No external office editor launcher should be used");
assert(viewer.includes('toolbarControl("Edit")') && viewer.includes("SpreadsheetEditorActivity::class.java"),
  "XLSX viewer must expose the custom FZ Sheets editor");
assert(viewer.includes("editableXlsx && !presentation"),
  "Only .xlsx workbooks may expose the editing action");
assert(writer.includes("VIEW ONLY"), "Writer reader must show view-only status");
assert(writer.includes("builtInZoomControls = true") && writer.includes("displayZoomControls = false"),
  "Word/TXT reader must use native WebView pinch zoom without overlay controls");
assert(wordHtml.includes('maximum-scale=4, user-scalable=yes') &&
  wordHtml.includes("touch-action:manipulation"),
  "Word/TXT pages must allow native pinch zoom while keeping one-finger scrolling");
const wordZoomLayout = wordHtml.slice(
  wordHtml.indexOf("function applyPageLayout"),
  wordHtml.indexOf("function makePage")
);
assert(wordZoomLayout.length > 0 &&
  !wordZoomLayout.includes("pages().forEach") &&
  wordZoomLayout.includes("--fz-shell-width") &&
  wordZoomLayout.includes("--fz-page-transform"),
  "Word zoom must update shared CSS variables instead of iterating every page");
assert(wordHtml.includes("applyPageLayout(true)"),
  "Word page numbers must refresh after pagination");
assert(viewer.includes("webView?.zoomIn()") && viewer.includes("webView?.zoomOut()"),
  "Spreadsheet and slide zoom controls must remain available");
assert(gradle.includes('"output" to "viewer/xlsx.js"') &&
  gradle.includes('"output" to "viewer/pptx.js"'),
  "Gander spreadsheet and presentation gesture handlers must remain bundled");
assert(!fs.existsSync(path.join(root, "app/src/main/java/com/fz/office/writer/WriterEditorActivity.java")),
  "Unused DOCX editor implementation must not ship");
assert(!manifest.includes(".writer.WriterEditorActivity"), "DOCX editor must not be registered");
assert(viewer.includes("VIEW ONLY"), "Spreadsheet/slide reader must show view-only status");
assert(fontManager.includes("getAvailableFonts") && fontManager.includes("getBundledCss"),
  "Bundled and imported font display support must remain");
for (const nativeApi of ["ensureBundledEngineFonts", "getEngineFontFileCount",
  "prepareEngineFontConfig", "clearEngineFontCaches", "LibreOfficeKit", "Collabora"]) {
  assert(!fontManager.includes(nativeApi), "Native font bridge must be removed: " + nativeApi);
}
assert(fontCenter.includes("Search all fonts") && fontCenter.includes("Total "),
  "Font Center must keep all-font search and total counts");
assert(!fontCenter.includes("Collabora") && !fontCenter.includes("Native engine"),
  "Font Center must not reference the native engine");
assert(docxCodec.includes("Noto Sans Devanagari") && docxCodec.includes("Noto Serif Devanagari"),
  "DOCX HTML must keep Devanagari fallback families");
assert(viewer.includes("pptx.html") && viewer.includes("xlsx.html"), "Gander reader pages are missing");
assert(viewer.includes("/doc/file.pptx"), "PPTX reader endpoint is not mapped");
assert(viewer.includes("FontManager.getBundledCss()"), "Hindi font CSS bridge is missing");
assert(viewer.includes("hindi-fonts.css"), "Hindi CSS endpoint is missing");
assert(viewer.includes("allowFileAccess = false"), "WebView file access must remain disabled");
assert(viewer.includes("allowContentAccess = false"), "WebView content access must remain disabled");
assert(viewer.includes("blockNetworkLoads = true"), "Runtime network loads must remain blocked");
assert(viewer.includes("connect-src 'self'"), "Viewer CSP must remain same-origin");
assert(manifest.includes('android:icon="@mipmap/ic_launcher"'), "Launcher icon is not assigned");
assert(manifest.includes('android:roundIcon="@mipmap/ic_launcher_round"'), "Round icon is not assigned");
assert(manifest.includes(".viewer.OfficeViewerActivity"), "Office reader missing from manifest");
assert(manifest.includes(".viewer.SpreadsheetEditorActivity"), "Spreadsheet editor missing from manifest");
assert(manifest.includes(".pdf.PdfViewerActivity"), "PDF reader missing from manifest");
assert(!manifest.includes('android.permission.INTERNET'), "Offline app must not request INTERNET");
assert(gradle.includes("downloadGanderViewerAssets"), "Pinned Gander assets are missing from build");
assert(gradle.includes("gitBlobSha"), "Viewer assets must be checksum verified");
assert(gradle.includes('versionName = "0.7.3"'), "Version name must match the viewer-focused build");
assert(gradle.includes("versionCode = 22"), "Version code must match the viewer-focused build");

console.log("FZ viewer routing, Excel-only edit path, permissions, Hindi fonts and pinned reader assets verified.");
