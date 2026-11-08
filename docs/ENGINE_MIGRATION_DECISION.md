# Office engine decision: Collabora Office / LibreOfficeKit

Decision date: 2026-11-08  
Status: Selected architecture; native engine migration is not yet integrated into the FZ Office APK.

## Decision

Use the native Android application and LibreOfficeKit engine from Collabora Office as the document-rendering foundation. Keep FZ Office's product identity, Hindi Font Center, font-import workflow, language defaults, file library and view-only policy as the FZ layer around that engine.

Do not continue expanding the hand-written DOCX parser as the primary renderer. It cannot realistically reproduce Word page layout, images, floating objects, headers/footers, fields, charts, shapes, numbering, sections, or spreadsheet/presentation rendering to desktop-engine quality.

The chosen upstream is the Collabora Office monorepo:

- Source clone: https://gerrit.collaboraoffice.com/online
- Read-only GitHub mirror for browsing: https://github.com/CollaboraOnline/online.mirror
- Android build guide: https://www.collaboraoffice.org/post/build-code-android/
- The old CollaboraOnline/online GitHub repository is an issue tracker; the source is now in the Gerrit monorepo. Do not treat the issue-tracker repository as the source checkout.

## Why this engine

Collabora Office for Android uses the LibreOffice core engine also used on desktop Linux, macOS and Windows. Its Android app already has a native front end, local file handling and document rendering based on LibreOfficeKit.

The engine supports the major Microsoft Office and OpenDocument families, including DOC/DOCX, XLS/XLSX, PPT/PPTX and ODT/ODS/ODP. It also supports PDF export and other office workflows. This is the strongest match for the requirement that a document should retain its original Word-like page layout and embedded content.

Collabora's Android font stack already supports Devanagari/Hindi using available Android system fonts. This avoids trying to reproduce Indic shaping in a WebView or a custom XML parser.

## Alternatives reviewed

### ONLYOFFICE

ONLYOFFICE Docs has strong OOXML support, but its documented mobile WebView integration is available only in commercial Enterprise and Developer builds and requires a Document Server. That is not the intended free, offline-first Android architecture.

The separate ONLYOFFICE Documents Android application supports offline local files, but its publicly available Android source is not a complete drop-in SDK for embedding its editor into FZ Office. Do not assume the server-side/mobile-web editor can be embedded for free.

### LibreOffice Viewer

LibreOffice Viewer is a valid read-only reference and uses the LibreOffice engine, but its Android editing capability is experimental. Collabora Office is the better maintained native Android product foundation for the long-term FZ mobile app.

### Fossify Documents

Fossify-style file/document utilities can help with file browsing and app shell UX, but they are not a replacement for a complete DOCX/XLSX/PPTX office engine. They are not selected as the rendering core.

## Hindi font architecture

Hindi support will be implemented in the engine's real font lookup path rather than by changing HTML CSS after a document has been parsed.

### Font lookup order

1. Fonts available on Android under /system/fonts.
2. Open-license Devanagari fonts packaged with FZ Office.
3. User-imported TTF/OTF files stored in the FZ Office private font directory.
4. Document-embedded fonts, when the file contains them and the font's embedding rights permit use.

The current Collabora Android fontconfig template scans /system/fonts. The engine build also creates a private user/fonts folder inside its unpacked app data. The integration patch will add that directory to android/lib/fonts.conf and expose it through the FZ Font Center.

Expected private path after the native app has unpacked its runtime assets:

<application dataDir>/user/fonts/

The Android LOActivity unpacks the assets/unpack tree into the application's private data directory on first run/update. The font import feature will copy licensed TTF/OTF files to that same user/fonts folder. Fontconfig will then index those files on startup/reload.

### Bundled fonts

Bundle the curated Unicode Devanagari families that have a redistributable license (for example Noto Sans/Serif Devanagari, Mukta, Hind, Tiro Devanagari Hindi, Kalam, Sahitya, Martel, Baloo 2, Khand, Gotu and Glegoo). Keep the corresponding license texts and upstream revision/SHA records.

This is a curated open-font pack, not a claim that every Hindi font ever made is bundled.

### User-imported Hindi fonts

Support import of .ttf and .otf files. This is the only safe way to support proprietary fonts such as DevLys 010, Kruti Dev variants, Chanakya, Shree Lipi and commercial DTP families without redistributing binaries without permission.

The font manager must:
- validate the font container before copying it;
- use app-private storage;
- retain the actual family metadata where possible (not just the file name);
- ask the user to restart/reload the document engine when fontconfig needs a rescan;
- show font family, style, file name and source;
- never upload a font file;
- never claim that a font is redistributable merely because it can be imported.

## Legacy Hindi documents: important correction

A legacy Word document may store Latin-range character codes and use a font such as DevLys 010 to draw Hindi glyphs. The document is not necessarily Unicode Hindi.

For faithful viewing, the primary path is:

1. identify the exact font family in the document's direct formatting and inherited styles;
2. make that exact font available to LibreOfficeKit through fontconfig;
3. let the office engine render the original text using the original font.

Do not auto-convert a legacy document to Unicode when opening it. This can corrupt English abbreviations, numeric references, punctuation and mixed-script text. The existing experimental WebView converter is not the authoritative rendering path and will be retired from document opening.

Optional Legacy-to-Unicode conversion can be a separate tool later. Each exact font/version needs its own verified mapping and golden test corpus. Conversion must create a new copy and show a review preview.

## Mobile experience

FZ Office's default document action remains View only:

- open documents with a read-only Android content URI / no write grant;
- use the native office engine for layout and image rendering;
- retain text selection, find/search, page navigation, zoom, print and export-to-PDF where supported;
- do not expose the editing keyboard or save controls in the default reading flow;
- preserve the source file byte-for-byte.

If full editing is added later, it must be a distinct, deliberate mode rather than an accidental tap in the viewer.

## Build and COTG constraint

The Collabora Android app is not a normal Gradle-only project. Its native engine must be built on a Linux or macOS host with the Android SDK/NDK, then the Android app module is built with Gradle.

The upstream Android guide currently specifies an NDK 29 toolchain, CPAndroidAarch64 for 64-bit ARM, an engine autogen.sh/make build, then the Collabora app configure/build and Android Gradle build.

Code On The Go can still be used for FZ Java/Android shell changes, source review and pulling the finished APK. It cannot replace the Linux/macOS native LibreOffice engine build. That native build must run in a cloud Linux environment.

The standard GitHub-hosted Ubuntu runner has 16 GB RAM and 14 GB SSD. The public Collabora source mirror reports roughly 4.7 GB repository size before generated build output and Android SDK/NDK. A full native build on the standard runner is therefore not assumed feasible. Before enabling this as the default build, use one of:
- a sufficiently large Linux runner with enough disk/RAM;
- a reproducible prebuilt Collabora Android engine/runtime from an official build, with correct source/notices and a matching ABI;
- a separately hosted build machine.

Do not keep retrying a Java-only assembleDebug workflow and claim the native engine is included when it is not.

## Licensing and branding

Collabora's source is primarily MPL 2.0, with additional component licenses. The integration must preserve all upstream copyright/license notices, publish the modified source required by the applicable licenses, and use FZ branding only through the upstream-supported branding mechanism. Do not remove Collabora/LibreOffice attribution or third-party notices where the licenses require them.

ONLYOFFICE AGPL/commercial constraints are documented separately above. Proprietary Hindi font binaries must not be redistributed without permission.

## Acceptance tests before calling it complete

Use a real corpus of files created on desktop Microsoft Word / LibreOffice:

- DOC/DOCX with DevLys 010, Kruti Dev 010 and Unicode Noto Hindi;
- mixed Hindi/English government letters with abbreviations, dates, references and rupee amounts;
- inline/floating images, signatures, scans, tables, page numbers and headers/footers;
- section breaks, page size/margins, columns, footnotes and numbering;
- XLS/XLSX with formulas, merged cells, charts and Hindi labels;
- PPT/PPTX with Hindi text, images, embedded fonts and slide layouts;
- password-protected and damaged files;
- open → view → copy text → print/PDF export → compare against desktop output.

A font or format is considered supported only after the real files pass these tests on the target Android device.

## Current FZ prototype

The existing v0.5.x HTML/WebView parser and limited DOCX conversion are prototypes only. They are not the chosen long-term engine and must not be presented as Word-equivalent. New format features should be implemented on top of the Collabora/LibreOfficeKit migration, not by growing the custom parser.
