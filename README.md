# FZ Office Suite

**An Android-first, offline-first office suite with Hindi-first document and font support.**

## Current status — v0.5.2

### Document Viewer (read-only)

- Open DOCX and UTF-8 TXT documents from Android's document picker.
- Display supported paragraphs, headings, common text styles, simple tables and embedded raster images (PNG, JPEG, GIF, WebP, BMP).
- Select and copy text; adjust reading size with A− / A+ controls.
- There are no editing controls, keyboard input, Save or Save As actions in the document screen.
- Original documents are opened read-only and are never rewritten by the app.

### Automatic legacy Hindi display

- DOCX font declarations are inspected in document, styles, header/footer and font-table parts.
- Automatic conversion is restricted to DOCX text runs explicitly tagged with the exact DevLys 010 or Kruti Dev 010 font name.
- Unmarked text, existing Unicode Hindi, English/date/reference runs and content with unknown fonts are not changed.
- If a file declares the legacy font only through an unresolved paragraph style, the viewer warns instead of guessing.
- The conversion runs only in memory. The original DOCX package and the file on storage remain unchanged.
- A conservative text-pattern fallback can identify some files whose font metadata is missing.
- Unverified legacy families and variants are not guessed. The viewer shows a warning instead.

### Hindi Font Center

- 12 bundled Unicode Devanagari font families with their OFL license texts.
- Import personal TTF/OTF fonts into private app storage and preview/select a default display font.
- Bundled and imported fonts work offline after installation.

**Coverage note:** font coverage differs by family. Noto Sans Devanagari and Noto Serif Devanagari remain the general-purpose choices.

### Legacy conversion limits

The automatic mapping in this release is for Kruti Dev 010 only. It is not a universal converter for all Kruti Dev versions, DevLys, Chanakya, Shusha, Shree Lipi or other DTP encodings. Unknown mappings are shown unchanged with a warning. A font name alone does not prove the character encoding.

### Document compatibility boundary

This is an early read-only DOCX renderer, not full Microsoft Word or LibreOffice fidelity. It reads main-body paragraphs, simple tables and common embedded raster images. Image placement is approximated as inline content. Complex elements such as charts, SmartArt, text boxes, equations, comments, tracked changes, advanced numbering, headers, footers and section layouts are not fully rendered. Vector WMF/EMF and some SVG fallback images are not supported yet. For complete layout fidelity, a full document engine integration remains a future milestone.

Sheets and Slides are not connected. PDF viewing is planned separately.

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
