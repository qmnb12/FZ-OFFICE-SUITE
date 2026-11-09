"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const activity = read("app/src/main/java/com/fz/office/viewer/SpreadsheetEditorActivity.java");
const html = read("app/src/main/assets/writer/sheet-editor.html");
const js = read("app/src/main/assets/writer/sheet-editor.js");
const viewer = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.java");
const manifest = read("app/src/main/AndroidManifest.xml");
assert(viewer.includes("SpreadsheetEditorActivity.class"), "XLSX viewer Edit action must launch our editor");
assert(viewer.includes("editableXlsx && !presentation"), "Only .xlsx should be editable");
assert(activity.includes("ACTION_CREATE_DOCUMENT"), "XLSX Save must create a separate copy");
assert(activity.includes("setAllowFileAccess(false)") && activity.includes("setAllowContentAccess(false)"),
  "Spreadsheet WebView must not expose file/content URLs");
assert(html.includes("xlsx.full.min.js") && html.includes("sheet-editor.js"),
  "Spreadsheet screen must load the XLSX codec and custom UI");
assert(js.includes("XLSX.read") && js.includes("XLSX.write"), "Spreadsheet editor must read/write workbook data");
assert(js.includes("formulaInput") && js.includes("data-address"), "Spreadsheet editor must support cell/formula editing");
assert(js.includes("AndroidBridge.saveWorkbook"), "Save Copy must call the Android Save As bridge");
assert(manifest.includes(".viewer.SpreadsheetEditorActivity"), "Spreadsheet editor must be registered");
console.log("FZ Sheets custom grid, XLSX read/write and copy-only save checks passed.");