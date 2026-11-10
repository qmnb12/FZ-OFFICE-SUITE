"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.kt");
const store = read("app/src/main/java/com/fz/office/library/RecentDocumentsStore.kt");
assert(main.includes("addLibrarySection(page)"), "Home screen must include the local library");
assert(main.includes("openDocumentPicker(type)"), "Each format tile must open its own filtered picker");
assert(main.includes("Intent.ACTION_OPEN_DOCUMENT_TREE") &&
  main.includes("DocumentsContract.buildChildDocumentsUriUsingTree") &&
  main.includes("DocumentsContract.buildDocumentUriUsingTree"),
  "Format tiles must use the in-app SAF folder browser instead of relying on providers to honor MIME filters");
assert(main.includes("matchesPickerFilter(name, mime, filter)"),
  "Only files matching the selected format may be listed in the format folder browser");
assert(main.includes("FORMAT_PICKER_TREE_URI") && main.includes("takePersistableUriPermission(treeUri, readGrant)"),
  "The selected folder and read permission must be remembered for the next visit");
assert(main.includes('setNeutralButton("Change folder")'),
  "Users must be able to change the remembered folder");
assert(main.includes("val resultIntent = data ?: return") &&
  main.includes("resultIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION"),
  "Filtered picker result must retain the returned URI grant flags");
assert(main.includes("Search recent documents"), "Recent documents must be searchable");
assert(main.includes('libraryTabButton("Recent files"'), "Recent files tab is missing");
assert(main.includes('libraryTabButton("Starred files"'), "Starred files tab is missing");
assert(main.includes("includeFontPadding = false"), "Library tab labels must not be clipped");
assert(main.includes("shareDocument(entry)"), "Share action is missing from library rows");
assert(main.includes("RecentDocumentsStore.record(this, uri, name, mime)"), "Opening a file must record it");
assert(main.includes("RecentDocumentsStore.setStarred"), "Star action must persist");
assert(main.includes("RecentDocumentsStore.remove"), "Remove-from-history action is missing");
assert(main.includes("RecentDocumentsStore.clearUnstarred"), "History cleanup is missing");
assert(store.includes("MAX_ENTRIES = 60"), "History must be bounded");
assert(store.includes("getSharedPreferences(PREFS, Context.MODE_PRIVATE)"), "History must be stored privately");
assert(store.includes("wasStarred"), "Reopening a document must preserve its starred state");
assert(store.includes("Source documents are never deleted"), "Library must not delete source files");
assert(!store.includes("Environment.getExternalStorageDirectory"), "Library must not scan shared storage");
assert(store.includes("@JvmStatic"), "Java callers must retain static method compatibility");
assert(store.includes("@JvmField"), "Java callers must retain direct Entry field compatibility");

console.log("FZ Office local library checks passed.");
