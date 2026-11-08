# Legacy Hindi font rendering and optional conversion

## Default behaviour: render with the original font

Older Hindi DOCX files can store legacy keyboard characters (often Latin code points) and rely on a matching font such as DevLys 010 or Kruti Dev 010 to display Devanagari glyphs.

**Viewing this kind of file does not require text conversion.** Desktop Word normally renders it using the font declared by the document. FZ Office must do the same:

1. Read the exact font family from run properties and paragraph/character styles.
2. Find a matching imported TTF/OTF in the app-private font library.
3. Register that font with the document renderer.
4. Preserve the stored characters and all font declarations.
5. If the font is unavailable, show its exact family and offer font import.

The normal Open/View path must never rewrite the document text using a guessed converter.

## Font versus encoding

A legacy font is a glyph mapping. A legacy encoding is the character sequence stored in the document. Installing a font does not turn the stored codes into Unicode; it makes the stored codes display using that font's glyph map.

A converter is only needed when the user explicitly wants to transform legacy characters into Unicode Hindi, for portability, search, indexing or editing in applications without the legacy font.

## Optional Legacy-to-Unicode conversion

Converters remain separate, opt-in utilities. Each exact family and version needs its own implementation and golden test corpus. Do not assume DevLys 010, DevLys 040, Kruti Dev 010, Chanakya, Shusha or Shree Lipi share one mapping.

Before offering a converter, test:
- Exact font version and internal family name.
- Punctuation, smart quotes, brackets, symbols and numerals.
- Matra ordering, reph, rakar, conjuncts and nukta.
- Mixed Hindi/English, abbreviations, dates, addresses and legal references.
- Real DOCX files with run-level and inherited paragraph/character styles.
- Before/after output against Word with the exact source font installed.

The converter code retained in the repository is not used by normal document viewing. It must not be described as verified for all DevLys/Kruti Dev variants.

## Current rendering implementation

The transitional WebView parses direct run font declarations and resolves paragraph style inheritance through styles.xml. Imported TTF/OTF families are registered as local CSS fonts. The production target is the Collabora/LibreOffice native engine, which reads app-private fonts through Fontconfig.

If a font is missing, the viewer leaves text unchanged and shows a warning.
