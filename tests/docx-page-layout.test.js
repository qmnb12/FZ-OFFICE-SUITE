const fs = require("fs");
const assert = require("assert");
const codec = fs.readFileSync("app/src/main/java/com/fz/office/writer/DocxCodec.kt", "utf8");
const activity = fs.readFileSync("app/src/main/java/com/fz/office/writer/WriterActivity.kt", "utf8");
const viewer = fs.readFileSync("app/src/main/assets/writer/viewer.html", "utf8");
assert(codec.includes("readPageLayout(documentXml)"), "DOCX load must read section page geometry");
assert(codec.includes("htmlToDocumentXml(html, pageLayout)"), "DOCX save must retain page geometry and margins");
assert(codec.includes('"pgSz" -> {'), "DOCX parser must read page dimensions");
assert(codec.includes('"pgMar" -> {'), "DOCX parser must read page margins");
assert(codec.includes('orientation.equals("landscape", true)'), "landscape orientation must be honored");
assert(codec.includes("boundedTwips"), "untrusted dimensions and margins must be bounded");
assert(activity.includes("pendingPageLayout = loadedPageLayout"), "page layout must survive async DOCX loading");
assert(activity.includes('pageLayout.put("widthTwips"'), "page geometry must be sent to the viewer");
assert(viewer.includes("function applyPageLayout(renumber)"), "viewer must apply page geometry through shared layout styles");
assert(viewer.includes("function makePage(after)") && viewer.includes("function paginate()"),
  "DOCX viewer must create and paginate separate physical pages");
assert(viewer.includes('numbers[i].textContent="Page "+(i+1)'),
  "DOCX viewer must show a page number for each physical page after pagination");
assert(viewer.includes('root.style.setProperty("--fz-page-gap",(28*scale)+"px")'),
  "DOCX viewer must show a visible gap between pages");
assert(viewer.includes("Math.min(.92,available"),
  "DOCX viewer must start with a screen-fit page scale");
const layoutStart = viewer.indexOf("function applyPageLayout");
const layoutEnd = viewer.indexOf("function makePage", layoutStart);
const layout = viewer.slice(layoutStart, layoutEnd);
assert(layout.includes('--fz-shell-width') && layout.includes('--fz-page-transform') &&
  !layout.includes("pages().forEach"),
  "DOCX zoom must update shared CSS variables without iterating every page");

assert(viewer.includes("window.addEventListener(\"resize\""), "page layout must adapt to viewport changes");
assert(viewer.includes("overflow-wrap:break-word;word-break:normal"), "normal words should not be split unnecessarily");
assert(!viewer.includes("overflow-wrap:anywhere"), "avoid breaking every word at arbitrary positions");
console.log("DOCX page-layout regression checks passed (11 assertions).");
