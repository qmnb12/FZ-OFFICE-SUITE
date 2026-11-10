"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const engine = read("app/src/main/java/com/fz/office/pdf/FzPdfDocument.kt");
assert(engine.includes("android.graphics.pdf.PdfRenderer"), "FZ PDF core must use Android's platform PdfRenderer");
assert(engine.includes("ParcelFileDescriptor"), "FZ PDF core must own the seekable descriptor");
assert(engine.includes('contentResolver.openFileDescriptor(uri, "r")'), "FZ PDF core must first try the original SAF descriptor");
assert(engine.includes("copyToPrivateCache"), "Non-seekable provider fallback must be available");
assert(engine.includes("DEFAULT_MAX_PDF_BYTES"), "PDF cache copies must have a size limit");
assert(engine.includes("MAX_BITMAP_PIXELS"), "Page raster dimensions must have a memory bound");
assert(engine.includes("page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)"),
  "The FZ PDF core must render pages through Android PdfRenderer");
assert(engine.includes("@Synchronized"), "PdfRenderer access must be serialized");
assert(engine.includes("cachedCopy?.delete()"), "Temporary cache files must be deleted when the document closes");
assert(!engine.includes("com.github.barteksc") && !engine.includes("PdfiumAndroid"),
  "FZ PDF core must not depend on MJ PDF/PDFium");

console.log("FZ platform PDF core checks passed.");
