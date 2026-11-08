# Project roadmap

Items are complete only after implementation and real-file testing. UI labels alone do not count as format support.

## Stage 0 — Transition to native office engine

- [x] Evaluate Collabora/LibreOffice, ONLYOFFICE, Fossify Documents and AndrOpen Office.
- [x] Select Collabora Office Android / LibreOffice native engine as the base.
- [x] Pin the upstream Collabora source revision as a Git submodule.
- [x] Design the app-private Hindi font directory patch.
- [ ] Build the native LibreOffice engine for arm64 in Linux CI.
- [ ] Package the Collabora Android app under com.fz.office.
- [ ] Preserve notices and add FZ Office branding without upstream trademarks.
- [ ] Verify offline open/render on the user's Android device.

## Stage 1 — Transitional viewer (v0.5.x)

The current custom WebView viewer is retained only until the native engine APK is ready. It is not the final DOCX rendering implementation.

- [x] Open DOCX and UTF-8 TXT files read-only.
- [x] Basic paragraph and simple table rendering.
- [x] Common raster image relationship support.
- [x] Limited legacy conversion experiments for exact font-tagged runs.
- [ ] Retire the custom DOCX parser after native-engine acceptance tests pass.

## Stage 2 — Native Hindi typography and font manager

- [x] Prepare the app-private user/fonts Fontconfig patch against the pinned upstream source.
- [x] Prepare bundled Hindi font packaging patch using the verified 12-family OFL set.
- [x] Prepare native Settings-based TTF/OTF import patch.
- [ ] Build and test the native patches in the Collabora Android project.
- [ ] Bundle the existing 12 licence-verified Devanagari families with their OFL texts.
- [ ] Expand the Unicode Devanagari catalogue after coverage/licence review.
- [x] Prepare Settings-based user TTF/OTF import using Android Storage Access Framework.
- [ ] Build and exercise the import flow on a physical Android device.
- [ ] Restart/reload the native document engine to refresh Fontconfig.
- [ ] Add missing-font detection, family aliasing and fallback reports.
- [ ] Test all shaping cases: matras, reph, rakar, conjuncts, nukta, ZWJ/ZWNJ and punctuation.

## Stage 3 — Legacy Hindi compatibility

- [ ] Prefer rendering with the exact imported legacy font when the DOCX names it.
- [ ] Add a separate exact-version mapping profile for DevLys 010.
- [ ] Add separate verified profiles for Kruti Dev versions, Chanakya, Shusha, Shree Lipi, APS-DV and Akruti where test fixtures and legal mapping sources exist.
- [ ] Preserve English names, abbreviations, numbers, dates, references and punctuation.
- [ ] Add preview/diagnostics for missing legacy fonts; never guess or silently convert.
- [ ] Test style-inherited and run-level Word font declarations.

## Stage 4 — Office module acceptance

- [ ] Writer: DOCX/DOC/ODT/RTF open, render, edit when enabled, save and reopen.
- [ ] Sheets: XLSX/XLS/ODS open, formulas, formatting, charts and save/reopen.
- [ ] Slides: PPTX/PPT/ODP open, layout, images, transitions where supported and export.
- [ ] PDF: view, search, print and export workflows.
- [ ] Verify embedded images, tables, headers/footers, section/page layout and fonts against desktop references.

## Stage 5 — Release quality

- [ ] Offline and low-memory tests.
- [ ] Accessibility, screen-size and reader-first UX checks.
- [ ] File permission, privacy and security review.
- [ ] Full third-party licence/SBOM audit.
- [ ] Reproducible arm64 build, then add other ABIs.
- [ ] Real-document regression corpus before stable release.
