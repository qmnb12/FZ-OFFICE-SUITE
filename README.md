# FZ Office Suite

**Offline-first Android document viewer with Hindi-first typography and a lightweight XLSX editor.**

## Current build

FZ Office is deliberately **view-only**. The native Collabora/LibreOffice and PDFium engines are not part of the app. The PDF reader uses Android's built-in `PdfRenderer` through FZ's Kotlin PDF core, with bounded page rendering and caching.

## FZ Kotlin engine

The document-engine rewrite is **Kotlin-first and written inside FZ Office**. No Rust engine or JNI bridge is used. The first FZ-owned foundation is a pure Kotlin file-signature detector for PDF, ZIP-based Office formats and legacy OLE containers.

This is a staged replacement, not a destructive reset: PDF rendering is now handled by Android's platform API, while spreadsheet and presentation previews continue to use pinned offline viewer assets. The common viewer controls are shared across formats through one Kotlin surface contract.

## Supported readers

- **DOCX and TXT:** FZ Writer's Hindi-aware read-only reader.
- **XLSX, XLS, XLSM, XLSB, XLTX, CSV and ODS:** offline Gander/SheetJS spreadsheet preview.
- **PPTX, PPSX, PPTM and POTX:** offline Gander/PPTXjs presentation preview.
- **PDF:** built-in offline PDF reader with page navigation, bounded zoom and native text Find on Android 15+.

DOCX, TXT, PPTX and PDF screens are read-only. The spreadsheet reader is also read-only by default; only `.xlsx` files offer an optional **Edit a copy** screen. Edited workbooks are saved as a separate new file through Save As. The original file is never overwritten and the app does not request write permission for the source document.

## Current limitations

The DOCX reader uses a lightweight, offline OOXML-aware layout layer over the original DOCX. It reads page geometry, inherited styles, run fonts/sizes, paragraph spacing/line height/indents, and paginates paragraphs and table rows without bundling a native office suite. This is designed to retain the source document's physical page size and formatting rather than shrinking text to force a fit. It is not a full Microsoft Word layout engine: mixed section sizes, headers/footers, floating objects, advanced numbering, and complex table-cell splitting can still render differently. Spreadsheet and presentation previews also have format-specific fidelity limits. Legacy Word formats such as DOC/ODT/RTF and legacy presentation formats such as PPT/ODP are not supported by the current built-in readers; convert those files to a supported format before opening them. PDF text Find uses Android 15 platform support; scanned-only pages without an embedded text layer cannot be searched.

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
