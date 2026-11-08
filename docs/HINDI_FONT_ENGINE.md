# Hindi fonts with the native office engine

## Goal

Make FZ Office use the same font-resolution/rendering path as the real office engine. A list of fonts in an HTML preview is not enough: the engine that reads the DOCX must be able to locate and load the exact font named in the document.

## Three categories

### 1. Unicode Devanagari fonts

These work with Unicode Hindi text and the engine's Indic shaping implementation. The first bundled pack should include the open-license Devanagari families listed in HINDI_FONTS.md.

Noto Sans Devanagari and Noto Serif Devanagari should be the general-purpose fallbacks. Other styles can be selected for document design.

### 2. Legacy font families

DevLys 010, Kruti Dev, Chanakya, Shree Lipi, Shusha and similar families often rely on non-Unicode character mappings. Their binaries may be commercial/proprietary.

The app will not package these binaries without proof of redistribution rights. It will support importing the user's own licensed TTF/OTF files. When the exact font is available to LibreOfficeKit, the engine can render the original character sequence with the correct glyphs; automatic conversion is not required to view the source document.

### 3. Document-embedded fonts

Some DOCX files contain embedded font data. The engine must respect the font's OS/2 embedding permission flags. If a font cannot legally be embedded or extracted, use the matching installed/imported font or a fallback and report the substitution.

## Collabora Android font path patch

Upstream source files to patch in the Collabora monorepo:

- android/lib/fonts.conf
- android/lib/build.gradle (copyUnpackAssets task)
- android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java or a small FZ FontManager helper for font import/reload.

Current upstream fonts.conf includes /system/fonts and uses an app-specific private fontconfig cache. It does not currently add a user-selected external font directory. The Android source has an unpack step that extracts assets/unpack into <application dataDir>.

Planned changes:

1. Add /data/data/<FZ application id>/user/fonts to the fontconfig scan paths, using the upstream Gradle application-id placeholder rather than hardcoding the final package in source.
2. Keep /system/fonts and Android fallback fonts.
3. Extend copyUnpackAssets to install the bundled open-license Devanagari fonts into user/fonts.
4. Add Font Center import using ACTION_OPEN_DOCUMENT; copy validated TTF/OTF data to <application dataDir>/user/fonts.
5. Clear/regenerate the app-private fontconfig cache and restart or reload the document engine before previewing the new font.
6. Show the exact detected font family and style from font metadata; don't infer the family from an arbitrary file name.
7. Add an in-app coverage preview and test font fallback with missing glyphs.

## Why not ship all Hindi fonts?

There is no finite list of every Hindi font. Some fonts are proprietary, some have different versions and character maps, and licenses can forbid redistribution or embedding. The robust product promise is:

- a large, curated, open-license Unicode Devanagari pack;
- user import for any licensed TTF/OTF font;
- accurate family metadata and engine-level font discovery;
- separate, verified conversion profiles only when needed.

## DevLys 010 validation

The user has identified the source document as DevLys 010. The correct first test is to import the exact DevLys 010 TTF/OTF file that was used to create the desktop DOCX and open the original DOCX without altering its stored text. Compare it against desktop Word output. Only if that still fails should a DevLys-specific text converter be investigated.

The test fixture must include the original DOCX, the exact font file, a desktop reference PDF/image, and expected text extracted from the document. Store test fixtures privately unless the user authorizes adding them to the public repository.
