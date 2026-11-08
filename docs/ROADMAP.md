# Project roadmap

This roadmap separates a working Android shell from actual office-engine capability. Items are not complete until they are implemented and tested with sample files.

## Stage 0 — Android foundation

- [x] Create a Java-only Android project for Code On The Go.
- [x] Add the first Hindi-first dashboard.
- [x] Add a document picker entry point (selection only; no parser/editor yet).
- [x] Add a visible Hindi Font Center prototype.
- [x] Add a GitHub Actions debug build.
- [ ] Confirm the first build on the owner's Code On The Go installation.
- [ ] Verify the CI APK installs and launches on a physical Android phone.

## Stage 1 — Engine selection and proof of concept

Evaluate, do not blindly embed:

- Collabora / LibreOffice Android and LibreOfficeKit integration.
- LibreOffice licensing, mobile integration constraints, native libraries and APK size.
- ONLYOFFICE mobile/document editor licensing and reusable components.
- A smaller document-rendering library only as a temporary preview fallback.

Deliverable: a short decision record with exact upstream repository, license, build requirements, Android ABI support, offline behavior, and integration risk.

## Stage 2 — Hindi typography proof of concept

- Add Unicode font metadata and a font sample screen.
- Test Devanagari shaping: matras, reph, rakar, half forms, conjuncts, nukta, ZWJ/ZWNJ and punctuation.
- Import user-provided TTF/OTF fonts into app-private storage (after validating font files).
- Add missing-font detection and safe substitution.
- Do not bundle third-party font binaries until redistribution licenses are verified.

## Stage 3 — Legacy Hindi text

- Establish separate, documented encoding profiles for Kruti Dev, DevLys, Chanakya, Shusha and other families.
- Create test fixtures with known input/output pairs.
- Convert into Unicode in a new copy; never silently overwrite the source.
- Preserve paragraph boundaries and document structure where supported.
- Report ambiguous or unknown encodings instead of guessing.

## Stage 4 — Office modules

1. Writer: DOCX, DOC, ODT and TXT.
2. Calc: XLSX, XLS, ODS and CSV.
3. Impress: PPTX, PPT and ODP.
4. PDF export and print/share flows.

Each format must have open, edit, save, reopen and export regression tests.

## Stage 5 — Quality and release

- Offline and low-memory tests.
- Accessibility, screen-size and dark-theme checks.
- File permission and privacy review.
- Dependency/license and security audit.
- Stable versioning and reproducible debug/release builds.
