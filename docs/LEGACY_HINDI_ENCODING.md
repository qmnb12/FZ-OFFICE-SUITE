# Legacy Hindi text inside Word documents

## Why a DOCX can open as English-like gibberish

Some older Hindi Word documents store ordinary Latin keyboard characters in the document XML and rely on a legacy font such as Kruti Dev or DevLys to draw Hindi glyphs. A Unicode Devanagari font cannot reinterpret those stored character codes. The document may therefore open successfully while its visible text looks like random Latin letters.

Changing a font is not the same as converting the stored text.

## Current support in FZ Office

- DOCX font declarations are inspected in word/document.xml, word/styles.xml, header/footer parts and word/fontTable.xml.
- A font name containing Kruti Dev or DevLys can trigger a legacy-font warning.
- The Writer toolbar has a manual Unicode action for a Kruti Dev 010 conversion preview.
- The source document is not rewritten until the user chooses Save.
- The conversion implementation is based on BharatType Hindi Text Utilities, MIT License. The license is packaged in the app.

## Important conversion limits

The app supports explicit DevLys 010 and Kruti Dev 010 font declarations. Their ordinary keyboard maps are shared; DevLys smart-quote substitutions are handled separately. Other versions such as DevLys 040, Chanakya, Shusha, Shree Lipi and APS-DV are not covered by this profile. Chanakya, Shusha, Shree Lipi, APS-DV and other DTP encodings are not converted by this profile.

Legacy converters cannot reliably distinguish English words typed using the same legacy font from Hindi keystrokes. After conversion, review the entire result for English names, email addresses, numbers, dates, legal references, abbreviations and uncommon symbols. For important complaints, legal notices or government files, use Save As first and compare against the original.

## What to send when a file still looks wrong

The most useful diagnostic input is the original DOCX file, not a photograph of its screen. The DOCX package contains the font declarations and stored text needed to identify the actual encoding profile. If available, also supply the exact matching TTF/OTF font name or a legally obtained font file.


## Mixed Hindi/English and matra handling

The viewer now converts a complete paragraph rather than each low-level Word text run separately. This is important because Word often splits a matra and its base consonant into adjacent runs. The converter preserves uppercase abbreviations such as ACEO, common English words and numeric/date/reference tokens. This reduces accidental changes but cannot perfectly distinguish every English word from legacy keyboard text when both use the same encoding and similar letter patterns. Review any document used for official or legal purposes.


## v0.5.2 safety change

The viewer no longer converts all text in a paragraph based on content-pattern guesses. Automatic DevLys 010 or Kruti Dev 010 conversion is restricted to individual Word runs that carry an explicit exact matching font declaration. Unmarked runs remain unchanged. This can mean that a file using only paragraph-style inheritance will show a warning and remain unconverted until the style inheritance is resolved; this is intentional to avoid damaging English and Unicode Hindi text.


## DevLys 010 support (v0.5.3)

DevLys 010 is now detected as its own exact DOCX font profile. The ordinary key map is shared with Kruti Dev 010, while Word smart-quote code points are handled with DevLys-specific substitutions (`’` → श्, `“` → भ; unused curly quote slots are dropped). Other DevLys versions are not assumed compatible.
