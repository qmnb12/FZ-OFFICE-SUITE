# FZ Office Suite

**Offline-first Android document viewer with Hindi-first typography and a lightweight XLSX editor.**

## Current build

FZ Office is deliberately **view-only**. The native Collabora/LibreOffice engine and its long native compilation pipeline have been removed from this branch. The app builds as a normal Android Gradle project and does not require the LibreOffice C/C++ toolchain or Android NDK cross-compilation.

## FZ Rust core

The in-repository Rust core is being introduced as the long-term document engine. Its first milestone provides native file-signature detection, bounded OOXML ZIP-package inspection, and an Android JNI bridge. Extensionless PDF detection is connected to the app. Existing readers remain available during the staged migration; no format renderer is removed until its Rust replacement is implemented and validated.

Android native libraries are built in GitHub Actions for ARM and x86 ABIs and stored in app/src/main/jniLibs so Code On The Go can package them without compiling Rust locally.

## Supported readers

- **DOCX and TXT:** FZ Writer's Hindi-aware read-only reader.
- **XLSX, XLS, XLSM, XLSB, XLTX, CSV and ODS:** offline Gander/SheetJS spreadsheet preview.
- **PPTX, PPSX, PPTM and POTX:** offline Gander/PPTXjs presentation preview.
- **PDF:** built-in offline PDF reader with page navigation and bounded zoom.

DOCX, TXT, PPTX and PDF screens are read-only. The spreadsheet reader is also read-only by default; only `.xlsx` files offer an optional **Edit a copy** screen. Edited workbooks are saved as a separate new file through Save As. The original file is never overwritten and the app does not request write permission for the source document.

## Current limitations

The DOCX reader uses a lightweight, offline OOXML-aware layout layer over the original DOCX. It reads page geometry, inherited styles, run fonts/sizes, paragraph spacing/line height/indents, and paginates paragraphs and table rows without bundling a native office suite. This is designed to retain the source document's physical page size and formatting rather than shrinking text to force a fit. It is not a full Microsoft Word layout engine: mixed section sizes, headers/footers, floating objects, advanced numbering, and complex table-cell splitting can still render differently. Spreadsheet and presentation previews also have format-specific fidelity limits. Legacy Word formats such as DOC/ODT/RTF and legacy presentation formats such as PPT/ODP are not supported by the current built-in readers; convert those files to a supported format before opening them.

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
