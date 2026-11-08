# Project roadmap

Items are complete only after implementation and real-file testing. UI labels alone do not count as format support.

## Stage 0 — Android foundation

- [x] Create a Java-first Android project for Code On The Go.
- [x] Add the Hindi-first dashboard.
- [x] Add document-picker entry point.
- [x] Add GitHub Actions debug-build workflow.
- [x] Confirm a first APK installs and launches on a physical Android device.

## Stage 1 — Writer MVP

- [x] Add a local, offline HTML editing surface in Android WebView.
- [x] Create a blank Writer document.
- [x] Open UTF-8 TXT files.
- [x] Open DOCX package main-body paragraphs and simple tables.
- [x] Support basic direct run formatting: bold, italic, underline, strike, color, font size and font family.
- [x] Add basic formatting toolbar, lists, alignment, undo/redo and table insertion.
- [x] Save as DOCX or TXT and attempt in-place save when the document provider allows it.
- [ ] Add automated DOCX round-trip tests with a corpus of real files.
- [ ] Add better preservation for images, hyperlinks, headers, footers, page breaks, lists and section properties.
- [ ] Add document outline, page layout, print preview and spell-check settings.
- [ ] Test with real Hindi documents and Devanagari shaping cases.

### Current Writer compatibility boundary

The first DOCX bridge is a deliberately limited WordprocessingML reader/writer. It retains package parts, but replaces word/document.xml with a new body generated from the editable content. Complex document body elements that are not represented in the current editor are not guaranteed to survive. Keep a backup of important originals.

## Stage 2 — Hindi typography

- [x] Add 12 bundled Unicode Devanagari font families with corresponding OFL license texts.
- [x] Pin upstream font revision and verify size plus Git blob SHA-1 at build time.
- [x] Add a Font Center with font previews and default Writer font selection.
- [x] Import user-provided TTF/OTF files into app-private storage after signature and Typeface validation.
- [x] Add Writer font switching and Hindi special-character insertion.
- [x] Add an in-app Devanagari shaping sample covering matras, reph/rakar, conjuncts, nukta, ZWJ/ZWNJ and numerals.
- [ ] Test the full corpus on multiple Android WebView versions and compare reference screenshots.
- [ ] Add missing-font detection and document-level substitution warnings.

## Stage 3 — Legacy Hindi text

- [x] Add a manual Kruti Dev 010 to Unicode preview based on an MIT-licensed mapping implementation.
- [x] Inspect DOCX font declarations in document/styles/header/footer/font-table parts and flag likely Kruti Dev/DevLys fonts.
- [ ] Verify Kruti Dev 010 conversion against a large golden fixture corpus and real official documents.
- [ ] Create separately verified encoding profiles for DevLys, Chanakya, Shusha, Shree Lipi and other font families.
- [ ] Build test fixtures with known input/output pairs for each font version.
- [ ] Convert to Unicode in a new copy; never silently overwrite the source.
- [ ] Preserve paragraph boundaries and document structure where supported.
- [ ] Report ambiguous or unknown encodings instead of guessing.

## Stage 4 — Remaining office modules

1. Sheets: XLSX, XLS, ODS and CSV.
2. Slides: PPTX, PPT and ODP.
3. PDF viewing, export and print/share flows.
4. Evaluate an office engine for higher-fidelity cross-format rendering and editing.

Each format needs open, edit, save, reopen and export regression tests.

## Stage 5 — Quality and release

- [ ] Offline and low-memory tests.
- [ ] Accessibility, screen-size and dark-theme checks.
- [ ] File permission and privacy review.
- [ ] Dependency/license and security audit.
- [ ] Reproducible debug/release builds.
