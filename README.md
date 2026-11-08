# FZ Office Suite

**An Android-first, offline-first office suite with Hindi-first document and font support.**

FZ Office aims to make office documents work reliably for Hindi users — including modern Unicode Devanagari and carefully tested legacy Hindi encodings.

## Current status — v0.2.0

The first Writer MVP is now connected:

- Create a blank Writer document.
- Open and edit .docx and .txt files from Android's document picker.
- Save changes back to the selected document when the storage provider permits it, or use Save As.
- Basic formatting: bold, italic, underline, headings, alignment, lists, undo/redo and font sizing.
- Basic Word tables.
- Offline editing; no account or document server is required.
- Existing DOCX package parts are retained when saving, but the edited main body is rebuilt from the supported subset.

**Compatibility boundary:** this is an early Writer MVP, not full Microsoft Word or LibreOffice compatibility. The current DOCX bridge handles normal paragraphs, common direct run formatting and simple tables. Complex objects such as images, charts, SmartArt, text boxes, equations, macros, tracked changes, comments, advanced numbering, headers/footers and complex section layouts are not fully implemented and may be lost or changed when the document is saved. Always keep a copy of important originals.

TXT is treated as UTF-8. Legacy Hindi font encodings such as Kruti Dev and DevLys are not converted automatically.

Sheets, Slides, PDF tools and the full Hindi Font Center are still under development.

## Development environment

| Component | Version |
|---|---:|
| Gradle | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| JDK | 21 |
| Compile SDK | 36 |
| Target SDK | 35 |
| Minimum SDK | 24 |

The Android app remains Java-first for Code On The Go. The embedded editor page is a local HTML asset; documents are processed on-device.

## Open in Code On The Go

1. Pull the latest main branch from https://github.com/qmnb12/FZ-OFFICE-SUITE.git.
2. Open the folder containing settings.gradle.kts as the project root.
3. Sync Gradle so the Jsoup and core-library desugaring dependencies can download.
4. Run :app:assembleDebug.
5. Install the new debug APK.

The GitHub Actions workflow also builds a debug APK and uploads it as an artifact.

## Product direction

- **Android application:** our UI, file handling, settings and Hindi Font Center.
- **Document engine:** use a reusable open-source engine where it is technically and legally suitable; do not hand-wave format compatibility.
- **Hindi typography:** reliable Devanagari shaping, font import and missing-font handling.
- **Legacy conversion:** separate verified encoding profiles for Kruti Dev, DevLys, Chanakya and other families.

See:
- [Project roadmap](docs/ROADMAP.md)
- [Hindi font compatibility strategy](docs/HINDI_FONT_STRATEGY.md)

## Project principles

1. Offline-first; no compulsory account.
2. No ads or tracking.
3. Review licenses before bundling third-party engines or fonts.
4. Never claim a format or Hindi encoding is supported until it passes real test documents.
5. Preserve original files where possible; create a separate copy when overwrite is not permitted.
6. Keep the Android build manageable from Code On The Go wherever technically possible.

## Third-party notices

- [jsoup](https://jsoup.org/) is used for safe HTML fragment parsing and is distributed under the MIT License.
- Core library desugaring is provided by Android's desugaring library.
