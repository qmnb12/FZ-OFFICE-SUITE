# FZ Office Suite

**An Android-first, offline-first office suite with Hindi-first document and font support.**

## Current status — engine migration planned

The v0.5.x Java/WebView reader is a prototype only. It does not provide full Microsoft Word-compatible DOCX rendering. The project has selected **Collabora Office / LibreOfficeKit** as the primary engine to replace the hand-written DOCX renderer.

The default document experience remains read-only. Hindi support will be integrated into the office engine with:
- A curated open-license Devanagari font pack.
- App-private TTF/OTF font import and registration with the engine.
- Exact legacy font support when users provide fonts they are licensed to use.
- Version-specific opt-in Legacy-to-Unicode converters with real-document tests.
- Full-engine layout rendering for images, tables, page breaks, headers/footers, sections and other Word structures.

The native engine has not yet been integrated. See [Engine migration decision](docs/ENGINE_MIGRATION.md) before treating any v0.5.x build as a production document viewer.

## Development environment

| Component | Version |
|---|---:|
| Gradle | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| JDK | 21 |
| Compile SDK | 36 |
| Target SDK | 35 |
| Minimum SDK | 24 |

The Android app shell can remain Java-first for Code On The Go. The selected office engine includes native C++ components and must be built on a Linux cloud runner with the Android NDK; the old Java-only build does not build that engine.

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

See docs/ENGINE_MIGRATION.md, docs/HINDI_FONTS.md, docs/HINDI_FONT_STRATEGY.md and docs/DEVANAGARI_TEST_CORPUS.md.
