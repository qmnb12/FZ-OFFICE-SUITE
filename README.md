# FZ Office Suite

**An Android-first, offline-first office suite with a native office engine and Hindi-first typography.**

## Current status

The v0.6.6 shell-preview APK remains a transitional Java/WebView build and is not the final Office package. The native Collabora build now merges the FZ Home screen, Recent/Starred library, Hindi Font Center, PDF reader and fallback viewers into the same Android package as LibreOffice. In that integrated package, Word, Excel and PowerPoint documents are routed from FZ Home directly to the native LibreOffice activity in read-only mode by default. DOCX page size, pagination and page spacing therefore come from the real office layout engine instead of the single-page HTML prototype. The native APK still needs a successful CI build and device acceptance tests before it can be called verified.

### Engine migration selected

FZ Office is moving to the native Android application and LibreOffice engine from Collabora Office. The upstream source is pinned as a Git submodule under third_party/collabora. This gives the project a mature document engine instead of extending the limited custom DOCX parser.

- Engine decision and build requirements: docs/ENGINE_STRATEGY.md
- Native build instructions: docs/NATIVE_BUILD.md
- Hindi font/custom-font integration: docs/HINDI_FONT_INTEGRATION.md
- Pinned upstream source: third_party/collabora

**Important:** the native source is pinned. The integration script copies FZ's Java shell and offline assets into the Collabora Android app, makes FZ MainActivity the only launcher, keeps LibreOffice's document activity internal, and routes Office files to it in read-only mode. It also applies the Fontconfig user-font directory, open-license Devanagari bundle and ZIP font importer. The integrated native APK is not verified until the native workflow completes and the resulting APK passes device tests.

## FZ app shell features

- Local recent-document library with private URI history, search, Starred shortcuts, share and remove-from-history actions.
- Built-in offline PDF reader with page navigation, fit-to-width rendering and bounded zoom; PDF text search and markup are not included in this transitional reader.
- History is limited to files explicitly opened by the user; FZ does not scan shared storage or delete the original file when history is cleared.

## Target features

- Writer, Sheets and Slides using the native LibreOffice engine (final target).
- Transitional APK: offline, read-only XLS/XLSX/CSV/ODS and PPTX-family preview screens.
- DOCX, DOC, ODT, RTF, XLSX, XLS, ODS, PPTX, PPT, ODP and PDF workflows supported by the engine.
- Word-like layout, images, tables, headers/footers, charts, numbering, styles and embedded objects where supported by the engine.
- Offline local-document workflows; no compulsory document server or user account.
- Reader-first mobile UX, with a deliberate edit action/settings choice rather than a cramped editing toolbar on every open.
- Hindi Font Center with licence-safe bundled Unicode Devanagari fonts and ZIP import into the same app-private user/fonts folder scanned by the Collabora Fontconfig patch. The supplied DevLys/Kruti Dev/Mangal archives are imported locally, not published as binaries, because their ZIPs contain no redistribution licence; Mangal includes Microsoft EULA metadata. See docs/USER_FONT_ARCHIVE_AUDIT.md.
- Separate, tested legacy encoding profiles for DevLys, Kruti Dev, Chanakya, Shusha, Shree Lipi and other families.

## Build environments

- **Code On The Go:** useful for Java/Gradle app-level changes, but it cannot compile the native LibreOffice C/C++ engine.
- **Linux CI/build host:** required to cross-compile the native engine with Android NDK before packaging the Android app.
- First native validation target: arm64-v8a, NDK 29.0.14206865, Android compile SDK 37, minimum SDK 26.

The **FZ Office shell validation** workflow builds the lightweight preview APK. The **Collabora native Android build** workflow packages the real LibreOffice engine together with the FZ Home/library/font shell; its artifact is **FZ-Office-Collabora-arm64**. Treat that APK as the candidate product only after the native build and device acceptance gates in docs/ENGINE_STRATEGY.md pass.

## Project principles

1. Offline-first after installation; no compulsory account.
2. No ads or document upload to a hosted server by default.
3. Reuse mature open-source engines; do not maintain a home-grown office file parser.
4. Bundle third-party fonts only when their redistribution licence allows it.
5. Let users import fonts they are entitled to use.
6. Never apply a legacy converter without an exact, tested font/version profile.
7. Preserve upstream notices and comply with all component licences.
