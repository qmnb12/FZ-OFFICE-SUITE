# Gander-derived spreadsheet and presentation viewers

FZ Office v0.6.2 adds offline, read-only spreadsheet and presentation preview routes to the transitional Java/WebView app.

## Source pin and licenses

- Gander source: https://github.com/mokshablr/gander
- Pinned upstream commit: 73cf325435868cab1683d8de8cc1b89533e767b3
- Hindi-run helper: the FZ Gander fork file, pinned at 01afbbba5454860ae121b07d9b1cc3bdf184162e
- Source assets are downloaded at build time from immutable Git commits and verified using Git blob SHA-1 plus exact byte counts.
- The upstream MIT license and Gander's third-party library notices are bundled into the APK at `viewer/licenses/`. The existing Devanagari font licenses remain under `writer/fonts/licenses/`.

The renderer assets are bundled in the APK. They do not fetch JavaScript, fonts or documents from a hosted service at runtime. The document is exposed only to the local WebView origin through a read-only ContentResolver stream. The viewer's content security policy is applied to local assets, file/content URL access is disabled, network loads are blocked, and external navigation is refused.

## Routes

- `.xlsx`, `.xls`, `.xlsm`, `.xlsb`, `.xltx`, `.csv`, `.ods` → SheetJS/Gander spreadsheet preview.
- `.pptx`, `.ppsx`, `.pptm`, `.potx` → PPTXjs/Gander presentation preview.
- `.docx` and `.txt` → existing Hindi-aware FZ Writer reader.

## Hindi font integration

Both Office viewer pages load the Hindi-run helper and a generated `hindi-fonts.css`. The CSS is served by the local asset bridge from the same font directory used by the Hindi Font Center, and the selected font is supplied through `--fz-hindi-default`. Bundled font URLs and the selected imported custom font are resolved locally.

## Current limitations

- Excel and PowerPoint routes are read-only previews. They do not edit or save workbooks/presentations.
- The renderers do not promise complete Microsoft Office fidelity for every chart, animation, embedded object, formula or rare layout feature.
- Legacy binary PowerPoint `.ppt` is not routed to this renderer; convert it to `.pptx`.
- The DOCX parser remains transitional and does not guarantee Microsoft Word-identical pagination.
- The application is intentionally view-only; no native office engine, Edit action or save-back workflow is included.
- The lightweight Android build downloads pinned viewer/font source assets during build; the installed app itself reads documents offline.
