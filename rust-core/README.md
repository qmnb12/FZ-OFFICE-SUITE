# FZ Office Rust Core

This is the new platform-independent foundation for FZ Office's document engines.

## Current milestone

- Document-family detection from PDF signatures, ZIP signatures, MIME types and extensions.
- Shared page geometry model with OOXML twips-to-points conversion.
- Configurable input and archive resource limits.
- Unit tests; no third-party Rust crates are required at this stage.
- Builds as both an rlib and cdylib, ready for a later Android JNI bridge.

## Migration plan

1. Stabilize the Rust core and Android bridge.
2. Implement OOXML package reading and DOCX layout in Rust.
3. Implement XLSX parsing and the lightweight spreadsheet editor engine.
4. Implement PPTX slide extraction and rendering.
5. Implement PDF integration behind the same FZ-owned engine API.
6. Remove old Gander and MJ PDF integrations only after parity tests pass.

The existing Android implementation remains active during migration so each new engine can be tested without removing working functionality.
