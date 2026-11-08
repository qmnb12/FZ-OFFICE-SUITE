# Project roadmap

Items are complete only after implementation and real-file testing. UI labels alone do not count as format support.

## Stage 0 — Prototype (historical)

- [x] Create the Java-first Android shell and GitHub Actions debug workflow.
- [x] Create a limited DOCX/TXT viewer prototype.
- [x] Add prototype Unicode font catalogue and font import UI.
- [ ] Do not treat the prototype parser or converter as production-compatible.

## Stage 1 — Office engine migration (selected: Collabora / LibreOfficeKit)

- [ ] Pin the Collabora Online monorepo revision and preserve upstream license/third-party notices.
- [ ] Build the native LibreOffice engine for arm64-v8a on a cloud Linux runner.
- [ ] Build and package the Collabora Android application with FZ Office branding.
- [ ] Replace the custom DOCX WebView renderer with the native LibreOfficeKit-based viewer.
- [ ] Keep the default opening mode read-only.
- [ ] Verify DOCX images, tables, styles, sections, headers/footers, page breaks and floating objects.
- [ ] Verify XLSX/XLS/ODS/CSV and PPTX/PPT/ODP viewing.
- [ ] Verify PDF viewing, print and export flows.
- [ ] Keep a reproducible CI artifact and source revision.

## Stage 2 — Hindi font integration

- [ ] Integrate a licensed Devanagari font pack into the engine font path.
- [ ] Build an app-private TTF/OTF importer that registers fonts with LibreOffice's user profile.
- [ ] Refresh the engine font cache and show the exact missing font in document information.
- [ ] Add font substitution/fallback choices without altering the document's saved font metadata.
- [ ] Validate matras, reph, rakar, conjuncts, nukta, ZWJ/ZWNJ, numerals and punctuation on Android.
- [ ] Add Hindi spell-check dictionary and hyphenation where licence-compatible.

## Stage 3 — Legacy Hindi fonts and encoding

- [ ] Test user-provided DevLys 010 font in actual DOCX.
- [ ] Add separate, documented profiles for each DevLys and Kruti Dev version.
- [ ] Add verified Chanakya, Shusha, Shree Lipi, APS-DV and other profiles where legally and technically possible.
- [ ] Render original legacy text with its exact font first; do not auto-convert on open.
- [ ] Keep Unicode conversion opt-in, previewable and non-destructive.
- [ ] Build golden fixtures from real documents for punctuation, English tokens, matras and mixed-language paragraphs.

## Stage 4 — FZ Office app experience

- [ ] Build document home, recent files, folders, search and favourites around the engine.
- [ ] Add app-level read-only mode and optional editing mode only after stability testing.
- [ ] Add Hindi Font Center, file info, share/open-with, print and export flows.
- [ ] Offline-first; no compulsory account and no document upload.

## Stage 5 — Quality and release

- [ ] Compare real user documents against Microsoft Word and Collabora desktop output.
- [ ] Test large files, password-protected files, damaged packages and missing fonts.
- [ ] Run offline, low-memory, accessibility and Android-version tests.
- [ ] Complete dependency/license/security audit.
- [ ] Publish upstream source notices and reproducible build instructions.

## Important boundary

Collabora/LibreOfficeKit has been selected, but it is **not yet integrated**. Do not label any current v0.5.x build as a full Microsoft Word replacement.
