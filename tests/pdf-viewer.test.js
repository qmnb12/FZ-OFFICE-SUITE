"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");
const pdfJs = read("app/src/main/java/com/fz/office/pdf/PdfJsViewerActivity.kt");
const pdfManifest = read("app/vendor/offline-pdf-viewer-1.manifest");
const pdfWorkerManifest = read("app/vendor/offline-pdf-viewer-2.manifest");
const pdfWasmManifest = read("app/vendor/offline-pdf-viewer-3.manifest");
const pdfCMapManifests = Array.from({ length: 11 }, (_, index) =>
  read("app/vendor/offline-pdf-cmaps-" + String(index + 1).padStart(2, "0") + ".manifest")
);
const pdfCore = read("app/src/main/java/com/fz/office/pdf/FzPdfDocument.kt");
const appGradle = read("app/build.gradle.kts");
const settings = read("settings.gradle.kts");
const workflow = read(".github/workflows/android-build.yml");

assert(main.includes('"application/pdf"'), "PDF must be in the document picker");
assert(main.includes('lower.endsWith(".pdf")'), "PDF filename route is missing");
assert(main.includes("PdfJsViewerActivity::class.java"), "PDF must open in the Gander PDF.js reader");
assert(pdfJs.includes("PdfViewerActivity::class.java") && pdfJs.includes("supportsBundledPdfJs()"),
  "Older WebViews must retain the native PDF compatibility fallback");
assert(pdfJs.includes("pdf.html") && pdfWorkerManifest.includes("viewer/lib/pdf.worker.min.mjs") &&
  pdfJs.includes("wasm") && pdfJs.includes("blockNetworkLoads = true"),
  "PDF.js, worker and image decoders must load locally with network blocked");
assert(pdfJs.includes("createWebMessageChannel") && pdfJs.includes('postCommand("q$value")') &&
  pdfJs.includes('postCommand("g" +'),
  "PDF Find and page navigation must use the safe WebMessagePort bridge");
assert(pdfJs.includes("pendingFindNavigation") && pdfJs.includes("searchIndexReady") &&
  pdfJs.includes("queueFindNavigation") &&
  pdfJs.includes("if (ready && !searchIndexReady)") &&
  pdfJs.includes("pendingFindNavigation.clear()"),
  "PDF Previous/Next must wait for PDF.js full-document indexing before navigating");
assert(pdfJs.includes("PdfJsDoubleTapZoom") && pdfJs.includes("PdfJsWebView") &&
  pdfJs.includes("ValueAnimator") && pdfJs.includes("leftToPage") &&
  pdfJs.includes("computeHorizontalScrollRange"),
  "PDF double-tap zoom must be smooth, anchored and coordinated with PDF.js pinch-out");
assert(pdfJs.includes("setFitScale(baseScale)") &&
  pdfJs.includes("override fun pageScale(): Float = scale") &&
  pdfJs.includes("return (current / fitted)") &&
  pdfJs.includes("currentScale / initialScale"),
  "PDF zoom anchoring must use the actual WebView scale rather than infer zoom from scroll geometry");
assert(pdfJs.includes("multiTouchStream") &&
  pdfJs.includes("MotionEvent.ACTION_POINTER_DOWN") &&
  pdfJs.includes("cancelDetectorSequence(event)") &&
  pdfJs.includes("cancelEvent.action = MotionEvent.ACTION_CANCEL"),
  "The native PDF double-tap detector must cancel and ignore multi-touch gesture streams");
assert(appGradle.includes("fitViewportWidth = 0") &&
  appGradle.includes("rememberFitViewportWidth();") &&
  appGradle.includes("Math.max(8, fitWidth * 0.03)") &&
  appGradle.includes("Unexpected pinned Gander PDF pinch floor function."),
  "PDF pinch ownership must be calibrated against the actual fitted WebView viewport");
assert(appGradle.includes("shrink >= 1 && (!atFloor() || d >= g.apart)") &&
  appGradle.includes("Unexpected pinned Gander PDF pinch ownership check."),
  "Once PDF.js has taken ownership below fit width, it must keep handling the active pinch");
assert(appGradle.includes("e.touches.length >= 2") &&
  appGradle.includes("else if (pinch) endPinch();") &&
  appGradle.includes("if (pinch) {") &&
  appGradle.includes("e.touches.length !== 2") &&
  appGradle.includes("if (pinch) endPinch();"),
  "PDF multi-touch pointer-count changes and cancellation must always release the active pinch state");
assert(appGradle.includes("recordOperations: !page.recordedBBoxes") &&
  appGradle.includes("operationsFilter: tileOperationsFilter(slot, req)") &&
  appGradle.includes("boxes.isEmpty(index)") &&
  appGradle.includes("boxes.maxX(index) >= left") &&
  appGradle.includes("Unexpected pinned Gander PDF tile render options."),
  "PDF zoom tiles must filter the page display list to operations that intersect the visible tile");
assert(appGradle.includes("return r > 1 / TILE_GATE;") &&
  appGradle.includes("Unexpected pinned Gander PDF bitmap-size policy."),
  "PDF pinch-out must keep higher-resolution page bitmaps instead of rerendering them at gesture release");
assert(appGradle.includes("var TILE_SETTLE_MS = 300;") &&
  appGradle.includes("window.requestIdleCallback") &&
  appGradle.includes("window.cancelIdleCallback") &&
  appGradle.includes("Unexpected pinned Gander PDF tile scheduling function."),
  "PDF sharp-tile rendering must wait for a quiet period and begin during browser idle time");

assert(appGradle.includes("var MAX_TILES = 1;"),
  "PDF high-resolution tile rendering must remain single-flight for smoother frame pacing");

assert(pdfJs.includes("window.vwPdfShrink(1") &&
  pdfJs.includes("override fun zoomIn()") && pdfJs.includes("override fun zoomOut()") &&
  pdfJs.includes("view.zoomIn()") && pdfJs.includes("view.zoomOut()"),
  "PDF Tools zoom must coordinate the PDF.js shrink range and native WebView zoom");
assert(pdfJs.includes("Accept-Ranges") && pdfJs.includes("Content-Range") &&
  pdfJs.includes("openAssetFileDescriptor") && pdfJs.includes("openInputStream"),
  "Large PDFs must stream read-only with HTTP range support");
assert(!pdfJs.includes("openOutputStream") && pdfJs.includes("UriPdfPrintAdapter"),
  "The PDF.js reader must never write into the original PDF");
assert(pdfManifest.includes("viewer/pdf.html") && pdfManifest.includes("viewer/lib/pdf.min.mjs"),
  "Pinned Gander PDF.js page and core must be bundled");
assert(pdfWorkerManifest.includes("viewer/lib/pdf.worker.min.mjs"),
  "The matching pinned PDF.js worker must be bundled");
assert(pdfWasmManifest.includes("viewer/lib/wasm/openjpeg.wasm") &&
  pdfWasmManifest.includes("viewer/lib/wasm/jbig2.wasm"),
  "PDF.js JPEG 2000 and JBIG2 decoders must be bundled");

assert(pdf.includes("FzPdfDocument.open(this, uri)"), "PDF activity must use the FZ Kotlin document core");
assert(pdf.includes("pageSizeKnown") && pdf.includes("requestPageSize(position)"),
  "PDF page dimensions must be resolved lazily as pages are bound");
assert(pdf.includes("MutableList(pageCount) { initialSize }"),
  "Unmeasured pages must temporarily preserve the first page aspect ratio");
assert(pdf.includes("searchExecutor.execute") && pdf.includes("pendingPageSizes"),
  "PDF text search and lazy page-size requests must not block each other on the render queue");
const openStart = pdf.indexOf("private fun openPdf()");
const openEnd = pdf.indexOf("private inner class PdfGestureHost", openStart);
const openBlock = pdf.slice(openStart, openEnd);
assert(!openBlock.includes("for (index in 0 until opened.pageCount)"),
  "Opening a PDF must not synchronously inspect every page before first paint");
assert(pdf.includes("RecyclerView") && pdf.includes("LinearLayoutManager"),
  "PDF pages must use a continuous, vertically scrolling page list");
assert(!pdf.includes("PDFView") && !pdf.includes("FitPolicy") &&
  !pdf.includes("com.github.barteksc") && !pdf.includes("PdfiumAndroid"),
  "PDF activity must not directly use MJ PDF/PDFium classes");
assert(pdf.includes("supportsFind: Boolean = Build.VERSION.SDK_INT >= 35") &&
  pdf.includes("findPdfText(query, forward)") && pdf.includes("PdfMatchOverlayView"),
  "PDF Find must use Android 15+ text search and highlight the current match");
assert(pdf.includes("renderPage(page, renderWidth)") && pdf.includes("bitmapCache"),
  "PDF page rendering must be demand-driven and cached");
assert(pdf.includes("ScaleGestureDetector") && pdf.includes("onDoubleTap") &&
  pdf.includes("class PdfGestureHost") && pdf.includes("applyPreviewZoom(previewZoom)"),
  "PDF pinch zoom must observe touches above page scrollers and preview continuously");
assert(pdf.includes("previewZoom * detector.scaleFactor") &&
  pdf.includes("event.pointerCount >= 2 || scaleDetector.isInProgress"),
  "PDF pinch zoom must accumulate incremental scale factors and intercept the two-finger stream");
assert(pdf.includes("captureHorizontalScrollFractions") &&
  pdf.includes("restoreHorizontalScrollFractions") &&
  pdf.includes("scroller.scrollTo((range * fraction).roundToInt(), 0)"),
  "PDF zoom must retain each visible page's horizontal position after its width is re-laid out");
assert(pdf.includes("horizontalScrollFractionsByPage") &&
  pdf.includes("setOnScrollChangeListener") &&
  pdf.includes("val savedFraction = horizontalScrollFractionsByPage[position] ?: 0f"),
  "PDF must remember horizontal position per page when RecyclerView recycles and rebinds pages");
assert(pdf.includes("val verticalOffset = anchorView?.top?.minus(recycler.paddingTop) ?: 0") &&
  pdf.includes("scrollToPositionWithOffset(anchor, verticalOffset)"),
  "PDF zoom must preserve the visible vertical position within the current page");
assert(pdf.includes("refreshVisibleSearchHighlights()") &&
  !pdf.includes("pageAdapter?.notifyDataSetChanged()\n        updatePageStatus()"),
  "PDF Find highlight refreshes must not rebind pages and reset horizontal scroll");
assert(!pdf.includes("recycler.setOnTouchListener"),
  "PDF pinch gesture handling must not depend on the RecyclerView touch listener");
assert(pdf.includes("ReaderDashboard") && pdf.includes("dashboard.setDocumentView(zoomHost)"),
  "PDF reader must retain the shared FZ dashboard and the pinch-aware gesture host");
assert(pdf.includes("jumpToPage") && pdf.includes("supportsPageNavigation") && pdf.includes("supportsScrollNavigation"),
  "Direct page navigation must be exposed through the shared document controller");
assert(pdf.includes("jumpToPageEnd(pageSizes.lastIndex)") &&
  pdf.includes("val itemHeight = displayHeight + dp(22)") &&
  pdf.includes("layout.scrollToPositionWithOffset(target, viewportHeight - itemHeight)"),
  "PDF Go to bottom must align the full last-page item at the viewport bottom");
assert(!pdf.includes('addToolbarAction("Pages"') &&
  !pdf.includes("toggleBookmark") && !pdf.includes("showBookmarks"),
  "PDF page previews and bookmarks must stay removed");
assert(pdf.includes("DocumentViewerController") && pdf.includes("UriPdfPrintAdapter"),
  "PDF share and print actions must come from the shared document controller");
assert(pdf.includes("applySafeAreaInsets") && pdf.includes("onSaveInstanceState"),
  "System insets and the last page/zoom position must be retained");
assert(!pdf.includes("openOutputStream(documentUri)"),
  "PDF reader must never write changes to the original document");

assert(pdfCore.includes("android.graphics.pdf.PdfRenderer"), "The FZ PDF core must use Android PdfRenderer");
assert(pdfCore.includes("MAX_BITMAP_PIXELS"), "The FZ PDF core must keep a memory bound");
assert(pdfCore.includes("copyToPrivateCache"), "The FZ PDF core must handle non-seekable SAF providers");
assert(appGradle.includes('implementation("androidx.recyclerview:recyclerview:1.4.0")'),
  "RecyclerView must remain available for the native compatibility reader");
assert(appGradle.includes("offline-pdf-viewer-1.manifest") &&
  appGradle.includes("offline-pdf-viewer-2.manifest") &&
  appGradle.includes("offline-pdf-viewer-3.manifest") &&
  Array.from({ length: 11 }, (_, index) =>
    appGradle.includes("offline-pdf-cmaps-" + String(index + 1).padStart(2, "0") + ".manifest")
  ).every(Boolean) &&
  appGradle.includes('opts.cMapUrl = vwAssetUrl("./lib/cmaps/");') &&
  !appGradle.includes('script = script.replace(cMapConfig, "")') &&
  appGradle.includes('versionName = "0.9.8"') && appGradle.includes("versionCode = 38"),
  "PDF.js assets and all CMaps must be checksum-verified and included in the new app version");
assert(!appGradle.includes("third_party:mj_pdf") && !settings.includes("mj_pdf") &&
  !settings.includes("PdfiumAndroid"),
  "The app and Gradle settings must not include the unused MJ PDF/PDFium modules");
assert(!workflow.includes("PDFium") && !workflow.includes("MJ PDF") &&
  !workflow.includes("buildMjPdfNativeDependencies") && !workflow.includes("ndk;30.0.14904198"),
  "CI must not download or build unused PDFium native dependencies");
assert(!fs.existsSync(path.join(root, "third_party/mj_pdf_prebuilt")),
  "Unused PDFium native binaries must not remain in the repository");

function readManifestRows(content, file) {
  const rows = [];
  let pending = "";
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (line.includes("|")) {
      if (pending) rows.push(pending);
      pending = line;
    } else {
      assert(pending, "Manifest continuation without an asset row in " + file);
      pending += line;
    }
  }
  if (pending) rows.push(pending);
  return rows;
}

function gitBlobSha(bytes) {
  const header = Buffer.from("blob " + bytes.length + "\0", "utf8");
  return crypto.createHash("sha1").update(header).update(bytes).digest("hex");
}

const cmapRows = pdfCMapManifests.flatMap((manifest, index) =>
  readManifestRows(manifest, "offline-pdf-cmaps-" + (index + 1))
);
assert.strictEqual(cmapRows.length, 168, "The complete Gander CMap set must be bundled");
const cmapPaths = new Set();
for (const row of cmapRows) {
  const fields = row.split("|");
  assert.strictEqual(fields.length, 5, "Malformed CMap manifest row");
  assert.strictEqual(fields[0], "VIEWER", "CMap manifest entry must be a viewer asset");
  assert(/^viewer\/lib\/cmaps\/[^/]+\.bcmap$/.test(fields[1]),
    "Unexpected CMap output path: " + fields[1]);
  assert(!cmapPaths.has(fields[1]), "Duplicate CMap asset: " + fields[1]);
  cmapPaths.add(fields[1]);
  const bytes = Buffer.from(fields[4], "base64");
  assert.strictEqual(bytes.length, Number(fields[2]), "CMap asset size mismatch: " + fields[1]);
  assert.strictEqual(gitBlobSha(bytes), fields[3], "CMap asset checksum mismatch: " + fields[1]);
}
for (const required of ["Adobe-GB1-UCS2.bcmap", "UniGB-UCS2-H.bcmap", "UniJIS-UCS2-H.bcmap"]) {
  assert(cmapPaths.has("viewer/lib/cmaps/" + required), "Missing required CMap: " + required);
}
const pdfLicenceRow = readManifestRows(pdfManifest, "offline-pdf-viewer-1")
  .find(row => row.split("|")[1] === "licences.md");
assert(pdfLicenceRow, "Gander's upstream third-party notices must be bundled");
const pdfLicenceText = Buffer.from(pdfLicenceRow.split("|")[4], "base64").toString("utf8");
assert(pdfLicenceText.includes("Adobe CMap resources") && pdfLicenceText.includes("BSD-3-Clause"),
  "Adobe CMap attribution must ship with the bundled CMap tables");

console.log("FZ PDF.js routing, zoom, search, offline assets and CMap integrity checks passed.");
