# FZ Office Suite

**An Android-first, offline-first office suite with Hindi-first document and font support.**

## Current status — v0.4.0

### Writer MVP

- Create a blank Writer document.
- Open and edit DOCX and TXT files.
- Save changes or use Save As.
- Basic formatting, lists, alignment and simple tables.
- Hindi character insertion for nukta, halant, danda, rupee sign, conjunct samples and ZWJ/ZWNJ.

### Hindi Font Center

- 12 bundled Unicode Devanagari font families: Noto Sans Devanagari, Noto Serif Devanagari, Mukta, Hind, Tiro Devanagari Hindi, Kalam, Sahitya, Martel, Baloo 2, Khand, Gotu and Glegoo.
- Regular/bold or italic companion files are included for selected families; variable font files are used where available.
- Import personal TTF and OTF fonts into private app storage, preview them, set a default Writer font or remove them.
- Bundled font files are pinned to a Google Fonts repository revision and verified against their Git object SHA-1 before packaging. The respective OFL license texts are bundled with the assets.
- After installation, bundled and imported fonts are available offline.

**Font coverage is not identical across families.** Noto Sans Devanagari and Noto Serif Devanagari are the recommended general-purpose choices. Decorative/display families may not contain every Devanagari extension character. Font Center includes shaping samples for comparison.

### Legacy Hindi fonts and encoding

The app does not bundle proprietary legacy font binaries such as Kruti Dev, DevLys, Chanakya, Shusha or Shree Lipi. Users may import TTF/OTF files they are legally entitled to use. Importing a legacy font changes glyph rendering only when the stored document text uses the matching legacy character mapping; it does not convert that text to Unicode.

Legacy-to-Unicode converters are a separate planned feature. Each version/profile requires verified mappings and sample documents. No automatic guessing or silent conversion is performed.

### Compatibility boundary

This remains an early Writer MVP, not full Microsoft Word or LibreOffice compatibility. The current DOCX bridge handles normal paragraphs, common direct run formatting and simple tables. Complex objects such as images, charts, SmartArt, text boxes, equations, macros, tracked changes, comments, advanced numbering, headers/footers and complex section layouts are not fully implemented and may be lost or changed when the document is saved. Keep a copy of important originals.

Sheets, Slides and PDF editing engines are still under development.

## Development environment

| Component | Version |
|---|---:|
| Gradle | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| JDK | 21 |
| Compile SDK | 36 |
| Target SDK | 35 |
| Minimum SDK | 24 |

The Android app remains Java-first for Code On The Go. The embedded Writer editor is a local HTML asset. Document and font processing is on-device.

## Build in Code On The Go

1. Pull the latest main branch from https://github.com/qmnb12/FZ-OFFICE-SUITE.git.
2. Open the folder containing settings.gradle.kts as the project root.
3. Sync Gradle dependencies.
4. The first build downloads pinned Google Fonts files and license texts. Internet is required for this one-time build step; every file is size- and hash-verified.
5. Run :app:assembleDebug and install the new APK.

Once installed, the app's bundled font library works offline. The GitHub Actions workflow also builds and uploads a debug APK.

## Project principles

1. Offline-first after installation; no compulsory account.
2. No ads or tracking.
3. Review licenses before bundling third-party engines or fonts.
4. Never claim a format or Hindi encoding is supported until it passes real test documents.
5. Preserve original files where possible; important documents should be backed up before using the early DOCX editor.
6. Keep the Android build manageable from Code On The Go wherever technically possible.

## Third-party notices

- Google Fonts Devanagari font files are distributed under their respective SIL Open Font License 1.1 texts, bundled in writer/fonts/licenses/ in the APK.
- jsoup is used for safe HTML fragment parsing under the MIT License.
- Core library desugaring is provided by Android's desugaring library.

See docs/HINDI_FONTS.md, docs/HINDI_FONT_STRATEGY.md and docs/DEVANAGARI_TEST_CORPUS.md.
