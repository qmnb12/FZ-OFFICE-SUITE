"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const docx = read("app/src/main/assets/writer/viewer.html");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const office = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.kt");
const controller = read("app/src/main/java/com/fz/office/viewer/DocumentViewerController.kt");
const pdf = read("app/src/main/java/com/fz/office/pdf/PdfViewerActivity.kt");
const gradle = read("app/build.gradle.kts");
const ganderAssets = read("app/vendor/offline-viewer-1.manifest") +
  read("app/vendor/offline-viewer-2.manifest");

assert(docx.includes("touch-action:pan-x pan-y pinch-zoom"),
  "DOCX must leave both-axis panning and pinch zoom enabled");
assert(docx.includes("body{display:block;min-height:100vh;overflow:visible"),
  "DOCX must scroll at the document viewport rather than inside a clipped body");
assert(docx.includes("#viewer-scroll{width:100%;min-height:100vh;overflow:visible"),
  "DOCX page canvas must not trap horizontal pan in a nested scroll container");
assert(docx.includes("window.scrollY") && docx.includes("scrollIntoView"),
  "DOCX page tracking and page navigation must follow root document scrolling");
assert(writer.includes("builtInZoomControls = true") &&
  writer.includes("displayZoomControls = false") &&
  writer.includes("setSupportZoom(true)"),
  "DOCX/TXT must retain native WebView pinch zoom without redundant zoom buttons");

assert(office.includes("settings.setSupportZoom(true)") &&
  office.includes("settings.builtInZoomControls = true") &&
  office.includes("settings.useWideViewPort = true") &&
  office.includes("settings.loadWithOverviewMode = true"),
  "PPTX/XLSX must retain WebView zoom and wide viewport support");
assert(ganderAssets.includes("viewer/pptx.html") &&
  ganderAssets.includes("viewer/xlsx.html") &&
  ganderAssets.includes("viewer/xlsx.js") &&
  ganderAssets.includes("viewer/pptx.js"),
  "PPTX/XLSX two-axis pan and their format-specific zoom handlers must stay bundled");
assert(controller.includes("class WebViewDocumentSurface") &&
  controller.includes("view.pageUp(true)") && controller.includes("view.pageDown(true)"),
  "DOCX/TXT/PPTX/XLSX must retain shared vertical scroll navigation");

assert(pdf.includes("HorizontalScrollView(this@PdfViewerActivity)") &&
  pdf.includes("displayWidth = (baseWidth * zoomScale)") &&
  pdf.includes("captureHorizontalScrollFractions") &&
  pdf.includes("restoreHorizontalScrollFractions"),
  "PDF must expose real per-page horizontal panning and preserve it when zoom changes");
assert(pdf.includes("class PdfGestureHost") &&
  pdf.includes("ScaleGestureDetector") &&
  pdf.includes("event.pointerCount >= 2 || scaleDetector.isInProgress"),
  "PDF two-finger pinch must remain isolated from one-finger page scrolling");

console.log("FZ Office cross-format zoom and two-axis scrolling checks passed.");
