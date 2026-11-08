# Supplied Hindi font archives

The following font ZIP archives were uploaded by the project owner and inspected locally. The individual font binaries are intentionally **not committed** to the public GitHub repository or downloaded into the standard APK. The ZIP files did not contain redistribution licence text, so they remain user-supplied content and are imported only into app-private storage.

## Archive inventory

| Archive | Valid TTF files | Internal families | Notes |
|---|---:|---:|---|
| Kruti_Dev_Fonts.zip | 407 | 322 | Multiple Kruti Dev families, versions and variants |
| Devlys-fonts.zip | 77 | 69 | Includes exact DevLys 010 family and related styles |
| mangalfonts.zip | 5 | 1 | Five Mangal versions/styles |
| **Total** | **489** | — | All scanned entries passed TTF signature and OpenType metadata checks |

## Exact DevLys 010 family files

The DevLys archive contains these exact variants (internal OpenType family metadata identifies them as DevLys 010, with some files storing a trailing space in the family name):

| File | Internal style |
|---|---|
| Devlys-010.ttf | Normal |
| DevLys-010-Bold.ttf | Bold |
| DevLys-010-Bold-Italic.ttf | Bold Italic |
| DevLys-010-Condensed.ttf | Condensed |
| DevLys-010-Thin.ttf | Thin |

The transitional Font Center reads internal OpenType name-table family/style metadata rather than trusting the ZIP filename. Family matching normalizes case, spaces and punctuation so the DevLys 010 variants resolve to the document family.

## Mangal files

- mangal16jul2016.ttf
- mangal11jun2009.ttf
- mangal14april2007.ttf
- mangal30nov2005.ttf
- mangalb.ttf (bold)

## Import procedure

1. Open FZ Office → Hindi Font Center.
2. Choose **Import ZIP font pack**.
3. Select one archive from the device storage.
4. Wait for its font count summary. Already-imported files are deduplicated by SHA-256.
5. Search for DevLys 010, Kruti Dev or Mangal to confirm the internal families were indexed.
6. Close and reopen the document after importing fonts so the native document engine can rescan its Fontconfig directory.

The importer:
- accepts TTF/OTF entries only;
- ignores directories and unrelated files;
- rejects invalid font signatures and files that Android cannot load;
- caps each font at 12 MB, the expanded archive at 180 MB and the number of font entries at 1,200;
- discards ZIP folder paths and stores safe filenames only;
- keeps files in the app-private user/fonts directory;
- never uploads or copies imported legacy font binaries to the GitHub repository.

## Licensing and privacy

The three uploaded archives did not contain licence files. The project therefore treats them as locally supplied fonts. Their presence in a local app does not grant the right to redistribute them, bundle them in a public APK or share them with other users. The app does not upload these files.

## Rendering rule

For a DOCX document, the correct first step is to load its exact declared font family (for example, DevLys 010) and leave the original stored text unchanged. This avoids unnecessary or lossy conversion of English abbreviations, dates, punctuation, matras and legacy glyph codes. Unicode conversion remains a separate operation and must use an exact, tested encoding profile.
