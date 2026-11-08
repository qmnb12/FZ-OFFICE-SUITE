# FZ Office Suite

**An Android-first, open-source office suite with Hindi-first document and font support.**

FZ Office aims to make office documents work reliably for Hindi users — including modern Unicode Devanagari and carefully tested legacy Hindi encodings.

## Current status

This repository now contains the **Android foundation build**:

- A COTG-compatible Android Gradle project.
- A small Hindi-first dashboard UI.
- A document picker entry point for future editor integration.
- A clearly marked Hindi Font Center prototype.
- GitHub Actions debug-build workflow.
- Initial architecture and compatibility notes.

**Important:** Writer, spreadsheet and presentation editing engines are not connected yet. Selecting a document does not currently edit or render it. This foundation is not a WPS/LibreOffice replacement yet.

## Development environment

The project is configured for the current Code On The Go toolchain:

| Component | Version |
|---|---:|
| Gradle | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| JDK | 21 |
| Compile SDK | 36 |
| Target SDK | 35 |
| Minimum SDK | 24 |

The app module is Java-only for the first stage. This avoids adding Kotlin or native-toolchain complexity before it is needed.

## Open in Code On The Go

1. Clone `https://github.com/qmnb12/FZ-OFFICE-SUITE.git` using COTG's Git support, or download the repository ZIP and extract it.
2. Open the folder containing `settings.gradle.kts` as the project root.
3. Let COTG sync the Gradle project.
4. Run the `app` configuration or execute `:app:assembleDebug`.
5. Install the debug APK on the phone and check the initial dashboard.

COTG's on-device build uses its bundled Gradle/AGP versions. This repository intentionally does not add a partial Gradle wrapper (a wrapper script without its matching wrapper JAR would be incomplete).

## Product direction

The intended architecture is:

- **Android application:** our own UI, file handling, settings and Hindi Font Center.
- **Document engine:** evaluate and integrate a reusable open-source engine rather than writing DOCX/XLSX/PPTX support from scratch.
- **Hindi typography:** reliable Devanagari shaping, font discovery, font import and missing-font handling.
- **Legacy conversion:** separate encoding profiles for formats such as Kruti Dev and DevLys; never assume that installing a legacy font converts its text to Unicode.

See:
- [Project roadmap](docs/ROADMAP.md)
- [Hindi font compatibility strategy](docs/HINDI_FONT_STRATEGY.md)

## Project principles

1. Offline-first; no compulsory account.
2. No ads or tracking.
3. Open-source dependencies with license review.
4. Never claim a file format or Hindi encoding is supported until it passes real test documents.
5. Preserve original files; conversion must create a new output unless the user explicitly chooses otherwise.
6. Keep the Android build manageable from Code On The Go wherever technically possible.

## License

The project license will be selected after the document-engine and font dependencies have been audited. Do not bundle third-party font files or engine binaries until their redistribution terms are confirmed.
