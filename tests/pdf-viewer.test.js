"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");
const appGradle = read("app/build.gradle.kts");
const settings = read("settings.gradle.kts");
const workflow = read(".github/workflows/android-build.yml");
const notices = read("third_party/README.md");

assert(main.includes('"application/pdf"'), "PDF must be in the document picker");
assert(main.includes('lower.endsWith(".pdf")'), "PDF filename route is missing");
assert(main.includes("PdfViewerActivity::class.java"), "PDF must open in the built-in reader");

assert(pdf.includes("PDFView(this, null)"), "MJ PDFView must be the active PDF renderer");
assert(pdf.includes("view.fromUri(") && pdf.includes("view.fromFile("),
  "PDF must open from SAF Uri and retain a private-cache fallback");
assert(pdf.includes("FitPolicy.WIDTH"), "PDF pages must initially fit the available width");
assert(pdf.includes(".enableSwipe(true)") && pdf.includes(".swipeHorizontal(false)"),
  "Continuous vertical page scrolling must be enabled");
assert(pdf.includes(".enableDoubleTap(true)") && pdf.includes(".enableTextSelection(true)"),
  "MJ PDF gestures and text selection must remain available");
assert(pdf.includes(".onLoad") && pdf.includes(".onPageChange") && pdf.includes(".onError"),
  "PDF loading, page progress and failure states must be handled");
assert(!pdf.includes("android.graphics.pdf.PdfRenderer") &&
  !pdf.includes("RecyclerView") &&
  !pdf.includes("ScaleGestureDetector") &&
  !pdf.includes("PdfHorizontalPanScrollView"),
  "The legacy custom PDF renderer and gesture stack must be removed");

assert(pdf.includes("ReaderDashboard") && pdf.includes("dashboard.setDocumentView(view)"),
  "PDF reader must retain the shared FZ dashboard");
assert(pdf.includes("showPages") && pdf.includes("requestThumbnail"),
  "The page grid must retain generated page previews");
assert(pdf.includes("showGoToPage") && pdf.includes("jumpToPage"),
  "Direct page navigation must remain available");
assert(pdf.includes("toggleBookmark") && pdf.includes("showBookmarks"),
  "Per-document bookmarks must remain available");
assert(pdf.includes("shareDocument"), "PDF sharing must remain available");
assert(pdf.includes("copyToPrivateCache") && pdf.includes("MAX_PDF_BYTES"),
  "Non-seekable providers must keep a size-bounded private-cache fallback");
assert(pdf.includes("applySafeAreaInsets") && pdf.includes("onSaveInstanceState"),
  "System insets and the last page/zoom position must be retained");
assert(!pdf.includes("openOutputStream(documentUri)"),
  "PDF reader must never write changes to the original document");

assert(appGradle.includes('implementation(project(":third_party:mj_pdf:AndroidPdfViewer:android-pdf-viewer"))'),
  "FZ app must depend on the MJ PDF viewer module");
assert(appGradle.includes('implementation(project(":third_party:mj_pdf:PdfiumAndroid"))'),
  "FZ app must directly depend on PdfiumAndroid for PDF thumbnails and metadata");

assert(appGradle.includes('versionCode = 31') && appGradle.includes('versionName = "0.9.1"'),
  "The MJ PDF integration must advance the app version");

assert(settings.includes('include(":third_party:mj_pdf:PdfiumAndroid")') &&
  settings.includes('include(":third_party:mj_pdf:AndroidPdfViewer:android-pdf-viewer")'),
  "Both Apache-licensed MJ PDF modules must be included");
assert(settings.includes("repository/archive.zip?sha=") &&
  settings.includes("mjPdfRevision") &&
  settings.includes("buildMjPdfNativeDependencies"),
  "The pinned upstream sources and native build step must be bootstrapped");
const bootstrapStart = settings.indexOf("fun patchMjPdfModuleBuildFiles(root: File)");
const bootstrapEnd = settings.indexOf("val mjPdfAlreadyPrepared");
const bootstrapPatch = settings.slice(bootstrapStart, bootstrapEnd);
assert(bootstrapPatch.includes("DefaultScrollHandle.java") &&
  bootstrapPatch.includes("com.google.android.material.R.attr.colorOnSurface"),
  "MJ PDF Material color attribute compatibility patch must run during source bootstrap.");

assert(workflow.includes('ndk;30.0.14904198') && workflow.includes("python3-requests"),
  "CI must install the NDK and Python native-build dependency");
assert(notices.includes("Apache License 2.0") && notices.includes("GPLv3") &&
  notices.includes("prebuilt"),
  "Third-party license and native-build notices must be documented");

console.log("MJ PDFium integration checks passed.");
