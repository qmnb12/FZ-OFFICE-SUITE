"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const main = read("app/src/main/java/com/fz/office/MainActivity.java");
const viewer = read("app/src/main/java/com/fz/office/viewer/OfficeViewerActivity.java");
const manifest = read("app/src/main/AndroidManifest.xml");
const gradle = read("app/build.gradle.kts");

for (const ext of [".xlsx", ".xls", ".csv", ".ods", ".pptx", ".ppsx", ".pptm", ".potx"]) {
  assert(main.includes(ext), "Missing file route for " + ext);
}
assert(main.includes("OfficeViewerActivity.class"), "Office viewer activity is not routed");
assert(viewer.includes("pptx.html") && viewer.includes("xlsx.html"), "Missing PPTX/XLSX page route");
assert(viewer.includes("/doc/file.pptx"), "PPTX endpoint is not mapped");
assert(viewer.includes("FontManager.getBundledCss()"), "Hindi fonts are not exposed");
assert(viewer.includes("hindi-fonts.css"), "Hindi CSS endpoint is missing");
assert(viewer.includes("setAllowFileAccess(false)"), "WebView file access must remain disabled");
assert(viewer.includes("setAllowContentAccess(false)"), "WebView content access must remain disabled");
assert(viewer.includes("setBlockNetworkLoads(true)"), "Runtime network loads must remain blocked");
assert(viewer.includes("setUseWideViewPort(true)") && viewer.includes("setLoadWithOverviewMode(true)"), "Office pages need Gander-style fit-to-screen viewport handling");
assert(viewer.includes("setMinimumFontSize(presentation ? 8 : 1)"), "Spreadsheet glyph sizing floor is not configured");
assert(viewer.includes("connect-src 'self'"), "Viewer CSP must remain same-origin");
assert(manifest.includes('android:icon="@mipmap/ic_launcher"'), "Launcher icon is not assigned");
assert(manifest.includes('android:roundIcon="@mipmap/ic_launcher_round"'), "Round icon is not assigned");
assert(manifest.includes(".viewer.OfficeViewerActivity"), "Viewer activity missing from manifest");
assert(!manifest.includes('android.permission.INTERNET'), "Offline app must not request INTERNET");
assert(gradle.includes("downloadGanderViewerAssets"), "Gander assets are not included in build");
assert(gradle.includes("73cf325435868cab1683d8de8cc1b89533e767b3"), "Gander commit is not pinned");
assert(gradle.includes("gitBlobSha"), "Viewer assets must be checksum verified");
assert(gradle.includes('versionName = "0.6.2"'), "Version name was not advanced");
assert(gradle.includes("versionCode = 11"), "Version code was not advanced");

console.log("Office viewer routing, Hindi font bridge, offline policy and pinned assets verified.");
