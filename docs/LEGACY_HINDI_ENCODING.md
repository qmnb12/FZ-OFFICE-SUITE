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

The current profile is for Kruti Dev 010. It is not a verified universal decoder for every Kruti Dev version. Many DevLys 010 documents use a similar keyboard layout, but the uncommon glyphs and punctuation need their own fixtures. Chanakya, Shusha, Shree Lipi, APS-DV and other DTP encodings are not converted by this profile.

Legacy converters cannot reliably distinguish English words typed using the same legacy font from Hindi keystrokes. After conversion, review the entire result for English names, email addresses, numbers, dates, legal references, abbreviations and uncommon symbols. For important complaints, legal notices or government files, use Save As first and compare against the original.

## What to send when a file still looks wrong

The most useful diagnostic input is the original DOCX file, not a photograph of its screen. The DOCX package contains the font declarations and stored text needed to identify the actual encoding profile. If available, also supply the exact matching TTF/OTF font name or a legally obtained font file.


## Mixed Hindi/English and matra handling

The viewer now converts a complete paragraph rather than each low-level Word text run separately. This is important because Word often splits a matra and its base consonant into adjacent runs. The converter preserves uppercase abbreviations such as ACEO, common English words and numeric/date/reference tokens. This reduces accidental changes but cannot perfectly distinguish every English word from legacy keyboard text when both use the same encoding and similar letter patterns. Review any document used for official or legal purposes.
