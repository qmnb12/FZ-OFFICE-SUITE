"use strict";
const assert = require("assert");
const fs = require("fs");
const read = (p) => fs.readFileSync(p, "utf8");
const writer = read("app/src/main/java/com/fz/office/writer/WriterActivity.kt");
const manifest = read("app/src/main/AndroidManifest.xml");
const main = read("app/src/main/java/com/fz/office/MainActivity.java");

assert(writer.includes("VIEW ONLY"), "DOCX reader must clearly identify its read-only mode");
assert(!writer.includes('control("Edit"'), "DOCX viewer must not expose an Edit action");
assert(!writer.includes("openEditor("), "DOCX viewer must not route to a Writer editor");
assert(!writer.includes("loadedDocumentForEdit"), "Viewer must not retain a duplicate DOCX package for editing");
assert(!writer.includes("setPreloadedDocument"), "Viewer must not prepare an editor hand-off");
assert(!manifest.includes(".writer.WriterEditorActivity"), "DOCX editor must not be registered");
assert(main.includes("WriterActivity.class"), "DOCX/TXT reader route must remain available");
assert(!main.includes("WriterEditorActivity"), "Main routing must not reference the DOCX editor");
console.log("FZ DOCX/TXT read-only routing checks passed.");
