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

assert(activity.includes('addToolbarAction("Size", "Choose Word page size")'),
  "Word viewer must expose a page-size option");
assert(activity.includes("showPageSizeOptions()") && activity.includes("selectedPageSizeKey"),
  "Word page-size selection must be retained across document/font loads");
assert(viewer.includes("function setPageSize(preset)") &&
  viewer.includes("a4:[11906,16838]") &&
  viewer.includes("a4_landscape:[16838,11906]") &&
  viewer.includes("letter:[12240,15840]") &&
  viewer.includes("legal:[12240,20160]") &&
  viewer.includes("a3:[16838,23811]") &&
  viewer.includes("a5:[8391,11906]"),
  "Word viewer must support standard paper-size presets");
assert(viewer.includes("originalPageLayout:copyLayout(original)") &&
  viewer.includes("renderDocument(lastDocument,false)"),
  "Changing paper size must repaginate from the untouched source document and retain Original");
assert(viewer.includes("window.addEventListener(\"resize\""), "page layout must adapt to viewport changes");
assert(viewer.includes("overflow-wrap:break-word;word-break:normal"), "normal words should not be split unnecessarily");
assert(codec.includes('"spacing" ->') && codec.includes('"ind" ->') &&
  codec.includes('lineRule') && codec.includes('margin-top:') &&
  codec.includes('text-indent:') && codec.includes('resolveParagraphCss'),
  "DOCX parser must preserve Word paragraph spacing, line height, indentation and inherited styles");
assert(codec.includes('"keepNext" ->') && codec.includes('"keepLines" ->') &&
  codec.includes('data-fz-keep-next') && codec.includes('data-fz-keep-lines'),
  "Word keep-with-next and keep-lines rules must reach the page layout engine");
assert(viewer.includes('font-size:11pt;line-height:1.15') &&
  viewer.includes('.fz-page p,.fz-page div{margin:0}'),
  "Word page defaults must use Word-like 11pt typography and compact normal line spacing");
assert(viewer.includes('function splitTableOverflow') &&
  viewer.includes('nextBody.insertBefore(row,nextBody.firstChild)'),
  "Tables must continue row-by-row across physical pages instead of being clipped");
assert(viewer.includes('function markOverflow') &&
  viewer.includes('data-overflow-expanded') &&
  viewer.includes('page.style.overflow="visible"'),
  "An indivisible oversized block must remain visible instead of losing its trailing text");
assert(viewer.includes('data-fz-keep-lines') && viewer.includes('data-fz-keep-next'),
  "Pagination must respect paragraph keep rules");
assert(!viewer.includes("overflow-wrap:anywhere"), "avoid breaking every word at arbitrary positions");
console.log("DOCX page-layout and lightweight layout-engine regression checks passed.");
