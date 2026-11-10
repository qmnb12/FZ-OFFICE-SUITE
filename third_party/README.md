# Third-party source pins

- MJ PDF Reader 3.1 viewer and PDFium binding modules are fetched from the pinned upstream revision `286277c6a85a4068cbbfbd18c93a742f0b2087ed`.
- Only `AndroidPdfViewer` and `PdfiumAndroid` are integrated. The MJ PDF application module is not copied into FZ Office.
- Both integrated modules are Apache License 2.0; the bundled PDFium native source retains its BSD license notice. Their upstream license files are kept with the downloaded source.
- MJ PDF's root application is GPLv3 and is not included in this integration.
- Native PDFium binaries are generated locally by the upstream build script. FZ Office uses the upstream's explicitly documented prebuilt PDFium comparison mode to avoid the full Chromium/PDFium source build. That mode does not include the upstream local Arabic-text patches.
- The Gradle settings bootstrap downloads only the required pinned source subfolders from GitLab and patches module paths/repository settings for this multi-module build. Do not remove upstream copyright, license notices or third-party license files.
- The PDF screen continues to use FZ Office's shared `ReaderDashboard`, document picker, URI permissions, bookmarks and sharing. Page rendering, zoom and scroll gesture handling are provided by MJ PDF's `PDFView`.
