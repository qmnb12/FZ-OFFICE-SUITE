const fs = require("fs");
const assert = require("assert");

const codec = fs.readFileSync("app/src/main/java/com/fz/office/writer/DocxCodec.kt", "utf8");
const gradle = fs.readFileSync("app/build.gradle.kts", "utf8");
const activity = fs.readFileSync("app/src/main/java/com/fz/office/writer/WriterActivity.kt", "utf8");

assert(!codec.includes("org.jsoup"), "DOCX parsing must not depend on Jsoup");
assert(!gradle.includes("org.jsoup:jsoup"), "Jsoup must not be an app dependency");
assert(!codec.includes("fun writeDocx("), "Unused DOCX export code must not remain in the read-only writer");
assert(!codec.includes("ZipOutputStream"), "DOCX export-only ZIP writer must be removed");
assert(!codec.includes("packageEntries:"), "The parsed ZIP package must not be retained after the read-only parse");
assert(codec.includes("fun readDocx(input: InputStream): LoadedDocument"), "Kotlin OOXML reader must remain available");
assert(codec.includes("Xml.newPullParser()"), "DOCX structure must continue to be read with the Android XML pull parser");
assert(codec.includes("fun htmlToPlainText(html: String?): String"), "Legacy Hindi detection must retain text extraction");
assert(codec.includes("decodeHtmlEntity(entity: String)"), "Text extraction must decode HTML entities without an external parser");
assert(codec.includes("findHtmlTagEnd(source: String, start: Int)"), "Text extraction must scan quoted HTML attributes safely");
assert(codec.includes('tagName == "script" || tagName == "style"'), "Text extraction must ignore non-document script and style content");
assert(activity.includes("DocxCodec.readDocx(ByteArrayInputStream(data))"), "WriterActivity must continue using the FZ Kotlin DOCX parser");

console.log("FZ Writer Kotlin-core dependency checks passed.");
