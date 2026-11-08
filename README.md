# FZ Office Suite

**An Android-first, offline-first office suite with a native office engine and Hindi-first typography.**

## Current status

The v0.6.2 lightweight APK remains a transitional, read-only Java/WebView app. DOCX/TXT continue through the Hindi-aware in-app reader; XLS/XLSX, CSV, ODS and PPTX-family files now use pinned, bundled Gander-derived offline preview renderers. This adds spreadsheet and slide viewing, not editing or full Word compatibility. The native Collabora/LibreOffice engine remains the selected final architecture and is not yet a verified FZ Office APK.

### Engine migration selected

FZ Office is moving to the native Android application and LibreOffice engine from Collabora Office. The upstream source is pinned as a Git submodule under third_party/collabora. This gives the project a mature document engine instead of extending the limited custom DOCX parser.

- Engine decision and build requirements: docs/ENGINE_STRATEGY.md
- Native build instructions: docs/NATIVE_BUILD.md
- Hindi font/custom-font integration: docs/HINDI_FONT_INTEGRATION.md
- Pinned upstream source: third_party/collabora

**Important:** the native source is pinned. Two small upstream-compatible patches plus an idempotent Python integration script add the Fontconfig user-font directory, the open-license Devanagari bundle, the ZIP font importer and read-only document opening. The native engine has not yet produced a verified FZ Office APK; the current lightweight APK remains transitional.

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

The existing lightweight Android workflow builds only the transitional app. Use the manual Collabora native build workflow for the real LibreOffice engine; it must pass the acceptance gates in docs/ENGINE_STRATEGY.md before the Collabora-backed APK replaces the prototype.

## Project principles

1. Offline-first after installation; no compulsory account.
2. No ads or document upload to a hosted server by default.
3. Reuse mature open-source engines; do not maintain a home-grown office file parser.
4. Bundle third-party fonts only when their redistribution licence allows it.
5. Let users import fonts they are entitled to use.
6. Never apply a legacy converter without an exact, tested font/version profile.
7. Preserve upstream notices and comply with all component licences.
