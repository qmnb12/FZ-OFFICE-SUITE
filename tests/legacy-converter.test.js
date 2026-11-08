// Small regression tests for the bundled Kruti Dev 010 to Unicode preview.
global.window = global;
require("../app/src/main/assets/writer/legacy-converter.js");

const convert = global.FZLegacyHindi && global.FZLegacyHindi.krutiDevToUnicode;
if (typeof convert !== "function") {
  throw new Error("Kruti Dev converter did not register.");
}

const cases = [
  ["", ""],
  ["d", "क"],
  ["v", "अ"],
  ["vk", "आ"],
  ["vks", "ओ"],
  ["vkS", "औ"],
  ["pkS", "चौ"],
  ["भारत", "भारत"]
];

for (const [input, expected] of cases) {
  const actual = convert(input);
  if (actual !== expected) {
    throw new Error("Conversion failed for " + JSON.stringify(input)
      + ": expected " + JSON.stringify(expected)
      + ", got " + JSON.stringify(actual));
  }
}

console.log("Kruti Dev 010 conversion tests passed (" + cases.length + " cases).");
