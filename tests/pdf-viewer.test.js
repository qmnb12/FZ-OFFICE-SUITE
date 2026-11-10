"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const main = read("app/src/main/java/com/fz/office/MainActivity.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");
const gradle = read("app/build.gradle.kts");

assert(main.includes('"application/pdf"'), "PDF must be in the document picker");
assert(main.includes('lower.endsWith(".pdf")'), "PDF filename route is missing");
assert(main.includes("PdfViewerActivity::class.java"), "PDF must open in the built-in reader");
assert(pdf.includes("PdfRenderer(opened") || pdf.includes("PdfRenderer(opened)"),
  "PDF reader must use Android PdfRenderer");
assert(pdf.includes("RENDER_MODE_FOR_DISPLAY"), "PDF page render mode is missing");
assert(pdf.includes("MAX_BITMAP_PIXELS") && pdf.includes("MAX_PAGE_DIMENSION"),
  "PDF bitmap memory and dimensions must be bounded");
assert(pdf.includes("copyToPrivateCache") && pdf.includes("MAX_PDF_BYTES"),
  "Non-seekable content providers must use a size-bounded private cache copy");
assert(pdf.includes("RecyclerView") && pdf.includes("PdfPageAdapter"),
  "PDF pages must use a recycling list, not a single-page ImageView");
assert(pdf.includes("requestPageRender(position, renderBucket)"),
  "Page bitmaps must be rendered on demand for bound pages");
assert(pdf.includes("LruCache<String, Bitmap>") && pdf.includes("pageCacheSizeKb()"),
  "Rendered page bitmaps must be kept in a bounded LRU cache");
assert(pdf.includes("qualityBucket") && pdf.includes("bestCachedPage"),
  "Zoom must use render-quality buckets and keep a previous bitmap while sharper output loads");
assert(pdf.includes("ScaleGestureDetector"), "Pinch-to-zoom must be supported");
assert(pdf.includes("override fun onScaleBegin") && pdf.includes("override fun onScaleEnd"),
  "Pinch zoom must preview with a transform and commit once on gesture end");
assert(pdf.includes("list.scaleX = previewScale") && pdf.includes("list.scaleY = previewScale"),
  "Pinch movement must scale the cached page layer without re-layout");
const scaleStart = pdf.indexOf("override fun onScale(detector: ScaleGestureDetector)");
const scaleEnd = pdf.indexOf("override fun onScaleEnd(detector: ScaleGestureDetector)", scaleStart);
assert(scaleStart >= 0 && scaleEnd > scaleStart &&
  !pdf.slice(scaleStart, scaleEnd).includes("setZoom("),
  "Pinch motion must not trigger expensive page layout on every event");
assert(pdf.includes("refreshForZoom") && pdf.includes("notifyItemRangeChanged"),
  "Page holders must refresh only once after zoom settles");
assert(pdf.includes("visibleStartPage") && pdf.includes("visibleEndPage") &&
  pdf.includes("obsolete pages queued"),
  "Out-of-view PDF render jobs must be skipped after fast scrolling");
assert(pdf.includes("showThumbnails") && pdf.includes("requestThumbnail"),
  "A lazily rendered page thumbnail grid is required");
assert(pdf.includes("showGoToPage") && pdf.includes("jumpToPage"),
  "Direct page navigation must be available");
assert(pdf.includes("toggleBookmark") && pdf.includes("showBookmarks"),
  "Per-document bookmarks must be available");
assert(pdf.includes("shareDocument"), "PDF sharing must be available");
assert(pdf.includes("Original file unchanged") || pdf.includes("Read only"),
  "PDF reader must preserve the source file");
assert(pdf.includes("applySafeAreaInsets(root)"), "PDF reader must respect system bars");
assert(!pdf.includes("openOutputStream(documentUri)"), "PDF reader must not write to the original file");
assert(gradle.includes('implementation("androidx.recyclerview:recyclerview:1.4.0")'),
  "Continuous PDF page list dependency is missing");
console.log("Offline continuous PDF reader checks passed.");
