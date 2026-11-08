# Hindi font compatibility strategy

Hindi font support has two separate technical parts. The app must not treat them as the same problem.

## 1. Unicode Devanagari

Modern Unicode text stores Devanagari characters as Unicode code points. Correct display still depends on font coverage and a shaping engine that handles Indic reordering and conjunct formation.

Initial font families to evaluate (not bundled yet):

- Noto Sans Devanagari
- Noto Serif Devanagari
- Mukta
- Hind
- Lohit Devanagari
- Shobhika
- Other families after license and glyph-coverage review

Test cases must cover:

- Independent vowels and consonants.
- Dependent vowel signs before, above, below and after consonants.
- Reph and rakar.
- Half forms and conjunct clusters.
- Nukta characters.
- ZWJ and ZWNJ sequences.
- Vedic marks and Sanskrit combinations where applicable.
- Numerals, danda, punctuation and mixed Hindi/Latin text.

## 2. Legacy font encodings

Fonts such as Kruti Dev, DevLys, Chanakya and Shusha often rely on legacy character mappings rather than Unicode Devanagari text.

A legacy font file can change how stored character codes look, but it does not rewrite those codes into Unicode. Correct migration requires a converter for the exact encoding/profile.

Planned converter requirements:

- Separate mapping profile per font family and variant (for example, Kruti Dev 010 is not assumed identical to every other variant).
- Verified fixtures with source text, expected Unicode output and reverse-conversion cases where possible.
- Handling for pre-base vowel signs and reordering rules.
- Unknown/ambiguous input warning.
- Preview before conversion.
- Save converted content as a new copy by default.

## 3. User font import

The target design is an app-private font library:

- Import TTF/OTF (TTC support only after explicit validation).
- Validate the file before registering it.
- Store imported fonts inside app-private storage.
- Show family name, style, weight and a preview sentence.
- Allow the user to remove imported fonts.
- Never assume that an imported font is licensed for redistribution or embedding.

## 4. Document engine integration

The selected document engine must be tested for:

- Devanagari shaping in editing and rendering.
- Font fallback when the exact font is absent.
- Font substitution warnings.
- Print/PDF export fidelity.
- DOCX font metadata and embedded-font behavior.
- Whether custom fonts can be made available to the engine without patching its native core.

## 5. Acceptance rule

No Hindi font or legacy encoding is marked as supported merely because its name appears in a list. It must pass a checked sample document on a real Android device, and the result must be compared with an expected rendering or Unicode output.
