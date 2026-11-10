"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const activity = read("app/src/main/java/com/fz/office/viewer/SpreadsheetEditorActivity.kt");
const html = read("app/src/main/assets/writer/sheet-editor.html");
const js = read("app/src/main/assets/writer/sheet-editor.js");
const viewer = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.kt");
const manifest = read("app/src/main/AndroidManifest.xml");
assert(viewer.includes("SpreadsheetEditorActivity::class.java"), "XLSX viewer Edit action must launch our editor");
assert(viewer.includes("editableXlsx && !presentation"), "Only .xlsx should be editable");
assert(activity.includes("ACTION_CREATE_DOCUMENT"), "XLSX Save must create a separate copy");
assert(activity.includes("allowFileAccess = false") && activity.includes("allowContentAccess = false"),
  "Spreadsheet WebView must not expose file/content URLs");
assert(html.includes("xlsx.full.min.js") && html.includes("sheet-editor.js"),
  "Spreadsheet screen must load the XLSX codec and custom UI");
assert(js.includes("XLSX.read") && js.includes("XLSX.write"), "Spreadsheet editor must read/write workbook data");
assert(js.includes("formulaInput") && js.includes("data-address"), "Spreadsheet editor must support cell/formula editing");
assert(js.includes("AndroidBridge.saveWorkbook"), "Save Copy must call the Android Save As bridge");
assert(html.includes("touch-action:pan-x pan-y"),
  "Spreadsheet grid must preserve single-finger panning while allowing custom pinch gestures");
assert(js.includes("function applySheetZoom") &&
  js.includes("table.style.zoom=String(sheetZoom)"),
  "XLSX editor must scale only the worksheet grid while keeping the formula bar and toolbar fixed");
assert(js.includes('addEventListener("touchstart",handlePinchStart') &&
  js.includes('addEventListener("touchmove",handlePinchMove') &&
  js.includes("event.preventDefault()") &&
  js.includes("Sheet zoom "),
  "XLSX editing mode must handle two-finger pinch-in and pinch-out without browser page zoom");
assert(manifest.includes(".viewer.SpreadsheetEditorActivity"), "Spreadsheet editor must be registered");
console.log("FZ Sheets custom grid, XLSX read/write and copy-only save checks passed.");
