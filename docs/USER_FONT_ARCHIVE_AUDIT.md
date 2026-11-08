# Supplied Hindi font archive audit

The user supplied three ZIP archives for local font import. This audit records their contents and metadata only. **The font binaries are not included in the public source repository** because the archives contain no redistribution licence files and their embedded metadata shows third-party copyright and/or embedding restrictions.

## Archive inventory

| Archive | Font files | Observed metadata |
|---|---:|---|
| Devlys-fonts.zip | 77 TTF | All 77 have OS/2 fsType 0x0001; internal copyright/name metadata includes “More Services”. No licence/README/notice file found. |
| Kruti_Dev_Fonts.zip | 407 TTF | 405 have OS/2 fsType 0x0001, 2 have 0x0002; internal copyright/name metadata includes “More Services”. No licence/README/notice file found. |
| mangalfonts.zip | 5 TTF | 4 contain Microsoft copyright and EULA text; one contains National Centre for Software Technology copyright metadata. No redistribution licence file found in the ZIP. |

A font's being available for free download, or being free for personal use, does not automatically grant permission to publish its binary in a public repository or bundle it into a redistributed APK. Mangal metadata explicitly references Microsoft EULA terms. Microsoft states that fonts supplied with Microsoft products generally may not be copied or redistributed without the relevant permission.

## What the app does with these files

The native FZ Office Hindi Font Center imports ZIP files selected by the user into the app-private user/fonts directory. It:
- extracts only TTF/OTF files;
- discards archive paths, preventing ZIP path traversal;
- enforces archive, entry-count and per-font size limits;
- validates font signatures and Android Typeface readability;
- de-duplicates by SHA-256;
- refreshes the app-private Fontconfig cache.

This lets a user install fonts they are legally entitled to use without publishing the supplied font binaries. The fonts stay on the user's device and are not uploaded.

## Public font bundle

The project may bundle Devanagari fonts only when their upstream licence explicitly permits redistribution. The existing checksum-pinned Google Fonts task is the source of the bundled Unicode font pack; each generated font is accompanied by its licence text. Do not move DevLys, Kruti Dev or Mangal binaries into that bundle unless a redistribution licence from the applicable rights holder is supplied and reviewed.

## Metadata flag note

OpenType OS/2 fsType is an embedding-permission field, not a complete standalone font licence. The observed flag is recorded as evidence for review; the absence of a licence file remains the reason that these archives are not published as binary assets.
