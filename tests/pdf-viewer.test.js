"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");
const pdfCore = read("app/src/main/java/com/fz/office/pdf/FzPdfDocument.kt");
const appGradle = read("app/build.gradle.kts");
const settings = read("settings.gradle.kts");
const workflow = read(".github/workflows/android-build.yml");

assert(main.includes('"application/pdf"'), "PDF must be in the document picker");
assert(main.includes('lower.endsWith(".pdf")'), "PDF filename route is missing");
assert(main.includes("PdfViewerActivity::class.java"), "PDF must open in the built-in reader");

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
  "RecyclerView must be available for the native PDF page list");
assert(!appGradle.includes("third_party:mj_pdf") && !settings.includes("mj_pdf") &&
  !settings.includes("PdfiumAndroid"),
  "The app and Gradle settings must not include the unused MJ PDF/PDFium modules");
assert(!workflow.includes("PDFium") && !workflow.includes("MJ PDF") &&
  !workflow.includes("buildMjPdfNativeDependencies") && !workflow.includes("ndk;30.0.14904198"),
  "CI must not download or build unused PDFium native dependencies");
assert(!fs.existsSync(path.join(root, "third_party/mj_pdf_prebuilt")),
  "Unused PDFium native binaries must not remain in the repository");

console.log("FZ platform PDF viewer integration checks passed.");
