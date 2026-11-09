# Project roadmap

Items are complete only after implementation and real-file testing. UI labels alone do not count as format support.

## Current baseline — View-only readers

- [x] FZ home screen and local recent/starred document library.
- [x] PDF reader with page navigation and bounded zoom.
- [x] DOCX and TXT read-only FZ Writer reader.
- [x] XLSX/XLS/XLSM/XLSB/XLTX/CSV/ODS read-only spreadsheet preview.
- [x] PPTX/PPSX/PPTM/POTX read-only presentation preview.
- [x] Hindi Font Center with bundled Unicode Devanagari fonts.
- [x] Local TTF/OTF and ZIP font import with metadata, validation and deduplication.
- [x] Explicit read-only URI access; original documents are not modified.

## Stage 1 — Reader reliability

- [ ] Validate DOCX page geometry, margins, page gaps, tables and images against real documents.
- [ ] Improve long-document scrolling, zoom controls and memory usage.
- [ ] Add clear unsupported-format guidance and safe file error recovery.
- [ ] Test spreadsheet formulas, cell formatting, frozen panes and larger workbooks.
- [ ] Test presentation layout, images, text boxes and multi-slide navigation.

## Stage 2 — Hindi typography

- [x] Bundle 12 licence-verified Unicode Devanagari families with OFL texts.
- [x] Search bundled and imported fonts by family, style and filename.
- [x] Show total, bundled and locally imported font counts.
- [x] Keep imported fonts in private app storage.
- [ ] Expand the Devanagari catalogue after coverage and licence review.
- [ ] Test matras, reph, rakar, conjuncts, nukta, ZWJ/ZWNJ and mixed Hindi/English text on physical devices.
- [ ] Improve family matching for DOCX run-level and inherited font declarations.

## Stage 3 — Legacy Hindi compatibility

- [x] Detect legacy font declarations in DOCX files.
- [x] Keep the Kruti Dev 010 conversion preview separate from normal document viewing.
- [ ] Add verified, version-specific profiles for DevLys, Kruti Dev, Chanakya, Shusha and Shree Lipi.
- [ ] Never guess a mapping or silently convert legacy text.
- [ ] Keep the source document unchanged.

## Stage 4 — Local library and release quality

- [ ] Add user-selected SAF folder browsing and indexed library locations.
- [ ] Add file metadata, collections and multi-select actions.
- [ ] Add accessibility, screen-size and low-memory tests.
- [ ] Complete file permission, privacy and security review.
- [ ] Build and test release APKs on physical Android devices.
