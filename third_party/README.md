# Third-party assets

- The PDF reader has no third-party PDF engine or native PDF binaries. It uses Android's platform `android.graphics.pdf.PdfRenderer` through FZ's Kotlin document core.
- Spreadsheet and presentation preview assets are pinned to immutable upstream revisions and checksum-verified by the Gradle build.
- Hindi font files are also pinned and checksum-verified during the build. Their upstream licenses are retained with the generated assets.
