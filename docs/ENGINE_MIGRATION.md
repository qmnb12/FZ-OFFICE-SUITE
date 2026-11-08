# FZ Office engine migration decision

## Decision

**Primary engine: Collabora Office for Android, powered by LibreOffice core / LibreOfficeKit (LOK).**

FZ Office must stop extending the prototype HTML/DOCX parser as its production document engine. The existing Java dashboard, WebView reader, DOCX parser and hand-written converter are prototypes only. They must not be mistaken for Word-compatible document support.

Collabora Office for Android uses the LibreOffice engine and a mobile-adapted application. LibreOfficeKit renders document tiles from the native LibreOffice core; the Android application bundles that core in its liblo-native-code.so shared library. This architecture avoids reimplementing WordprocessingML layout rules for images, tables, sections, headers/footers, page flow and complex objects.

Official source/build references:

- Collabora/LibreOffice Android source monorepo: https://gerrit.collaboraoffice.com/online
- Android build guide: https://collaboraonline.github.io/post/build-code-android/
- LibreOffice Android architecture: https://github.com/LibreOffice/core/blob/master/android/README.md
- LibreOffice license overview: https://www.libreoffice.org/licenses/

## Why not continue the custom renderer?

The current prototype reads selected WordprocessingML paragraphs, simple tables and some raster image relationships. It is not a complete DOCX layout engine. A Word document is a package of document XML, styles, numbering, relationships, media, themes, fonts, sections, headers/footers, fields and compatibility settings. Reimplementing all those rules inside this app would duplicate years of work and continue to produce regressions.

After engine migration:
- Remove the production dependency on the custom DocxCodec HTML renderer.
- Remove automatic content-pattern based legacy conversion from the normal document-open flow.
- Keep legacy converters only as separate, opt-in utilities with version-specific test fixtures.
- Keep FZ Office's app shell, Hindi Font Center, recent files, file access, preferences, privacy controls and branding.

## Alternatives evaluated

### Fossify Documents

Use as a reference for Android file browsing, recent documents, favourites, themes and a lightweight viewer shell. It is a private/offline document organizer and reader, not a replacement for LibreOffice's full WordprocessingML engine. Its repository is GPL-3.0-only; review and comply with that license before copying any code.

### ONLYOFFICE

ONLYOFFICE's native Android app supports local offline files and custom OTF font import. However, the documented mobile WebView integration for a branded application uses ONLYOFFICE Docs Developer/Enterprise server builds. This is not the preferred self-contained offline engine path for this project. Re-evaluate only if its commercial mobile integration model is acceptable.

### AndrOpen Office

Do not use as the primary base. Its own source page says only part of the source code or components/extensions are made available; this is not a straightforward full-app source base.

## Hindi-first integration architecture

Hindi support must happen inside the selected office engine, not as post-processing over arbitrary document text.

### 1. Unicode font library

Ship a curated set of redistribution-permitted Devanagari fonts and their licence files, including broad-coverage families and appropriate serif/sans/reading/display faces. Do not claim that a small bundle contains every Hindi font ever made.

Planned starter families:
- Noto Sans Devanagari and Noto Serif Devanagari
- Mukta and Hind
- Tiro Devanagari Hindi
- Sahitya, Martel, Kalam, Gotu, Khand, Baloo 2 and Glegoo
- Additional open-license fonts only after metadata, coverage and redistribution rights are checked

### 2. User-managed font library

Provide a Hindi Font Center that:
- Imports user-selected TTF/OTF files into app-private storage.
- Validates font files and retains original filename/family metadata.
- Registers imported fonts with the LibreOffice user font directory/profile.
- Refreshes the engine font cache and makes fonts selectable for supported documents.
- Shows source/licence notes and warns users not to redistribute fonts without permission.
- Keeps imported fonts offline and local.

Collabora Android currently has an open request for external font-folder/custom-font workflow. FZ Office must implement and test its own app-private font registration path rather than assume Android system fonts automatically become available to the engine.

### 3. Legacy font handling

Legacy fonts and legacy text encodings are not the same thing.

For documents that store legacy character codes and declare a matching legacy font (for example, DevLys 010):
1. Prefer loading the exact user-provided, legally obtained font into the engine.
2. Preserve the original text and original font declaration.
3. Let the office engine render the font's glyph mapping, rather than rewriting all document runs as Unicode.
4. If the font is missing, show the exact missing family and offer a font-import action.
5. Offer a separate opt-in Legacy-to-Unicode conversion tool only when a tested mapping exists for that exact family/version.
6. Never run a best-guess converter automatically over a complete mixed Hindi/English document.

Planned legacy profiles require separate golden fixtures:
- DevLys 010 and other DevLys versions separately
- Kruti Dev 010 and other Kruti Dev versions separately
- Chanakya
- Shusha
- Shree Lipi
- APS-DV and other verified families

No profile is marked supported until real documents pass text, punctuation, matra, conjunct, mixed-language and round-trip tests.

### 4. Hindi language tools

- Hindi (hi-IN) document language selection and metadata.
- Hindi spell-check dictionary and grammar integration where licence-compatible.
- Hindi hyphenation/line breaking where available.
- Unicode search that handles nukta and canonically equivalent text carefully.
- Correct input-method composition and hardware keyboard support.
- Font fallback/substitution warnings without changing stored font metadata.
- PDF export and print testing for Devanagari shaping.

## User experience direction

The default document opening mode remains **View / Read Only**, as requested earlier. The engine should preserve desktop-like layout while viewing. Editing is not exposed by default in the first engine-backed milestone. An explicit editing mode may be enabled later only after the engine is stable and the user can choose it.

The app shell will eventually expose:
- Documents / recent / folders / search
- Writer, Sheets and Slides open/view support
- PDF viewing, print and export
- Hindi Font Center and legacy conversion utilities
- File info, share/open-with and safe read-only permissions

## Build strategy for the current development setup

The Collabora Android native engine cannot be built as a normal Java-only COTG Gradle module. Its build guide requires a Linux/macOS native toolchain, Android SDK and NDK, configures an Android ABI, builds the engine with make, then configures and builds the Android app.

Therefore:
- Use a cloud Linux GitHub Actions build for the native engine and app.
- Start with arm64-v8a only to keep the first APK smaller and the first CI path simpler.
- Cache native downloads/build outputs only after verifying cache keys and upstream revision pinning.
- Keep a pinned upstream commit/revision and record all third-party notices.
- COTG remains useful for editing the Android app shell and installing/testing downloaded APK artifacts.
- Do not tell the user that the old Java-only assembleDebug task builds the new native engine.

The upstream guide documents NDK-based engine configuration, make, and a separate Android app build. Build duration and runner disk/RAM must be validated in CI before promising repeatable APK builds.

## Licensing and redistribution

This project must not blindly copy binaries or fonts from proprietary office suites.

- Collabora/LibreOffice contains MPL-2.0 and additional third-party components with their own licences. Preserve the full upstream LICENSE, THIRDPARTYLICENSES, copyright and notice files; publish corresponding source and build instructions as required by each licence.
- ONLYOFFICE open-source components use AGPL-3.0; separate Developer/Enterprise products have their own commercial terms.
- Fossify Documents is GPL-3.0-only.
- Every font requires its own redistribution licence review. DevLys, Kruti Dev, Shree Lipi and similar legacy font files must be user-supplied unless redistribution permission is confirmed.
- Do not remove upstream attribution or claim that the LibreOffice/Collabora engine was written by FZ Office.

## Migration acceptance tests

Before replacing the current build as the main user-facing APK:

1. Open Bhagwan Singh Shikayat.docx in the engine-backed viewer and compare it with Word/Collabora desktop screenshots.
2. Confirm DevLys 010 is identified from the actual document metadata and exact font is loaded.
3. Confirm English abbreviations, dates, legal references, punctuation and every Hindi matra remain unchanged.
4. Confirm embedded images, tables, headers, footers, page breaks and section layouts render.
5. Test password-protected DOCX, damaged documents, large DOCX, fonts absent/present and embedded fonts.
6. Test DOCX, DOC, DOCX Strict, ODT; XLSX/XLS/ODS/CSV; PPTX/PPT/ODP; PDF viewing/printing.
7. Run offline after installation with no compulsory account and no document uploads.
8. Confirm the source remains read-only unless the user explicitly chooses editing mode.

## Current status

This document records the architecture decision. **The engine has not yet been integrated into FZ Office.** The current v0.5.x custom viewer remains a prototype and must not be described as full Word compatibility.
