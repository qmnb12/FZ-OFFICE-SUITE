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


const safeConvert = global.FZLegacyHindi.krutiDevToUnicodePreservingEnglish;
if (typeof safeConvert !== "function") {
  throw new Error("Mixed-language safe converter did not register.");
}

const safeCases = [
  ["Jheku", "श्रीमान"],
  ["Jheku 'kklu lfpo", "श्रीमान शासन सचिव"],
  ["'kklu ACEO egksn;", "शासन ACEO महोदय"],
  ["lfpo", "सचिव"],
  ["foHkkx", "विभाग"],
  ["ACEO", "ACEO"],
  ["ACEO महोदय", "ACEO महोदय"],
  ["2007/12.08.2025", "2007/12.08.2025"],
  ["01.05.25", "01.05.25"],
  ["the office", "the office"]
];

for (const [input, expected] of safeCases) {
  const actual = safeConvert(input);
  if (actual !== expected) {
    throw new Error("Safe conversion failed for " + JSON.stringify(input)
      + ": expected " + JSON.stringify(expected)
      + ", got " + JSON.stringify(actual));
  }
}

console.log("Mixed-language preservation tests passed (" + safeCases.length + " cases).");
