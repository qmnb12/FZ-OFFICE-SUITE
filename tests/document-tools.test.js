"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const controller = read("app/src/main/java/com/fz/office/viewer/DocumentViewerController.kt");
const office = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.kt");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");
const gradle = read("app/build.gradle.kts");

for (const item of [
  'actions.add("Find"',
  'actions.add("Scroll up"',
  'actions.add("Scroll down"',
  'actions.add("Go to top"',
  'actions.add("Go to bottom"',
  'actions.add("Zoom in"',
  'actions.add("Zoom out"',
  'actions.add("Fit to width"',
  "surface.supportsPageNavigation",
  "surface.preparePrint",
  "actions.add(surface.printLabel",
  'actions.add("Share"'
]) assert(controller.includes(item), "Missing Tools action dispatch: " + item);

assert(controller.includes("createWebMessageChannel()") &&
  controller.includes("postWebMessage(WebMessage(") &&
  controller.includes('postFindCommand("q" + value)') &&
  controller.includes('postFindCommand(if (forward) "n" else "p")') &&
  controller.includes('postFindCommand("p")') &&
  controller.includes('postFindCommand("c")'),
  "XLSX/PPTX Find must connect to the full-document find.js message channel");
assert(controller.includes("window.scrollTo(window.scrollX||0,0)") &&
  controller.includes("window.scrollTo(window.scrollX||0,Math.max"),
  "Go to top and bottom must preserve horizontal pan");
assert(controller.includes("ClipData.newUri") &&
  controller.includes("FLAG_GRANT_READ_URI_PERMISSION"),
  "Share must attach ClipData and a read-only URI grant");
assert(controller.includes("view.zoomBy(targetScale / currentScale)"),
  "PPTX Fit to width must restore its captured initial overview scale");

assert(writer.includes("documentWebView.setFindListener") &&
  !writer.includes("findAllAsync"),
  "DOCX/TXT Find status must be wired to native WebView search");
assert(writer.includes("FZViewer.pageCount()") &&
  writer.includes("FZViewer.goToPage($page)"),
  "DOCX page navigation must use the document pagination API");

assert(office.includes('findBridgeOrigin = Uri.parse("https://" + ASSET_HOST)') &&
  office.includes("documentSurface?.connectFindBridge()"),
  "Office viewers must connect the bundled Find message channel after page load");
assert(office.includes("pageCountScript = if (presentation)") &&
  office.includes("pageCommand = if (presentation)"),
  "PPTX must expose Go to page as Go to slide");
assert(office.includes("initialPresentationFitScale") &&
  office.includes("capturePresentationFitScale(view)"),
  "PPTX Fit to width must capture the initial slide overview scale");
assert(office.includes("vwSheetFitWidth") && office.includes("vwSheetPreparePrint"),
  "XLSX fit and print actions must use workbook-aware APIs");

assert(pdf.includes("jumpToPageEnd(pageSizes.lastIndex)") &&
  pdf.includes("val itemHeight = displayHeight + dp(22)") &&
  pdf.includes("layout.scrollToPositionWithOffset(target, viewportHeight - itemHeight)"),
  "PDF Go to bottom must land at the bottom edge of the final page");
assert(pdf.includes("override fun fitToWidth() = setZoom(1f)") &&
  pdf.includes("override fun zoomIn() = setZoom(zoomScale * 1.2f)") &&
  pdf.includes("override fun zoomOut() = setZoom(zoomScale / 1.2f)"),
  "PDF Zoom in/out and Fit must connect to the native page renderer");

assert(gradle.includes("window.vwSheetFitWidth = function ()") &&
  gradle.includes("window.vwSheetPreparePrint = function ()") &&
  gradle.includes("@media print") &&
  gradle.includes("current.pieces.length"),
  "Build must inject workbook fit/print support into the checksum-verified pinned XLSX renderer");

console.log("FZ Office document Tools audit checks passed.");
