"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const home = read("app/src/main/java/com/fz/office/MainActivity.java");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.java");
const office = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.java");
const docxViewer = read("app/src/main/assets/writer/viewer.html");

assert(home.includes("applySafeAreaInsets(scroll)"), "Home screen must apply system-bar insets");
assert(writer.includes("applySafeAreaInsets(root)"), "DOCX/TXT screen must apply system-bar insets");
assert(office.includes("applySafeAreaInsets(root)"), "XLSX/PPTX screen must apply system-bar insets");

for (const source of [home, writer, office]) {
  assert(source.includes("setOnApplyWindowInsetsListener"), "Missing Android edge-to-edge inset listener");
  assert(source.includes("getSystemWindowInsetTop()"), "Top status-bar inset is not applied");
  assert(source.includes("getSystemWindowInsetBottom()"), "Bottom navigation inset is not applied");
  assert(source.includes("consumeSystemWindowInsets()"), "Insets should not be applied twice by child views");
}

assert(docxViewer.includes('page.style.width=widthPx+"px"'), "DOCX page must retain its real paper width before scaling");
assert(docxViewer.includes('page.style.zoom=(pageScale*zoomLevel)'), "Whole DOCX page must scale to fit the viewport");
assert(docxViewer.includes('page.style.fontSize="16px"'), "Base font size must not be scaled separately");
assert(!docxViewer.includes("16*pageScale*zoomLevel"), "Font-only scaling would leave explicit runs inconsistent");

console.log("System-bar safe areas and DOCX whole-page zoom checks passed.");
