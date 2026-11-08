# FZ Office Suite

**An Android-first, offline-first office suite with a native office engine and Hindi-first typography.**

## Current status

The installed v0.5.x app is a transitional Java/WebView document viewer. It is not the final architecture and is not Word-compatible enough for production documents.

### Engine migration selected

FZ Office is moving to the native Android application and LibreOffice engine from Collabora Office. The upstream source is pinned as a Git submodule under third_party/collabora. This gives the project a mature document engine instead of extending the limited custom DOCX parser.

- Engine decision and build requirements: docs/ENGINE_STRATEGY.md
- Hindi font/custom-font integration: docs/HINDI_FONT_INTEGRATION.md
- Pinned upstream source: third_party/collabora

**Important:** the source is pinned, but the native engine has not yet been built into a FZ Office APK. Do not treat the current v0.5.x APK as the Collabora-backed build.

## Target features

- Writer, Sheets and Slides using the native LibreOffice engine.
- DOCX, DOC, ODT, RTF, XLSX, XLS, ODS, PPTX, PPT, ODP and PDF workflows supported by the engine.
- Word-like layout, images, tables, headers/footers, charts, numbering, styles and embedded objects where supported by the engine.
- Offline local-document workflows; no compulsory document server or user account.
- Reader-first mobile UX, with a deliberate edit action/settings choice rather than a cramped editing toolbar on every open.
- Hindi Font Center with licence-safe bundled Unicode Devanagari fonts and import of personal TTF/OTF fonts.
- Separate, tested legacy encoding profiles for DevLys, Kruti Dev, Chanakya, Shusha, Shree Lipi and other families.

## Build environments

- **Code On The Go:** useful for Java/Gradle app-level changes, but it cannot compile the native LibreOffice C/C++ engine.
- **Linux CI/build host:** required to cross-compile the native engine with Android NDK before packaging the Android app.
- First native validation target: arm64-v8a, NDK 29.0.14206865, Android compile SDK 37, minimum SDK 26.

The existing lightweight Android workflow builds only the transitional app. A separate native-engine pipeline must pass the acceptance gates in docs/ENGINE_STRATEGY.md before a new Collabora-backed APK is released.

## Project principles

1. Offline-first after installation; no compulsory account.
2. No ads or document upload to a hosted server by default.
3. Reuse mature open-source engines; do not maintain a home-grown office file parser.
4. Bundle third-party fonts only when their redistribution licence allows it.
5. Let users import fonts they are entitled to use.
6. Never apply a legacy converter without an exact, tested font/version profile.
7. Preserve upstream notices and comply with all component licences.
