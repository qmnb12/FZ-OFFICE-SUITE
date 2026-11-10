# FZ Office PDF viewer design

## Implementation choices

FZ Office keeps Android's platform `PdfRenderer` as its rendering engine. The reader now uses a vertical `RecyclerView` for continuous pages instead of replacing one page in a single `ImageView`.

Rendering is deliberately demand-driven:
- The adapter binds the visible and nearby pages; it does not render every page in the document. The opening pass measures only the page that will be shown first; other page dimensions are resolved lazily as RecyclerView binds those pages, so a long PDF does not need a full metadata scan before its first page appears.
- A single background executor serializes page access, page-size requests and rendering. A separate search worker prevents a full-document Find scan from sitting ahead of visible-page rendering.
- Page bitmaps are stored in an LRU cache with a memory limit.
- Zoom uses quality buckets. The existing lower-resolution bitmap stays visible while a sharper bitmap is generated.
- Thumbnail bitmaps are small and are generated only when the thumbnail grid is opened.
- Page dimensions are learned when a page is rendered, and the item is resized to the page's actual aspect ratio.

The reader also includes direct page navigation, a thumbnail grid, per-document local bookmarks, sharing, fit-width reset and pinch/button zoom. The original SAF document is opened read-only; a private cache copy is used only when the provider cannot supply a seekable descriptor.

## Open-source research references

The implementation was reviewed against these public projects. Their architecture and user-facing behavior were used as references; FZ Office's PDF activity was written for its existing Android Views app and does not embed their source files.

- [danjdt/android-pdfviewer](https://github.com/danjdt/android-pdfviewer) — MIT licensed. Its README describes PdfRenderer + RecyclerView, background rendering, page quality settings and zoom.
- [codegeasse1/docreader](https://github.com/codegeasse1/docreader) — MIT licensed. Its ReaderScreen demonstrates continuous pages, focal-point zoom, thumbnail navigation, bookmarks and quality-bucket rendering.
- [Android PdfRenderer API](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) — platform renderer used by FZ Office.

## Deliberate exclusions for the fast reader

Text search, annotations, form editing and encrypted-PDF password entry are not part of this rendering pass. They need a separate text/annotation or document-processing layer and should not be simulated with expensive full-page raster scans. The current reader stays lightweight and read-only.
