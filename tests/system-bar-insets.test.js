"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const home = read("app/src/main/java/com/fz/office/MainActivity.kt");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const office = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.kt");
const docxViewer = read("app/src/main/assets/writer/viewer.html");

assert(home.includes("applySafeAreaInsets(scroll)"), "Home screen must apply system-bar insets");
assert(writer.includes("applySafeAreaInsets(shell.root)"), "DOCX/TXT dashboard must apply system-bar insets");
assert(office.includes("applySafeAreaInsets(shell.root)"), "XLSX/PPTX dashboard must apply system-bar insets");

for (const source of [home, writer, office]) {
  assert(source.includes("setOnApplyWindowInsetsListener"), "Missing Android edge-to-edge inset listener");
  assert(source.includes("systemWindowInsetTop"), "Top status-bar inset is not applied");
  assert(source.includes("systemWindowInsetBottom"), "Bottom navigation inset is not applied");
  assert(source.includes("consumeSystemWindowInsets"), "Insets should not be applied twice by child views");
}

assert(docxViewer.includes('root.style.setProperty("--fz-page-width",width+"px")'), "DOCX page must retain its real paper width before scaling");
assert(docxViewer.includes('root.style.setProperty("--fz-page-height",height+"px")'), "DOCX page must retain its real paper height before scaling");
assert(docxViewer.includes('root.style.setProperty("--fz-page-transform","scale("+scale+")")'), "Whole DOCX page must scale uniformly to fit the viewport");
assert(docxViewer.includes('Math.min(.92,available/Math.max(1,width))*zoomLevel'), "Viewer must start with screen-fit zoom while retaining page dimensions");
assert(docxViewer.includes('root.style.setProperty("--fz-page-gap",(28*scale)+"px")'), "Physical pages must have a visible gap");

console.log("System-bar safe areas and DOCX whole-page zoom checks passed.");
