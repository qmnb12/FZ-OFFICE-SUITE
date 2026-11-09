"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.java");
const viewer = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.java");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.java");
const editor = read("app/src/main/java/com/fz/office/OfficeEditorLauncher.java");
const fontManager = read("app/src/main/java/com/fz/office/fonts/FontManager.java");
const fontCenter = read("app/src/main/java/com/fz/office/fonts/FontCenterActivity.java");
const docxCodec = read("app/src/main/java/com/fz/office/writer/DocxCodec.java");
const manifest = read("app/src/main/AndroidManifest.xml");
const gradle = read("app/build.gradle.kts");

for (const ext of [".pdf", ".docx", ".doc", ".docm", ".dotx", ".dotm", ".odt", ".ott", ".fodt",
  ".xlsx", ".xls", ".xlsm", ".xlsb", ".xlt", ".xltx", ".csv", ".ods", ".ots", ".fods",
  ".ppt", ".pps", ".pot", ".pptx", ".ppsx", ".pptm", ".potx", ".odp", ".otp", ".fodp"]) {
  assert(main.includes(ext), "Missing file route for " + ext);
}
assert(main.includes("OfficeViewerActivity.class"), "Office viewer activity is not routed");
assert(main.includes("isFzWriterViewerDocument"), "DOCX/TXT must use the old FZ Writer reader by default");
assert(writer.includes("OfficeEditorLauncher.openForEdit(this, sourceUri, documentName)"), "DOCX viewer is missing its explicit Edit action");
assert(viewer.includes("OfficeEditorLauncher.openForEdit(this, documentUri, documentName)"), "Spreadsheet/slide reader is missing its explicit Edit action");
assert(editor.includes("editor.putExtra(READ_ONLY_EXTRA, false)"), "Edit action must launch Collabora in editable mode");
assert(editor.includes("FLAG_GRANT_WRITE_URI_PERMISSION"), "Edit action must respect provider write grants");
assert(main.includes("FLAG_GRANT_WRITE_URI_PERMISSION"), "Document picker must request persistable write access");
assert(fontManager.includes("ensureBundledEngineFonts") && fontManager.includes("getEngineFontFileCount"), "Native Hindi font install/count APIs are missing");
assert(fontCenter.includes("Search all fonts") && fontCenter.includes("actual font files installed in Collabora"), "Font Center must expose all-font search and total counts");
assert(docxCodec.includes("Noto Sans Devanagari") && docxCodec.includes("Noto Serif Devanagari"), "DOCX HTML must include Devanagari fallback families");
assert(main.includes("Choose a Word, Excel, PowerPoint, PDF or TXT document."),
  "file picker error text must match the supported formats");
assert(!main.includes("Choose DOCX, PDF, XLS/XLSX, CSV, ODS, PPTX or TXT."),
  "obsolete file picker error text must not return");
for (const mime of [
  "application/vnd.ms-word.template.macroenabled.12",
  "application/vnd.oasis.opendocument.text-template",
  "application/vnd.oasis.opendocument.text-flat-xml",
  "application/vnd.oasis.opendocument.spreadsheet-template",
  "application/vnd.oasis.opendocument.spreadsheet-flat-xml",
  "application/vnd.oasis.opendocument.presentation-template",
  "application/vnd.oasis.opendocument.presentation-flat-xml",
  "text/comma-separated-values"
]) {
  assert(main.includes(mime), "Open document picker is missing MIME type " + mime);
}
assert(main.includes('type.equals("text/comma-separated-values")'),
  "CSV MIME alias must be recognized even when a provider omits the file extension");
assert(main.includes("PdfViewerActivity.class"), "PDF viewer activity is not routed");
assert(viewer.includes("pptx.html") && viewer.includes("xlsx.html"), "Missing PPTX/XLSX page route");
assert(viewer.includes("/doc/file.pptx"), "PPTX endpoint is not mapped");
assert(viewer.includes("FontManager.getBundledCss()"), "Hindi fonts are not exposed");
assert(viewer.includes("hindi-fonts.css"), "Hindi CSS endpoint is missing");
assert(viewer.includes("setAllowFileAccess(false)"), "WebView file access must remain disabled");
assert(viewer.includes("setAllowContentAccess(false)"), "WebView content access must remain disabled");
assert(viewer.includes("setBlockNetworkLoads(true)"), "Runtime network loads must remain blocked");
assert(viewer.includes("setUseWideViewPort(true)") && viewer.includes("setLoadWithOverviewMode(true)"), "Office pages need Gander-style fit-to-screen viewport handling");
assert(viewer.includes("setMinimumFontSize(presentation ? 8 : 1)"), "Spreadsheet glyph sizing floor is not configured");
assert(viewer.includes("connect-src 'self'"), "Viewer CSP must remain same-origin");
assert(manifest.includes('android:icon="@mipmap/ic_launcher"'), "Launcher icon is not assigned");
assert(manifest.includes('android:roundIcon="@mipmap/ic_launcher_round"'), "Round icon is not assigned");
assert(manifest.includes(".viewer.OfficeViewerActivity"), "Viewer activity missing from manifest");
assert(manifest.includes(".pdf.PdfViewerActivity"), "PDF activity missing from manifest");
assert(manifest.includes('android:mimeType="application/pdf"'), "PDF MIME filter is missing");
assert(!manifest.includes('android.permission.INTERNET'), "Offline app must not request INTERNET");
assert(gradle.includes("downloadGanderViewerAssets"), "Gander assets are not included in build");
assert(gradle.includes("73cf325435868cab1683d8de8cc1b89533e767b3"), "Gander commit is not pinned");
assert(gradle.includes("gitBlobSha"), "Viewer assets must be checksum verified");
assert(gradle.includes('versionName = "0.6.7"'), "Version name must match the new release");
assert(gradle.includes("versionCode = 16"), "Version code must match the new release");
assert(main.includes("org.libreoffice.androidlib.LOActivity"), "Native LibreOffice routing is missing");
assert(main.includes('nativeOpen.putExtra("com.fz.office.READ_ONLY", true)'),
  "Office documents must open in read-only mode by default");
assert(main.includes('libraryTabButton("Recent files"') && main.includes('libraryTabButton("Starred files"'),
  "Recent and Starred library labels must be visible");

console.log("Office viewer routing, Hindi font bridge, offline policy and pinned assets verified.");
