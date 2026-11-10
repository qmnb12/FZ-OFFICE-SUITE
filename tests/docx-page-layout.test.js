const fs = require("fs");
const assert = require("assert");
const codec = fs.readFileSync("app/src/main/java/com/fz/office/writer/DocxCodec.kt", "utf8");
const activity = fs.readFileSync("app/src/main/java/com/fz/office/writer/WriterActivity.kt", "utf8");
const viewer = fs.readFileSync("app/src/main/assets/writer/viewer.html", "utf8");
const vm = require("vm");
const cIncludes = (source, snippet) => source.includes(snippet);
const scriptStart = viewer.indexOf("<script>");
const scriptEnd = viewer.indexOf("</script>", scriptStart);
assert(scriptStart >= 0 && scriptEnd > scriptStart, "Word viewer must contain its layout engine script");
new vm.Script(viewer.slice(scriptStart + 8, scriptEnd), { filename: "FZ Writer layout engine" });
assert(codec.includes("readPageLayout(documentXml)"), "DOCX load must read section page geometry");
assert(codec.includes("readPageLayout(documentXml)") && codec.includes("return LoadedDocument(html, fonts, legacyProfile, legacyFont, pageLayout)"), "Read-only DOCX loading must retain original page geometry and margins");
assert(!codec.includes("fun writeDocx("), "The read-only DOCX viewer must not keep an unused export implementation");
assert(codec.includes('"pgSz" -> {'), "DOCX parser must read page dimensions");
assert(codec.includes('"pgMar" -> {'), "DOCX parser must read page margins");
assert(codec.includes('orientation.equals("landscape", true)'), "landscape orientation must be honored");
assert(codec.includes("boundedTwips"), "untrusted dimensions and margins must be bounded");
assert(codec.includes("var inFirstSection = false") &&
  cIncludes(codec, "parser.name == \"sectPr\" && !inFirstSection"),
  "Original page geometry must come from the first DOCX section, not the last section's settings");
assert(activity.includes("pendingPageLayout = loadedPageLayout"), "page layout must survive async DOCX loading");
assert(activity.includes('pageLayout.put("widthTwips"'), "page geometry must be sent to the viewer");
assert(viewer.includes("function applyPageLayout(renumber)"), "viewer must apply page geometry through shared layout styles");
assert(viewer.includes("function makePage(after)") && viewer.includes("function paginate()"),
  "DOCX viewer must create and paginate separate physical pages");
const manualBreakStart = viewer.indexOf("var marker=children[breakIndex],following=children.slice(breakIndex+1);");
const manualBreakEnd = viewer.indexOf("if(!overflows(page))break;", manualBreakStart);
const manualBreakLogic = viewer.slice(manualBreakStart, manualBreakEnd);
assert(manualBreakStart >= 0 && manualBreakEnd > manualBreakStart &&
  manualBreakLogic.includes("if(overflows(page))") &&
  manualBreakLogic.includes("nextPage.appendChild(marker)") &&
  manualBreakLogic.includes("children=Array.prototype.slice.call(page.children)"),
  "Explicit Word page breaks must not bypass pagination of overflowing content before the break");
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
  viewer.includes("renderDocument(lastDocument)") &&
  viewer.includes("paginateWhenFontsReady(state)"),
  "Changing paper size must repaginate from the untouched source document and wait for its actual font");
assert(viewer.includes("window.addEventListener(\"resize\""), "page layout must adapt to viewport changes");
assert(viewer.includes("overflow-wrap:break-word;word-break:normal"), "normal words should not be split unnecessarily");
assert(codec.includes('endTag: String = "body"') &&
  codec.includes('"sdt", "sdtContent", "customXml", "ins", "moveTo"') &&
  codec.includes('"del", "moveFrom"'),
  "DOCX block content nested in controls and accepted tracked changes must not be silently skipped");
assert(codec.includes('"fldSimple"') && codec.includes('"ins", "moveTo", "fldSimple"'),
  "Simple fields and tracked insertion run wrappers must retain their visible content");
assert(codec.includes('"sectPr" ->') &&
  codec.includes('sectionBreakAfter') &&
  codec.includes("data-fz-section-break"),
  "Next-page/odd/even Word section breaks must create physical page boundaries");
assert(codec.includes('"spacing" ->') && codec.includes('"ind" ->') &&
  codec.includes('lineRule') && codec.includes('margin-top:') &&
  codec.includes('text-indent:') && codec.includes('resolveParagraphCss'),
  "DOCX parser must preserve Word paragraph spacing, line height, indentation and inherited styles");
assert(codec.includes('"keepNext" ->') && codec.includes('"keepLines" ->') &&
  codec.includes('data-fz-keep-next') && codec.includes('data-fz-keep-lines'),
  "Word keep-with-next and keep-lines rules must reach the page layout engine");
assert(viewer.includes('font-size:11pt;line-height:1.2') &&
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
const splitStart = viewer.indexOf("function splitLongBlock");
const splitEnd = viewer.indexOf("function splitTableOverflow", splitStart);
const splitLogic = viewer.slice(splitStart, splitEnd);
assert(splitLogic.includes("keepTogether") &&
  splitLogic.includes("prefixFits(page,block,map,map.total)"),
  "keepLines/keepNext must be respected when the paragraph fits, but oversized paragraphs must still split");
assert(viewer.includes("window.getComputedStyle(page)") &&
  viewer.includes('"fontFamily","fontSize","fontWeight","fontStyle","lineHeight"'),
  "split measurements must use the rendered page typography, including the actual DOCX font");
assert(viewer.includes("function paginateWhenFontsReady(state)") &&
  viewer.includes("state.fontAvailable&&state.fontName") &&
  viewer.includes("document.fonts.load(fontSpec)") &&
  viewer.includes("if(lastDocument===state)paginate()"),
  "DOCX pagination must wait for the exact available document font before measuring page breaks");
const renderStart = viewer.indexOf("function renderDocument(state)");
const renderEnd = viewer.indexOf("function copyLayout", renderStart);
const renderLogic = viewer.slice(renderStart, renderEnd);
assert(renderLogic.includes("paginateWhenFontsReady(state)") && !renderLogic.includes("paginate();"),
  "A DOCX must not paginate immediately with fallback font metrics");

console.log("DOCX page-layout and lightweight layout-engine regression checks passed.");
