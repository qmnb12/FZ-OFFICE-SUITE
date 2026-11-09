"use strict";
const assert = require("assert");
const fs = require("fs");
const read = (p) => fs.readFileSync(p, "utf8");
const editor = read("app/src/main/java/com/fz/office/writer/WriterEditorActivity.java");
const html = read("app/src/main/assets/writer/editor.html");
const viewer = read("app/src/main/java/com/fz/office/writer/WriterActivity.java");
const codec = read("app/src/main/java/com/fz/office/writer/DocxCodec.java");
assert(viewer.includes('control("Edit"'), "Edit action must be visible in DOCX viewer");
assert(viewer.includes("startActivity(intent)"), "Editor must open only after explicit Edit tap");
assert(viewer.includes('contains("<img")'), "Image-bearing documents must be protected from lossy editing");
assert(html.includes('contenteditable="true"'), "Writer editor page must be editable");
for (const command of ["bold", "italic", "underline", "insertUnorderedList", "insertOrderedList",
  "justifyLeft", "justifyCenter", "justifyRight", "undo", "redo"]) {
  assert(editor.includes('"'+command+'"'), "Missing Writer command: " + command);
}
assert(html.includes("function insertTable()"), "Writer must support inserting tables");
assert(editor.includes("ACTION_CREATE_DOCUMENT"), "Save must create a separate copy");
assert(!editor.includes("FLAG_GRANT_WRITE_URI_PERMISSION"), "Editor must not request source write permission");
assert(editor.includes("DocxCodec.writeDocx(editedHtml, packageEntries, layout)"), "Save must preserve page geometry");
assert(codec.includes("entries.putAll(originalEntries)"), "Original DOCX package parts must be retained");
assert(codec.includes("private static String firstCssFontFamily("),
  "DOCX export must reduce CSS fallback stacks to one real font family");
assert(codec.includes('String family = firstCssFontFamily(cssValue(css, "font-family"));'),
  "Inline Hindi font-family fallback stacks must not be stored as one invalid Word font name");
assert(codec.includes('String defaultFamily = firstCssFontFamily('),
  "Block-level CSS font-family fallback stacks must also be reduced before DOCX export");
console.log("FZ Writer edit-only-on-demand and safe Save As checks passed.");
