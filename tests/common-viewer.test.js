"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const controller = read("app/src/main/java/com/fz/office/viewer/DocumentViewerController.kt");
const pdfPrint = read("app/src/main/java/com/fz/office/viewer/UriPdfPrintAdapter.kt");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const office = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");

for (const feature of ["Find", "Zoom in", "Zoom out", "Fit to width", "Go to page", "Print", "Share"]) {
  assert(controller.includes('actions.add("' + feature + '"'), "Shared controller must own " + feature);
}
assert(controller.includes("class DocumentViewerController"), "The one common document controller must exist");
assert(controller.includes("interface DocumentViewerSurface"), "All formats must implement the common surface contract");
assert(controller.includes("class WebViewDocumentSurface"), "WebView-based formats must reuse one surface adapter");
assert(controller.includes("createPrintDocumentAdapter(jobName)"), "WebView printing must be shared");
assert(pdfPrint.includes("class UriPdfPrintAdapter"), "PDF printing must reuse the same print action");
assert(pdfPrint.includes("openInputStream(documentUri)"), "PDF print must stream the unchanged source file");
assert(writer.includes("DocumentViewerController(") && writer.includes("WebViewDocumentSurface("),
  "DOCX/TXT must connect to the common controller");
assert(office.includes("DocumentViewerController(") && office.includes("WebViewDocumentSurface(browser)"),
  "XLSX/PPTX must connect to the common controller");
assert(pdf.includes("DocumentViewerController(") && pdf.includes("DocumentViewerSurface"),
  "PDF must connect to the common controller through its renderer adapter");
assert(!writer.includes("private fun showSearch()") && !office.includes("private fun showSearch()"),
  "WebView readers must not keep separate Find dialogs");
assert(!office.includes("private fun findText("), "Office viewer must not keep a duplicate find implementation");
assert(!pdf.includes("private fun showGoToPage()") && !pdf.includes("private fun shareDocument()"),
  "PDF must not keep separate page/share dialogs");
assert(!pdf.includes('addToolbarAction("Fit"'), "PDF Fit must come from the shared controller");
assert(!office.includes('addToolbarAction("Find"') && !writer.includes('addToolbarAction("Find"'),
  "Each format must not create its own Find toolbar control");

console.log("Shared FZ document-viewer architecture checks passed.");
