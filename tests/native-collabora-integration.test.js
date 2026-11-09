const fs = require("fs");
const assert = require("assert");

const main = fs.readFileSync("app/src/main/java/com/fz/office/MainActivity.java", "utf8");
const fontManager = fs.readFileSync("app/src/main/java/com/fz/office/fonts/FontManager.java", "utf8");
const editorLauncher = fs.readFileSync("app/src/main/java/com/fz/office/OfficeEditorLauncher.java", "utf8");
const fontCenter = fs.readFileSync("app/src/main/java/com/fz/office/fonts/FontCenterActivity.java", "utf8");
const patch = fs.readFileSync("scripts/patch-collabora-hindi.py", "utf8");
const manifest = fs.readFileSync("third_party/collabora/android/app/src/main/AndroidManifest.xml", "utf8");
const collaboraGradle = fs.readFileSync("third_party/collabora/android/app/build.gradle", "utf8");
const fontconfigPatch = fs.readFileSync("patches/collabora/0001-android-fontconfig-hindi-font-directory.patch", "utf8");
const buildScript = fs.readFileSync("scripts/build-collabora-android.sh", "utf8");
const workflow = fs.readFileSync(".github/workflows/collabora-native-build.yml", "utf8");
const loActivity = fs.readFileSync("third_party/collabora/android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java", "utf8");

assert(main.includes('libraryTabButton("Recent files"'), "Recent tab must show its full label");
assert(main.includes('libraryTabButton("Starred files"'), "Starred tab must show its full label");
assert(main.includes("button.setIncludeFontPadding(false)"), "library labels must not be vertically clipped");
assert(main.includes("button.setPadding(dp(8), 0, dp(8), 0)"), "library tabs must use the available height");
assert(main.includes("org.libreoffice.androidlib.LOActivity"), "Office documents must route to native Collabora");
assert(main.includes('nativeOpen.putExtra("com.fz.office.READ_ONLY", true)'), "native Office documents must open read-only");
assert(main.includes("isFzWriterViewerDocument"), "DOCX/TXT should remain on the FZ reader until Edit");
assert(editorLauncher.includes('editor.putExtra(READ_ONLY_EXTRA, false)'), "explicit Edit action must open Collabora writable mode");
assert(editorLauncher.includes("FLAG_GRANT_WRITE_URI_PERMISSION"), "native editor must forward an actual write grant when available");
assert(fontManager.includes("ensureBundledEngineFonts") && fontManager.includes("BUNDLED_ENGINE_FONT_FILES"), "Bundled Hindi font installer/catalogue is missing");
assert(fontManager.includes("getEngineFontFileCount"), "Installed native font file count is missing");
assert(fontCenter.includes("Search all fonts"), "Font Center must provide all-font search");
assert(main.includes("isWordOfficeDocument"), "Word formats beyond DOCX must be recognized");
assert(main.includes('Native Word layout engine is not available in this build.'),
  "do not silently fall back to the broken single-page DOCX renderer when Collabora is missing");
assert(main.includes('lower.endsWith(".ppt")'), "legacy PowerPoint files must use native Office");
assert(main.includes('lower.endsWith(".xlt")'), "legacy Excel templates must use native Office");
assert(main.includes('lower.endsWith(".odp")'), "ODP presentations must use native Office");

assert(patch.includes("def patch_fz_shell(root: Path)"), "native APK must receive the FZ app shell");
assert(patch.includes("generated_fonts"), "FZ Hindi fonts must be bundled in the native APK");
assert(patch.includes("generated_viewer"), "FZ fallback viewer assets must be bundled");
assert(patch.includes('make_internal(manifest_text, "org.libreoffice.androidlib.LOActivity")'),
  "native editor activity must be kept internal");
assert(patch.includes("FZ MainActivity to be the only launcher activity."),
  "manifest patch must assert a single FZ launcher");
assert(manifest.includes('android:name="com.fz.office.MainActivity"'), "FZ Home must be registered");
assert(manifest.includes('android:label="FZ Work Suite"'), "native package must use FZ app label");
assert(manifest.includes('android:allowBackup="false"'), "FZ private library and imported fonts must not be auto-backed up");
assert(!manifest.includes("android.permission.MANAGE_EXTERNAL_STORAGE")
  && !manifest.includes("android.permission.WRITE_EXTERNAL_STORAGE"),
  "reader-only FZ Office must not request broad shared-storage permissions");
assert(loActivity.includes('isDocEditable = !getIntent().getBooleanExtra("com.fz.office.READ_ONLY", false);'),
  "native Collabora activity must honour read-only mode");
assert(loActivity.includes("isDocEditable && (getIntent().getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0"),
  "read-only documents must not be copied into a writable temporary file");
assert(/private boolean canAllFilesAccessHelp\(\) \{\s*\/\/ FZ opens native documents in reader-only mode: never ask users for broad storage access\.\s*if \(getIntent\(\)\.getBooleanExtra\("com\.fz\.office\.READ_ONLY", false\)\)\s*return false;/.test(loActivity),
  "read-only documents must never trigger the All files access permission prompt");
assert(manifest.includes('android:icon="@mipmap/fz_launcher"'), "native package must use the FZ launcher icon");
assert(manifest.includes('android:roundIcon="@mipmap/fz_launcher_round"'), "native package must use the FZ round icon");
assert(manifest.includes('android:mimeType="text/comma-separated-values"'),
  "native launcher must accept the comma-separated-values MIME alias");
for (const mime of [
  "application/vnd.ms-word.document.macroenabled.12",
  "application/vnd.ms-word.template.macroenabled.12",
  "application/vnd.oasis.opendocument.text-template",
  "application/vnd.oasis.opendocument.text-flat-xml",
  "application/vnd.oasis.opendocument.spreadsheet-template",
  "application/vnd.oasis.opendocument.spreadsheet-flat-xml",
  "application/vnd.oasis.opendocument.presentation-template",
  "application/vnd.oasis.opendocument.presentation-flat-xml"
]) {
  assert(manifest.includes('android:mimeType="' + mime + '"'),
    "native launcher is missing MIME filter " + mime);
}
assert(manifest.includes('android:name="android.intent.category.LAUNCHER"'), "FZ Home must be launcher");
const browserActivity = manifest.match(/<activity(?=[^>]*android:name="\.ui\.LibreOfficeUIActivity")[\s\S]*?<\/activity>/);
assert(browserActivity, "Collabora browser activity must remain registered internally");
assert(!/<intent-filter\b/.test(browserActivity[0]), "Collabora browser must not be externally reachable");
const nativeActivity = manifest.match(/<activity(?=[^>]*android:name="org\.libreoffice\.androidlib\.LOActivity")[\s\S]*?<\/activity>/);
assert(nativeActivity, "native LOActivity must remain registered");
assert(/android:exported="false"/.test(nativeActivity[0]) && !/<intent-filter\b/.test(nativeActivity[0]),
  "LOActivity must be internal to FZ MainActivity");
const launcherCount = [...manifest.matchAll(/<intent-filter>[\s\S]*?<category android:name="android.intent.category.LAUNCHER"/g)].length;
assert.strictEqual(launcherCount, 1, "FZ MainActivity must be the only launcher");
assert(!manifest.split(/\r?\n/).some(line => /^[ \t]+$/.test(line)),
  "Generated Collabora manifest must not contain whitespace-only lines");
assert(patch.includes("manifest_text, inserted = re.subn("),
  "FZ activity insertion must replace the complete application-tag line");
assert(patch.includes("if permission in manifest_text:"),
  "storage permission removal must be safe to apply more than once");
assert(collaboraGradle.includes("org.jsoup:jsoup:1.24.1"), "native module must include Jsoup for DOCX fallback");
assert(!collaboraGradle.includes("applicationIdSuffix '.debug'"),
  "debug APK package ID must match the Fontconfig app-private font directory");
assert(fontconfigPatch.includes("@@APPLICATION_ID@@/user/fonts"),
  "Fontconfig must use the configured application ID for imported fonts");
assert(fontManager.includes('return new File(new File(context.getApplicationInfo().dataDir, "user"), "fonts");'),
  "FZ imported fonts must be stored in the same app-private directory as Fontconfig");
assert(fontManager.includes("String fontPath = xmlEscape(customDirectory(context).getAbsolutePath());"),
  "generated fontconfig must point at the actual FZ imported-font directory");
assert(buildScript.includes('--with-app-package-name="com.fz.office"'),
  "native build must configure the FZ application ID");
assert(buildScript.includes("-path '*/outputs/apk/*' -name '*debug*.apk' -print -quit"),
  "native APK discovery must support Collabora's relocated build directory and ABI-split output folders");
assert(buildScript.includes("FZ-Office-Collabora-${ANDROID_ABI}-debug.apk"),
  "native APK export name must match the selected ABI");
assert(patch.includes("debug_suffix = ") && patch.includes("applicationIdSuffix '.debug'")
  && patch.includes("splitlines(keepends=True)"),
  "native integration must remove the upstream debug application ID suffix");
assert(workflow.includes(":app:downloadGanderViewerAssets"), "CI must generate Gander assets");
assert(workflow.includes("node tests/native-collabora-integration.test.js"), "CI must run integration checks");
assert(workflow.includes("VERSION_NAME: 0.6.7.0") && workflow.includes("VERSION_CODE: 16"),
  "native APK version must be aligned with the FZ shell");

const java = "third_party/collabora/android/app/src/main/java/com/fz/office/";
assert(fs.existsSync(java + "MainActivity.java"), "FZ MainActivity must be copied into Collabora");
assert(fs.existsSync("third_party/collabora/android/app/src/main/res/mipmap-anydpi-v26/fz_launcher.xml"),
  "FZ adaptive launcher icon must be copied");
assert(fs.existsSync("third_party/collabora/android/app/src/main/res/drawable/fz_launcher_foreground.xml"),
  "FZ launcher foreground must be copied");
assert(fs.existsSync("third_party/collabora/android/app/src/main/res/values/fz_brand_colors.xml"),
  "FZ brand colors must be copied");
assert(fs.existsSync(java + "fonts/FontCenterActivity.java"), "FZ Font Center must be copied");
assert(fs.existsSync(java + "OfficeEditorLauncher.java"), "FZ explicit Edit launcher must be copied into native app");
const copiedOfficeViewer = fs.readFileSync(java + "viewer/OfficeViewerActivity.java", "utf8");
const copiedPdfViewer = fs.readFileSync(java + "pdf/PdfViewerActivity.java", "utf8");
assert(copiedOfficeViewer.includes("android.R.attr.progressBarStyleHorizontal")
  && !copiedOfficeViewer.includes("android.android.R.attr.progressBarStyleHorizontal"),
  "Office fallback must use exactly one framework resource prefix");
assert(copiedPdfViewer.includes("android.R.attr.progressBarStyleHorizontal")
  && !copiedPdfViewer.includes("android.android.R.attr.progressBarStyleHorizontal"),
  "PDF reader must use exactly one framework resource prefix");
assert(fs.existsSync("third_party/collabora/android/app/src/main/assets/writer/viewer.html"),
  "FZ Writer assets must be copied");
assert(fs.existsSync("third_party/collabora/android/app/src/main/assets/viewer/xlsx.html"),
  "Gander fallback reader must be copied");

console.log("FZ native Collabora integration checks passed (64 assertions).");
