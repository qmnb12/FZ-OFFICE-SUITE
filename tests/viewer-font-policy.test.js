const fs = require("fs");
const assert = require("assert");

const html = fs.readFileSync("app/src/main/assets/writer/viewer.html", "utf8");

assert(!html.includes("legacy-converter.js"),
  "The normal document viewer must not load a legacy converter.");
assert(!html.includes("convertLegacyFontRuns"),
  "The normal document viewer must not rewrite legacy text.");
assert(html.includes("Matching font found"),
  "Viewer should report when the exact declared document font is available.");
assert(html.includes("paginateWhenFontsReady") && html.includes("document.fonts.load"),
  "Viewer must finish matching-font loading before measuring page breaks.");
assert(html.includes("Legacy font missing"),
  "Viewer should show an exact missing-font warning.");

console.log("Read-only font-rendering policy tests passed.");
