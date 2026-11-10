# FZ Rust document engine

This crate is the in-repository native engine for FZ Office. It is independent of Android UI code and builds as both a Rust library and an Android JNI shared library named libfzcore.so.

## First migration milestone

- Detects PDF, ZIP-based Office formats and legacy OLE containers from a small file header plus filename.
- Validates DOCX, XLSX and PPTX ZIP package structure without extracting files.
- Limits archive entry count, total expanded size and the content-types manifest size.
- Exposes a small JNI surface to Kotlin.
- Includes host-side unit tests for signature detection and OOXML package validation.

The Android app currently uses the Rust signature detector to recognize PDFs even when the file provider omits a .pdf suffix and MIME type. inspectPackage(File) is the native entry point for the next DOCX/XLSX/PPTX parsing migration.

## Android build

GitHub Actions builds libfzcore.so for arm64-v8a, armeabi-v7a, x86 and x86_64 using the Android NDK and cargo-ndk. The shared libraries are committed under app/src/main/jniLibs so Code On The Go can package them without installing Rust or compiling native code locally.

## Migration rule

Do not remove an existing reader until its Rust replacement passes the same format-specific regression tests and is verified in an Android build.
