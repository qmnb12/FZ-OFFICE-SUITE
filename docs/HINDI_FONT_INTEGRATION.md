# Hindi font integration for the Collabora / LibreOffice engine

## Broad font availability

There is no finite, lawful package called "all Hindi fonts". Devanagari fonts have different licences; commercial and proprietary families cannot be redistributed without permission.

FZ Office will use two paths:

1. **Bundled fonts:** a curated, licence-verified set of Unicode Devanagari fonts that can legally ship with the app. Include each font's upstream copyright and licence text.
2. **Custom font library:** let users import any TTF/OTF font they are legally entitled to use. Keep it private to the device; do not upload it.

This avoids a fixed list being the only available fonts.

## Native font discovery

The pinned Collabora Android source has android/lib/fonts.conf. It currently scans /system/fonts and stores its Fontconfig cache in the app's private data directory. It does not expose a custom-font folder in the app UI; an upstream request for scoped-storage font-folder support remains open.

FZ's native patch adds:

/data/data/@@APPLICATION_ID@@/user/fonts

to the Fontconfig search directories. The build substitutes the real application id. The directory is inside the app sandbox.

The Fontconfig path patch is stored at patches/collabora/0001-android-fontconfig-hindi-font-directory.patch. The native build also applies patches 0002 and 0003 to bundle the generated OFL font pack and add the native Font Center screen.

The native Hindi Font Center:
- Import ZIP font packs through Android Storage Access Framework; archives may contain nested folders.
- Extract only TTF/OTF files. ZIP path names are discarded, preventing path traversal.
- Enforce 180 MB total expanded size, 12 MB per font, 20,000 archive entries and 1,200 font entries.
- Validate TrueType/OpenType signatures and Android Typeface readability before copying.
- Detect exact duplicate files by SHA-256 and avoid overwriting existing names.
- Store imported files only in the app-private user/fonts directory read by Fontconfig.
- Clear the app-private Fontconfig cache after import; close and reopen documents so the engine rescans.
- Keep the imported ZIP and font binaries on the device; they are not committed to the public repository.

TTC/OTC collections are a later item; initial import accepts individual TTF/OTF files only.

## Bundled Devanagari collection

Start with the existing 12-family Unicode collection from the v0.3 Font Center:
- Noto Sans Devanagari
- Noto Serif Devanagari
- Mukta
- Hind
- Tiro Devanagari Hindi
- Kalam
- Sahitya
- Martel
- Baloo 2
- Khand
- Gotu
- Glegoo

Expand from the upstream Google Fonts Devanagari catalogue after checking each family, style, file size, glyph coverage and OFL licence. Do not claim that every font covers every Hindi/Marathi/Sanskrit code point. Noto Sans and Noto Serif Devanagari remain general-purpose defaults.

## Legacy Hindi fonts

The target behaviour matches desktop Word: install/register the exact declared font and render legacy character codes with that font. Do not Unicode-convert just to display a document. Unicode conversion is a separate, user-invoked operation.


Profiles to research and test include:
- DevLys, each exact version including 010
- Kruti Dev, each exact version
- Chanakya
- Shusha
- Shree Lipi
- APS-DV / APS family
- Akruti and other vendor-specific DTP encodings

The default and first choice is to render the original document using its exact legacy font when the user imports a font they are licensed to use. This preserves stored characters and avoids conversion. Automatic conversion is disabled in the normal open/view path.

If the required legacy font is unavailable, a converter may be offered only when the document's exact encoding profile is known and a verified test corpus exists. Never infer an encoding from random-looking Latin text alone. Never apply one family's map to another family because the names look similar.

Each profile needs:
- Exact font family/version identifiers and aliases.
- Source and licence for the mapping implementation.
- Golden input/output pairs including punctuation and smart quotes.
- Matra/reph/rakar/conjunct tests.
- Mixed English, names, abbreviations, dates, reference numbers and symbols.
- Real DOCX files with run-level and style-inherited font declarations.
- Round-trip checks proving Unicode content is not modified a second time.

## Shaping and QA corpus

Run docs/DEVANAGARI_TEST_CORPUS.md through the native engine, not just WebView. Include:
- कि, क्रि, प्रि, र्क, र्क्ष, क्ष, त्र, ज्ञ, श्र, स्त्र
- क़, ख़, ग़, ज़, ड़, ढ़, फ़
- अँ, अं, अः, ।, ॥, Devanagari digits and ₹
- ZWJ and ZWNJ sequences
- Mixed Hindi/English paragraphs, tables, headers/footers, footnotes and text boxes
- Embedded fonts and missing-font substitution

A font is not marked verified merely because it can be imported. It must render correctly in LibreOffice on a physical Android device and survive save/reopen/export tests.

## Font-family matching rule

DOCX stores family names in styles and runs. The font manager must expose matching family names to Fontconfig. If a declared font is missing, the engine substitutes another font and page layout may change. Importing a file with a different internal family name does not automatically satisfy a document's declared font. Family inspection and aliasing are part of the integration.


## Native Font Center implementation

The native Collabora client gets a Hindi Font Center entry in its document-browser menu. It lists font files from user/fonts, previews a Devanagari sample using Android Typeface, imports validated TTF/OTF files, and removes user-imported files. On import/removal, the app-private Fontconfig cache is cleared; the user should close and reopen documents so the native engine rescans the directory.

The files are installed under the app data directory, outside Android's shared public storage. Imported files are never uploaded. The same user/fonts path is used by the transitional Font Center and the native Collabora engine.


## Default read-only opening

The native FZ Office browser launches documents using ACTION_VIEW with read permission only and sets the private com.fz.office.READ_ONLY intent extra. The patched LibreOffice Android activity uses that extra to keep isDocEditable false and avoids attempting to resolve a writable source file. This protects the original document during normal reading. The engine's editing capability is not removed from the upstream project; a future explicit edit action can be added after reader-mode acceptance tests.


For the user-supplied ZIP inventory and binary redistribution decision, see docs/USER_FONT_ARCHIVE_AUDIT.md.
