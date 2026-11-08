# Bundled Hindi font catalogue

The app packages a curated starter library of 12 Unicode Devanagari families sourced from the Google Fonts repository at commit:

86aced91de05cd4f96a06838124cca45be337b2b

The Gradle task downloads only the declared files, verifies the upstream Git blob SHA-1 and expected byte count, and copies the matching OFL license text into the APK assets.

| Family | Included style(s) | Intended use |
|---|---|---|
| Noto Sans Devanagari | Variable weight/width | General-purpose text; default |
| Noto Serif Devanagari | Variable weight/width | Formal and long-form reading |
| Mukta | Regular, Bold | General-purpose sans-serif |
| Hind | Regular, Bold | Compact text |
| Tiro Devanagari Hindi | Regular, Italic | Editorial/book-style Hindi |
| Kalam | Regular | Handwritten notes |
| Sahitya | Regular, Bold | Literary/book-style text |
| Martel | Regular | Serif text |
| Baloo 2 | Variable weight | Rounded display titles |
| Khand | Regular | Condensed display headings |
| Gotu | Regular | Rounded text style |
| Glegoo | Regular | Serif/display style |

Every family is distributed under its own SIL Open Font License 1.1 text, included in the APK under writer/fonts/licenses/. Keep these license files with the corresponding font files in any redistribution.

The catalogue is intentionally selective. It does not contain commercial fonts, proprietary legacy-encoding fonts, or every Devanagari family available on the internet. The in-app Import TTF/OTF action is the route for personal/local fonts.

## Upstream directories

- Noto Sans Devanagari: google/fonts/ofl/notosansdevanagari
- Noto Serif Devanagari: google/fonts/ofl/notoserifdevanagari
- Mukta: google/fonts/ofl/mukta
- Hind: google/fonts/ofl/hind
- Tiro Devanagari Hindi: google/fonts/ofl/tirodevanagarihindi
- Kalam: google/fonts/ofl/kalam
- Sahitya: google/fonts/ofl/sahitya
- Martel: google/fonts/ofl/martel
- Baloo 2: google/fonts/ofl/baloo2
- Khand: google/fonts/ofl/khand
- Gotu: google/fonts/ofl/gotu
- Glegoo: google/fonts/ofl/glegoo
