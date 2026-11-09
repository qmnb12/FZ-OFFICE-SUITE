# FZ Office Suite

**Offline-first Android document reader with Hindi-first typography.**

## Current build

FZ Office is deliberately **view-only**. The native Collabora/LibreOffice engine and its long native compilation pipeline have been removed from this branch. The app builds as a normal Android Gradle project and does not require the LibreOffice C/C++ toolchain or Android NDK cross-compilation.

## Supported readers

- **DOCX and TXT:** FZ Writer's Hindi-aware read-only reader.
- **XLSX, XLS, XLSM, XLSB, XLTX, CSV and ODS:** offline Gander/SheetJS spreadsheet preview.
- **PPTX, PPSX, PPTM and POTX:** offline Gander/PPTXjs presentation preview.
- **PDF:** built-in offline PDF reader with page navigation and bounded zoom.

All document screens are read-only. There is no Edit button, no save-back path and no write permission requested from document providers. Original files remain unchanged.

## Current limitations

The DOCX reader is a custom, transitional renderer and does not promise Microsoft Word-identical pagination or support for every advanced Word feature. Spreadsheet and presentation previews also have format-specific fidelity limits. Legacy Word formats such as DOC/ODT/RTF and legacy presentation formats such as PPT/ODP are not supported by the current built-in readers; convert those files to a supported format before opening them.

## FZ app features

- Local recent-document library with private URI history, search, Starred shortcuts, share and remove-from-history actions.
- Hindi Font Center with 12 bundled Unicode Devanagari families, all-font search, total font counts and local TTF/OTF/ZIP import.
- Imported fonts stay in app-private storage and are not uploaded.
- Offline viewer assets are pinned to immutable upstream revisions and checksum-verified at build time.
- No compulsory account, hosted document server or ads.

## Build

- Android Gradle build using JDK 21 and Android SDK 36.
- Code On The Go can be used for Java/Gradle app-level builds.
- GitHub Actions workflow **FZ Office shell validation** runs the viewer tests and builds the debug APK.

The installed app reads supported documents locally. Network access is not required while viewing documents.
