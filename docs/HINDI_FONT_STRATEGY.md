# Hindi typography and encoding strategy

Hindi support requires more than displaying Devanagari glyphs. The app must combine Unicode text, an Indic shaping engine, a font with the needed glyphs, and (for legacy files) a verified encoding converter.

## 1. Unicode Devanagari

Writer uses Android WebView's text layout/shaping stack and local font files. The editor declares Hindi as its document language and uses Noto Sans Devanagari as its general-purpose default. Font Center lets users select another family and import local TTF/OTF fonts.

Bundled families:

- Noto Sans Devanagari — variable sans-serif; default general-purpose font.
- Noto Serif Devanagari — variable serif.
- Mukta — regular and bold.
- Hind — regular and bold.
- Tiro Devanagari Hindi — regular and italic.
- Kalam — handwriting.
- Sahitya — regular and bold.
- Martel — regular.
- Baloo 2 — variable rounded display.
- Khand — condensed display.
- Gotu — rounded.
- Glegoo — serif display.

The current catalogue contains 12 families. This is a useful starter collection, not every Devanagari typeface ever published. Users can add their own TTF/OTF fonts through Font Center. Do not add font files without checking redistribution rights.

## 2. Required shaping and coverage tests

The in-app preview and DEVANAGARI_TEST_CORPUS.md cover:

- Matras before, above, below and after consonants.
- Reph, rakar, half forms and conjunct clusters.
- Nukta characters and precomposed/decomposed forms.
- Anusvara, chandrabindu, visarga, danda, double danda and Devanagari digits.
- ZWJ and ZWNJ sequences.
- Hindi mixed with Latin text and the rupee sign.

A font may be installed while still lacking some code points. In those cases the Android/WebView fallback font can draw missing glyphs. That is why Noto Sans Devanagari remains the default and each family's preview must be checked. The first bundled collection has not yet been validated against a comprehensive real-document corpus on every Android WebView version.

## 3. User font import

- Import TTF and OTF only; TTC collections are not enabled in this build.
- Files are limited to 5 MB and checked for a supported font signature and Android Typeface loadability.
- Imported font files are copied into app-private storage; the original source file is not modified.
- The file name is used as the CSS family label. For best matching with a legacy DOCX, name the file after the family name embedded in that document.
- Imported fonts are private to this installation and are not uploaded.
- The app cannot determine whether a user's font may legally be redistributed or embedded in a document.

## 4. Legacy Hindi font encodings

Kruti Dev, DevLys, Chanakya, Shusha, Shree Lipi, Akruti and similar families may use legacy character mappings instead of Unicode. A font alone does not convert the stored data.

The app does not redistribute proprietary legacy font binaries. Users may import fonts they are licensed to use. Legacy conversion is not implemented in this release; future support must contain separate mapping profiles for each actual font/version (for example, Kruti Dev 010 cannot be treated as every Kruti Dev variant).

Converter requirements:

- Known input/output fixtures for every version.
- Correct pre-base vowel reordering and conjunct handling.
- Preview before conversion.
- Unknown/ambiguous mapping warnings.
- Save converted data as a new copy by default.
- Never silently overwrite the source.

## 5. Document engine integration

The selected document engine must be tested for Devanagari shaping in editing, rendering, print and PDF export; custom-font discovery and substitution; DOCX font metadata; and font embedding rules. The current Writer bridge stores font-family choices in DOCX run properties but does not embed font files inside documents.

## 6. Acceptance rule

A font is listed as bundled when the binary and license are packaged. It is not labelled fully compatible with every Hindi document until it passes the real-device corpus and round-trip tests.


## 7. First legacy conversion preview

Writer now includes a manual Kruti Dev 010 to Unicode preview based on the MIT-licensed BharatType Hindi Text Utilities converter. DOCX font declarations are inspected in document, styles, header/footer and font-table XML parts; names containing Kruti Dev or DevLys can trigger a prompt.

The conversion is a preview operation on the editor's current text. The app does not silently convert at open time. Before saving, users must inspect the entire document, especially English text and uncommon glyphs typed in a legacy font. The current mapping is not a universal converter for every Kruti Dev or DevLys version. Chanakya, Shusha, Shree Lipi and other families still need their own verified mapping profiles.

Original documents remain unchanged until the user chooses Save. Save As is recommended for important legal or official documents.
