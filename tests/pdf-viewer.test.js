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
assert(pdf.includes("ScaleGestureDetector") && pdf.includes("onDoubleTap"),
  "Pinch zoom and double-tap zoom must be available");
assert(pdf.includes("ReaderDashboard") && pdf.includes("dashboard.setDocumentView(recycler)"),
  "PDF reader must retain the shared FZ dashboard");
assert(pdf.includes("jumpToPage") && pdf.includes("supportsPageNavigation") && pdf.includes("supportsScrollNavigation"),
  "Direct page navigation must be exposed through the shared document controller");
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
