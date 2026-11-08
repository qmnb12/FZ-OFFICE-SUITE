"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.java");
const store = read("app/src/main/java/com/fz/office/library/RecentDocumentsStore.java");
assert(main.includes("addLibrarySection(page)"), "Home screen must include the local library");
assert(main.includes("Search recent documents"), "Recent documents must be searchable");
assert(main.includes('actionButton("★  Starred"'), "Starred filter is missing");
assert(main.includes("shareDocument(entry)"), "Share action is missing from library rows");
assert(main.includes("RecentDocumentsStore.record(this, uri, name, mime)"), "Opening a file must record it");
assert(main.includes("RecentDocumentsStore.setStarred"), "Star action must persist");
assert(main.includes("RecentDocumentsStore.remove"), "Remove-from-history action is missing");
assert(main.includes("RecentDocumentsStore.clearUnstarred"), "History cleanup is missing");
assert(store.includes("MAX_ENTRIES = 60"), "History must be bounded");
assert(store.includes("getSharedPreferences(PREFS, Context.MODE_PRIVATE)"), "History must be stored privately");
assert(store.includes("wasStarred"), "Reopening a document must preserve its starred state");
assert(store.includes("Does not delete original documents"), "Library must not delete source files");
assert(!store.includes("Environment.getExternalStorageDirectory"), "Library must not scan shared storage");

console.log("FZ Office local library checks passed.");
