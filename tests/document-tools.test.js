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

assert(gradle.includes('tasks.register("prepareHindiFonts")') &&
  gradle.includes('tasks.register("prepareGanderViewerAssets")') &&
  gradle.includes("offline-viewer-1.manifest") &&
  gradle.includes("offline-viewer-2.manifest") &&
  gradle.includes("font-licenses.manifest") &&
  gradle.includes("window.vwSheetFitWidth = function ()") &&
  gradle.includes("window.vwSheetPreparePrint = function ()") &&
  gradle.includes("@media print") &&
  gradle.includes("current.pieces.length"),
  "Build must prepare workbook fit/print support from repository-bundled assets");
assert(!gradle.includes("raw.githubusercontent.com") &&
  !gradle.includes("HttpURLConnection") &&
  !gradle.includes("java.net.URL") &&
  !gradle.includes("downloadGanderViewerAssets") &&
  !gradle.includes("downloadHindiFonts"),
  "Build must not fetch viewer assets or Hindi fonts from the network");

const crypto = require("crypto");
function gitBlobSha(bytes) {
  const header = Buffer.from("blob " + bytes.length + "\0", "utf8");
  return crypto.createHash("sha1").update(header).update(bytes).digest("hex");
}
function verifyManifest(file, expectedKind, expectedCount) {
  const lines = read(file).trim().split("\\n");
  assert.strictEqual(lines.length, expectedCount, "Unexpected asset count in " + file);
  for (const line of lines) {
    const parts = line.split("|");
    assert.strictEqual(parts.length, 5, "Malformed local asset manifest row");
    assert.strictEqual(parts[0], expectedKind, "Unexpected asset type in " + file);
    const bytes = Buffer.from(parts[4], "base64");
    assert.strictEqual(bytes.length, Number(parts[2]), "Asset size mismatch: " + parts[1]);
    assert.strictEqual(gitBlobSha(bytes), parts[3], "Asset checksum mismatch: " + parts[1]);
  }
}
verifyManifest("app/vendor/offline-viewer-1.manifest", "VIEWER", 16);
verifyManifest("app/vendor/offline-viewer-2.manifest", "VIEWER", 8);
verifyManifest("app/vendor/font-licenses.manifest", "LICENSE", 12);
const bundledFonts = [
  "NotoSansDevanagari-VF.ttf", "NotoSerifDevanagari-VF.ttf",
  "Mukta-Regular.ttf", "Mukta-Bold.ttf", "Hind-Regular.ttf", "Hind-Bold.ttf",
  "TiroDevanagariHindi-Regular.ttf", "TiroDevanagariHindi-Italic.ttf",
  "Kalam-Regular.ttf", "Sahitya-Regular.ttf", "Sahitya-Bold.ttf",
  "Martel-Regular.ttf", "Baloo2-VF.ttf", "Khand-Regular.ttf",
  "Gotu-Regular.ttf", "Glegoo-Regular.ttf"
];
for (const font of bundledFonts) {
  const encoded = read("app/vendor/fonts/" + font + ".b64").trim();
  assert(encoded.length > 1000, "Missing bundled Hindi font payload: " + font);
  assert(gradle.includes('"' + font + '"'), "Gradle is missing bundled font metadata: " + font);
}

console.log("FZ Office document Tools audit checks passed.");
