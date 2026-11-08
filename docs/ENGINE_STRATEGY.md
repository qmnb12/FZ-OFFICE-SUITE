# Office engine decision: Collabora / LibreOffice native engine

**Decision status:** selected; upstream source pinned; native FZ APK not yet built.

## Why the current renderer is being retired

The in-house HTML/DOCX parser is a prototype. It cannot reliably reproduce Word pagination, section layout, floating images, text boxes, charts, fields, comments, tracked changes, complex tables, numbering, headers/footers and style inheritance. Continuing to add individual parsers is the wrong way to reach desktop-level document fidelity.

FZ Office will reuse a mature open-source document engine instead of maintaining its own DOCX/XLSX/PPTX parser.

## Selected engine

Use the Android application and native engine from **Collabora Office**, based on LibreOffice.

Pinned source:
- Upstream mirror: https://github.com/CollaboraOnline/online.mirror
- Pinned commit: ec0238c3ff83d2806225765fcccd7a7dc67e5da4
- Submodule path: third_party/collabora
- Active development/review: https://gerrit.collaboraoffice.com/online
- Android source/build instructions: third_party/collabora/android/README.md

The GitHub mirror is read-only; the submodule is a reproducible source pin, not a fork that accepts upstream contributions. Preserve upstream notices and all third-party license files.

### Why this is the best fit

Collabora Office for Android bundles the LibreOffice core in a native Android library and uses LibreOfficeKit for document processing/rendering. This is much closer to desktop LibreOffice than the custom HTML reader and is designed for local Android documents. The native core is the common engine for Writer, Calc and Impress.

The target is **PC-like document rendering and broad office compatibility**, not a promise of pixel-identical rendering for every Microsoft Word version. Real DOCX files will be compared against Microsoft Word and LibreOffice desktop and any differences recorded.

## Alternatives considered

| Option | Decision |
|---|---|
| ONLYOFFICE mobile WebView integration | Not the primary offline engine. Its documented embedded mobile web editor requires ONLYOFFICE Docs Enterprise or Developer and a Document Server. The standalone Android app can work with local files, but that is a separate app, not the documented self-contained mobile embed route. |
| Fossify Documents | Use only as a UX reference for offline file organization. Its DOCX experience is a reading view; editing focuses on text/Markdown/CSV/TSV, not a full Word-compatible engine. |
| AndrOpen Office | Not selected. It is an independent Apache OpenOffice Android port; its source page says only parts of the code/extensions are published. |
| Hand-written DOCX parser | Retired. It cannot economically reach Word-level layout and round-trip compatibility. |

## Target application architecture

1. **FZ Office shell** — home, recents, local file management, favorites, search, themes, Hindi Font Center and settings.
2. **Collabora Android client** — document browser and native Android document-opening integration.
3. **LibreOffice native core / LibreOfficeKit** — document import, layout, render, document features and export.
4. **Hindi Font Service** — packaged open-license fonts, app-private custom-font folder, family aliases, missing-font reports and font-cache refresh.
5. **Legacy Encoding Service** — separate conversion profiles for each exact legacy family/version. No content-only guessing and no silent replacement.
6. **QA corpus** — real Hindi DOCX/XLSX/PPTX files with expected rendering and save/reopen checks.

The mobile UX will remain reader-first by default. A document opens in a comfortable reading experience; editing can be an explicit action/settings choice rather than a dense toolbar on every open. The underlying engine remains capable of full office editing.

## Native build constraints

The upstream Android engine is not a normal Gradle dependency. It must be cross-compiled with the Android NDK on Linux or macOS before the Android Gradle app is packaged.

The pinned upstream tree currently specifies:
- Android NDK 29.0.14206865
- Android compile SDK 37 / build tools 37
- Minimum Android SDK 26
- Supported ABIs: armeabi-v7a, arm64-v8a, x86 and x86_64

The first native validation target is arm64-v8a to limit build scope. Additional ABIs can be added after the native pipeline is stable.

**Code On The Go alone cannot compile the C/C++ LibreOffice engine.** COTG remains useful for Java/Gradle app-level work, but the native build must run on a Linux GitHub Actions runner or another Linux build host. The existing lightweight Android workflow is not the native-engine build.

## License and branding rules

- Collabora Online/Office is primarily MPL-2.0, with additional third-party components under their own licenses.
- Keep upstream notices, source offers, license files and third-party notices required by the relevant licenses.
- Do not reuse Collabora trademarks, logos or branded artwork as FZ Office branding.
- Keep FZ Office UI and Hindi integration patches separate and documented.
- Do not redistribute commercial/legacy fonts without permission.

## Acceptance gates before replacing the current APK

1. The pinned submodule checks out at the recorded commit.
2. The NDK build produces the native LibreOffice library for arm64.
3. The Android package builds with applicationId com.fz.office.
4. A DOCX with paragraphs, styles, tables, page breaks, images, headers/footers and embedded fonts opens with desktop-like layout.
5. Hindi Unicode documents render matras, reph, rakar, conjuncts, nukta, ZWJ and ZWNJ correctly.
6. A legacy DevLys 010 DOCX renders with its matching imported font; missing-font behaviour is reported clearly.
7. XLSX and PPTX open; ODS/ODP and PDF workflows are checked.
8. Save/reopen and export regression tests pass before editing mode is promoted.
9. No APK is labelled Collabora-backed until these CI gates pass.

The current v0.5.x APK remains a limited transitional viewer; it is not the target engine.
