# FZ Office PDF viewer design

## Primary PDF reader

PDF documents open in the pinned Gander PDF.js viewer, bundled completely offline in the APK. The matching PDF.js module and worker, Gander page styles/scripts, PDF.js image decoders and all 168 Adobe CMap tables are checksum-verified from the same Gander revision already used by the spreadsheet and presentation viewers.

The original SAF PDF is exposed to the local WebView origin at `/doc/file.pdf` as a read-only stream. Small files use one sequential response; larger files advertise byte ranges so PDF.js can fetch only the pieces it needs. The app never opens an output stream to the source document and does not rewrite, convert, or re-save its bytes.

The page renderer is demand-driven:
- Pages near the viewport are queued and rendered; work outside the keep band is cancelled or released.
- PDF.js can draw a sharper tile at the zoom the reader is actually using, rather than stretching a permanently low-resolution Android bitmap.
- A bounded number of renders run concurrently; off-screen canvas and text-layer data are released.
- PDF text selection, Find, password prompts, continuous scrolling and PDF page tracking are supplied by the pinned Gander page.
- Pinch and double-tap zoom are handled by the viewer/WebView. The shared FZ Tools menu provides zoom, fit, page navigation, Find, print and share.

The Android bridge uses a one-way-origin-checked `WebMessagePort` channel. It carries only fixed commands and status strings; no JavaScript interface exposes Android objects to the document. WebView file/content access is disabled, runtime network loads are blocked, and the viewer's CSP restricts scripts, workers and resources to the local asset origin.

## Compatibility fallback

On a WebView older than the PDF.js bundle's supported Chromium floor, FZ opens the native `PdfRenderer` reader instead. That fallback remains demand-driven, uses bounded bitmap caching and does not modify the source PDF. The modern PDF.js path is the default on supported WebViews.

## Vendored assets and limitations

The PDF.js bundle is pinned to Gander revision `73cf325435868cab1683d8de8cc1b89533e767b3`. Its upstream license and bundled third-party notices are shipped under `viewer/licences.md`; the PDF image-decoder notices are shipped beside their WASM files. The 168 Adobe CMap files are included (about 1.17 MB uncompressed) and loaded on demand by PDF.js; their BSD-3-Clause attribution is included in the upstream notices.

ICC color profiles still use the pinned PDF.js configuration, which does not enable the optional QCMS worker fetch. The original PDF bytes are always preserved, including for encrypted files that require a password prompt.
