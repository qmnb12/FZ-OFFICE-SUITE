# Modern Office editor integration

## Goal

Replace the old Collabora/LibreOfficeKit editing path with the offline editor implementation from
`vayun-mathur/Modern-Apps` (`office/`). Keep FZ Work Suite's existing home screen, document
library, Hindi font center, PDF reader, and normal read-only viewers. The upstream app shell is
not being copied.

## Non-goals

- Do not add Collabora, LibreOfficeKit, UNO, a native LibreOffice build, or an NDK-heavy document rendering pipeline.
- Do not copy Modern-Apps home screens, app navigation, branding, online collaboration, E2EE, relay/network services, accounts, or cloud features.
- Do not make document viewing invoke the editor automatically.
- Do not request write access to the original document merely to open it.

## Required user flow

1. Open a supported document from FZ's library or Android file picker.
2. Keep the existing FZ read-only viewer as the default.
3. Show an explicit **Edit** action for formats supported by the integrated editor.
4. Only after Edit is tapped, open the local Modern Office editing surface with the selected URI.
5. Preserve the original URI and package; save safely to the original only when a valid write grant exists, otherwise offer Save As. Never silently overwrite or corrupt the source.
6. Returning from the editor returns to the same FZ viewer/document.

## Upstream source scope

Pinned source repository: https://github.com/vayun-mathur/Modern-Apps

Initial source area: `office/` and the minimum required document model/conversion code from
`library/ui/src/main/java/com/vayunmathur/library/ui/odf/`.

Candidate functionality to port:
- ODF and OOXML importers for DOCX/XLSX/PPTX and ODT/ODS/ODP.
- Text document, spreadsheet, and presentation editing surfaces.
- OOXML/ODF export and local save flow.
- Formatting, tables, images, sheet operations, slide operations, and undo/redo where their offline dependencies are satisfied.

Exclude online collaboration and the sync/relay/crypto stack. Keep the editor local-first.

## Dependency and build constraints

The upstream `:office` module is not a standalone Android library: it uses convention plugins,
`:library:ui`, `:library:network`, `:library:e2ee-p2p`, and a Rust `office_engine` JNI bridge.
Do not copy its Gradle file unchanged or import the full upstream root build. Extract only the
minimal offline source/dependency graph and remove network/collaboration references.

The upstream Rust bridge is used for formula evaluation and CRDT collaboration. Do not add the CRDT
or sync stack. Prefer the existing Kotlin formula implementation for offline calculation where
feature parity is sufficient; only retain/build a small native formula component if required and
after proving it does not recreate the long Collabora build problem.

FZ currently uses a Java Android application module and a lightweight Gradle build. Preserve its
existing JDK/SDK/Gradle workflow and Code On The Go compatibility as far as possible. Any Kotlin /
Compose additions must be version-aligned with the current project rather than copying upstream
alpha toolchain versions.

## License

The upstream Modern-Apps repository is GPL-3.0. Before distributing a build containing adapted
source, retain required copyright/license notices, identify modified files, include the GPL-3.0
license text, and satisfy corresponding-source obligations for the distributed app. Do not remove
upstream attribution.

## Acceptance checks

- No Collabora/LibreOfficeKit code, activities, dependencies, workflows, build scripts, or native artifacts remain in the FZ build path.
- A normal open always starts in the existing FZ viewer.
- Edit is explicit and opens the integrated local editor for supported formats only.
- DOCX, XLSX, PPTX, ODT, ODS, and ODP are tested for open-edit-save-reopen round trips.
- Hindi Devanagari shaping, conjuncts, matras, punctuation, and selected FZ fonts survive editing and export.
- Unsupported features fail safely with a clear message; they must not silently disappear on save.
- The Android build remains a regular Gradle app build without the Collabora native compilation pipeline.