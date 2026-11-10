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

for (const feature of ["Find", "Scroll up", "Scroll down", "Go to top", "Go to bottom", "Zoom in", "Zoom out", "Fit to width", "Go to page", "Share"]) {
  assert(controller.includes('actions.add("' + feature + '"'), "Shared controller must own " + feature);
}
assert(controller.includes("surface.supportsPrint") &&
  controller.includes("actions.add(surface.printLabel"),
  "Print action must be shown with the renderer-specific label");
assert(controller.includes("class DocumentViewerController"), "The one common document controller must exist");
assert(controller.includes("interface DocumentViewerSurface"), "All formats must implement the common surface contract");
assert(controller.includes("class WebViewDocumentSurface"), "WebView-based formats must reuse one surface adapter");
assert(controller.includes("zoomInScript") && controller.includes("zoomOutScript") &&
  controller.includes("fitZoomScript") && controller.includes("view.evaluateJavascript(script, null)"),
  "WebView surfaces must allow renderer-owned zoom where native WebView zoom is disabled");
assert(controller.includes("createWebMessageChannel()") &&
  controller.includes("postWebMessage(WebMessage(") &&
  controller.includes('postFindCommand("q" + value)') &&
  controller.includes('postFindCommand(if (forward) "n" else "p")') &&
  controller.includes('postFindCommand("p")') &&
  controller.includes('postFindCommand("c")'),
  "Office Find must use Gander's full-document message-port API, not only WebView's visible-DOM Find");
assert(controller.includes("ClipData.newUri") &&
  controller.includes("FLAG_GRANT_READ_URI_PERMISSION"),
  "Share must grant the receiving app read access to the selected document URI");
assert(controller.includes("preparePrint(onReady") &&
  controller.includes("surface.preparePrint"),
  "Format-specific print preparation must complete before opening Android Print");
assert(office.includes("window.vwSheetZoom(z*1.2") &&
  office.includes("window.vwSheetZoom(z/1.2") &&
  office.includes("window.vwSheetZoom(1,"),
  "XLSX Tools zoom must call the sheet's own CSS zoom engine rather than the disabled WebView zoom");
assert(controller.includes("view.pageUp(true)") && controller.includes("view.pageDown(true)"),
  "DOCX/TXT/XLSX/PPTX must use the shared WebView scroll adapter");
assert(controller.includes("fun scrollBackward()") && controller.includes("fun scrollForward()"),
  "All renderer types must expose common scroll operations");
assert(controller.includes("createPrintDocumentAdapter(jobName)"), "WebView printing must be shared");
assert(pdfPrint.includes("class UriPdfPrintAdapter"), "PDF printing must reuse the same print action");
assert(pdfPrint.includes("openInputStream(documentUri)"), "PDF print must stream the unchanged source file");
assert(writer.includes("DocumentViewerController(") && writer.includes("WebViewDocumentSurface("),
  "DOCX/TXT must connect to the common controller");
assert(office.includes("DocumentViewerController(") &&
  office.includes("WebViewDocumentSurface(") && office.includes("browser,"),
  "XLSX/PPTX must connect to the common controller with its renderer-specific zoom adapter");
assert(pdf.includes("DocumentViewerController(") && pdf.includes("DocumentViewerSurface"),
  "PDF must connect to the common controller through its renderer adapter");
assert(pdf.includes("override val supportsScrollNavigation: Boolean = true") &&
  pdf.includes("override fun scrollForward()") && pdf.includes("override fun scrollToEnd()"),
  "PDF must map the same common scroll actions to its native page list");
assert(pdf.includes("jumpToPageEnd(pageSizes.lastIndex)") &&
  pdf.includes("viewportHeight - page.height"),
  "PDF Go to bottom must align the last page's bottom edge, not only jump to its top");
assert(!writer.includes("private fun showSearch()") && !office.includes("private fun showSearch()"),
  "WebView readers must not keep separate Find dialogs");
assert(!office.includes("private fun findText("), "Office viewer must not keep a duplicate find implementation");
assert(!pdf.includes("private fun showGoToPage()") && !pdf.includes("private fun shareDocument()"),
  "PDF must not keep separate page/share dialogs");
assert(!pdf.includes('addToolbarAction("Fit"'), "PDF Fit must come from the shared controller");
assert(!office.includes('addToolbarAction("Find"') && !writer.includes('addToolbarAction("Find"') && !writer.includes("showSearch()"),
  "Each format must not create its own Find toolbar control");

console.log("Shared FZ document-viewer architecture checks passed.");
