import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
}

val hindiFontAssets = layout.buildDirectory.dir("generated/assets/hindiFonts")
val ganderViewerAssets = layout.buildDirectory.dir("generated/assets/ganderViewer")

val testingKeystorePath = System.getenv("FZ_TESTING_KEYSTORE")
val testingKeyAlias = System.getenv("FZ_TESTING_KEY_ALIAS")
val testingStorePassword = System.getenv("FZ_TESTING_STORE_PASSWORD")
val testingKeyPassword = System.getenv("FZ_TESTING_KEY_PASSWORD")
val hasTestingSigning = listOf(
    testingKeystorePath,
    testingKeyAlias,
    testingStorePassword,
    testingKeyPassword
).all { !it.isNullOrBlank() }




data class OfflineVendorAsset(
    val kind: String,
    val path: String,
    val size: Long,
    val sha: String,
    val bytes: ByteArray
)

fun gitBlobSha(bytes: ByteArray): String {
    val header = ("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(header)
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun readOfflineVendorAssets(manifests: List<java.io.File>): List<OfflineVendorAsset> {
    val assets = ArrayList<OfflineVendorAsset>()
    manifests.forEach { manifest ->
        if (!manifest.isFile) {
            throw GradleException("Required offline vendor bundle is missing: " + manifest.path)
        }
        var pendingRow: StringBuilder? = null

        fun addManifestRow(row: String) {
            val fields = row.split('|', limit = 5)
            if (fields.size != 5) {
                throw GradleException("Invalid offline vendor manifest row in " + manifest.name)
            }
            val size = fields[2].toLongOrNull()
                ?: throw GradleException("Invalid asset size in " + manifest.name)
            val bytes = try {
                Base64.getDecoder().decode(fields[4])
            } catch (exception: IllegalArgumentException) {
                throw GradleException("Invalid Base64 payload for " + fields[1], exception)
            }
            if (bytes.size.toLong() != size || gitBlobSha(bytes) != fields[3]) {
                throw GradleException("Offline asset checksum mismatch: " + fields[1])
            }
            assets.add(OfflineVendorAsset(fields[0], fields[1], size, fields[3], bytes))
        }

        manifest.forEachLine(Charsets.UTF_8) { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine

            if (line.contains('|')) {
                pendingRow?.let { addManifestRow(it.toString()) }
                pendingRow = StringBuilder(line)
            } else {
                val row = pendingRow
                    ?: throw GradleException("Invalid offline vendor manifest continuation in " + manifest.name)
                row.append(line)
            }
        }
        pendingRow?.let { addManifestRow(it.toString()) }
    }
    return assets
}

val offlineViewerManifests = listOf(
    layout.projectDirectory.file("vendor/offline-viewer-1.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-viewer-2.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-viewer-1.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-viewer-2.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-viewer-3.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-01.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-02.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-03.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-04.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-05.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-06.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-07.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-08.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-09.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-10.manifest").asFile,
    layout.projectDirectory.file("vendor/offline-pdf-cmaps-11.manifest").asFile
)
val fontLicenseManifest = layout.projectDirectory.file("vendor/font-licenses.manifest").asFile
val hindiFontPayloads = listOf(
    Triple("NotoSansDevanagari-VF.ttf", 641944L, "163f04bddc5a96fc523e4080466cac5c588f6594"),
    Triple("NotoSerifDevanagari-VF.ttf", 757692L, "3d90da279fe70b5017d9b51f58772f0c9ea4146e"),
    Triple("Mukta-Regular.ttf", 432248L, "ccb22cfb2cbc43cd516d6376492ad6dde3a94c2f"),
    Triple("Mukta-Bold.ttf", 418416L, "d7256f58bd2f8344c494e0df3be08dcd00d348c8"),
    Triple("Hind-Regular.ttf", 299532L, "d354dbbcd295b1b7e09b015f0bd490864ad47aca"),
    Triple("Hind-Bold.ttf", 286604L, "4469b8415f1dcbced9dfc3f2bb24e2f2f2618c5d"),
    Triple("TiroDevanagariHindi-Regular.ttf", 423224L, "9a55b8c823496ca170aad4c4fced67891541fff0"),
    Triple("TiroDevanagariHindi-Italic.ttf", 425636L, "b0a5bd9916bf6d912ce75bee30c24d9db42e5c41"),
    Triple("Kalam-Regular.ttf", 427360L, "0c956a29348e69bff24a1ae8952fd0b42569311c"),
    Triple("Sahitya-Regular.ttf", 261652L, "be1a414eff64e0ba15e8eb5852f35a66da2923c9"),
    Triple("Sahitya-Bold.ttf", 267140L, "c9f249fcc9f4664f439c159ac1ac720a1624c416"),
    Triple("Martel-Regular.ttf", 170196L, "7bd3ef19172c35f443476ce5657e1247c614ca66"),
    Triple("Baloo2-VF.ttf", 683200L, "bc1b9f1191c0d6d23cb2ce0af66aba82ddbf8d6c"),
    Triple("Khand-Regular.ttf", 316808L, "0d8e9536f19b4a9842d95b699c33b7ac40f433cb"),
    Triple("Gotu-Regular.ttf", 697792L, "8182906d0266999a9df466a5370a2a9a06935fea"),
    Triple("Glegoo-Regular.ttf", 271368L, "388d1fe3c0985cabbf04eb5977fc6e6d0c553345")
)

val prepareHindiFonts = tasks.register("prepareHindiFonts") {
    inputs.files(hindiFontPayloads.map {
        layout.projectDirectory.file("vendor/fonts/" + it.first + ".b64").asFile
    })
    inputs.file(fontLicenseManifest)
    outputs.dir(hindiFontAssets)
    doLast {
        val root = hindiFontAssets.get().asFile
        val fontDirectory = root.resolve("writer/fonts")
        val licenseDirectory = fontDirectory.resolve("licenses")
        fontDirectory.mkdirs()
        licenseDirectory.mkdirs()

        hindiFontPayloads.forEach { item ->
            val output = item.first
            val expectedSize = item.second
            val expectedSha = item.third
            val payload = layout.projectDirectory.file("vendor/fonts/" + output + ".b64").asFile
            if (!payload.isFile) {
                throw GradleException("Bundled Hindi font payload is missing: " + payload.name)
            }
            val bytes = try {
                Base64.getDecoder().decode(
                    payload.readText(Charsets.UTF_8).filterNot { it.isWhitespace() }
                )
            } catch (exception: IllegalArgumentException) {
                throw GradleException("Invalid Base64 Hindi font payload: " + payload.name, exception)
            }
            if (bytes.size.toLong() != expectedSize || gitBlobSha(bytes) != expectedSha) {
                throw GradleException("Bundled Hindi font checksum mismatch: " + output)
            }
            fontDirectory.resolve(output).writeBytes(bytes)
        }

        readOfflineVendorAssets(listOf(fontLicenseManifest)).filter { it.kind == "LICENSE" }.forEach { asset ->
            val destination = root.resolve(asset.path)
            destination.parentFile.mkdirs()
            destination.writeBytes(asset.bytes)
        }
    }
}

val prepareGanderViewerAssets = tasks.register("prepareGanderViewerAssets") {
    inputs.files(offlineViewerManifests)
    outputs.dir(ganderViewerAssets)
    doLast {
        val root = ganderViewerAssets.get().asFile
        root.mkdirs()

        readOfflineVendorAssets(offlineViewerManifests).filter { it.kind == "VIEWER" }.forEach { asset ->
            var outputBytes = asset.bytes
            if (asset.path == "viewer/pdf.mjs") {
                var script = String(outputBytes, Charsets.UTF_8)
                if (!script.contains("""opts.cMapUrl = vwAssetUrl("./lib/cmaps/");""") ||
                    !script.contains("opts.cMapPacked = true;") ||
                    !script.contains("""opts.wasmUrl = vwAssetUrl("./lib/wasm/");""")
                ) {
                    throw GradleException("Unexpected pinned Gander PDF.js asset configuration.")
                }

                // Measure the true fitted visual viewport after the PDF opens. Android
                // WebView overview mode does not always report innerWidth as the exact floor.
                val oldAtFloor = """function atFloor() {
  var vv = window.visualViewport;
  return !vv || !(vv.width > 0) || vv.width >= window.innerWidth - 0.5;
}"""
                val newAtFloor = """
                    var fitViewportWidth = 0;
                    var fitLayoutWidth = window.innerWidth;

                    function rememberFitViewportWidth() {
                      var vv = window.visualViewport;
                      if (vv && vv.width > 0) fitViewportWidth = vv.width;
                      fitLayoutWidth = window.innerWidth;
                    }

                    window.addEventListener("resize", function () {
                      if (window.innerWidth > 0 && window.innerWidth !== fitLayoutWidth) {
                        rememberFitViewportWidth();
                      }
                    });

                    function atFloor() {
                      var vv = window.visualViewport;
                      if (!vv || !(vv.width > 0)) return true;
                      var fitWidth = fitViewportWidth > 0 ? fitViewportWidth : window.innerWidth;
                      var tolerance = Math.max(8, fitWidth * 0.03);
                      return vv.width >= fitWidth - tolerance;
                    }
                """.trimIndent()
                if (!script.contains(oldAtFloor)) {
                    throw GradleException("Unexpected pinned Gander PDF pinch floor function.")
                }
                script = script.replace(oldAtFloor, newAtFloor)

                val oldFitMeasure = "      doc = opened;"
                val newFitMeasure = "      doc = opened;\n      rememberFitViewportWidth();"
                if (!script.contains(oldFitMeasure)) {
                    throw GradleException("Unexpected pinned Gander PDF document-open block.")
                }
                script = script.replace(oldFitMeasure, newFitMeasure)

                // Once PDF.js has shrunk the column, it owns every pinch until the column
                // returns to fit width. The WebView is not allowed to take that gesture.
                val oldPinchGate = "if (!atFloor() || (shrink >= 1 && d >= g.apart)) {"
                val newPinchGate = "if (shrink >= 1 && (!atFloor() || d >= g.apart)) {"
                if (!script.contains(oldPinchGate)) {
                    throw GradleException("Unexpected pinned Gander PDF pinch ownership check.")
                }
                script = script.replace(oldPinchGate, newPinchGate)

                // Keep the page readable at the smallest zoom: after fit, a page may
                // shrink by at most 30% of the fitted width. The pinned viewer starts
                // at a quarter, which turns pages into unreadable thumbnails on a phone.
                val oldShrinkFloor = "var SHRINK_LEAST = 0.25;"
                val newShrinkFloor = "var SHRINK_LEAST = 0.7;"
                if (!script.contains(oldShrinkFloor)) {
                    throw GradleException("Unexpected pinned Gander PDF shrink floor value.")
                }
                script = script.replace(oldShrinkFloor, newShrinkFloor)

                // The app owns double tap. Growing the column back to the layout width
                // from the page would raise the WebView's pinch floor above 70%.
                val oldPageDoubleTap = """    lastTap = null;
    zoomTo(1, tap.y, DOUBLE_TAP_MS);"""
                val newPageDoubleTap = """    lastTap = null;"""
                if (!script.contains(oldPageDoubleTap)) {
                    throw GradleException("Unexpected pinned Gander PDF double-tap block.")
                }
                script = script.replace(oldPageDoubleTap, newPageDoubleTap)

                // Capture operator bounds during the already-required full-page draw.
                // PDF.js can then skip display-list operations that cannot intersect a
                // high-resolution zoom tile, instead of replaying the entire page.
                val oldMaxTiles = "var MAX_TILES = 2;"
                val newMaxTiles = "var MAX_TILES = 1;"
                if (!script.contains(oldMaxTiles)) {
                    throw GradleException("Unexpected pinned Gander PDF tile concurrency limit.")
                }
                script = script.replace(oldMaxTiles, newMaxTiles)

                // Keep ordinary page and tile draws free of per-operator bounding-box
                // tracking. PDF.js executes the tracker on every operator and pays that
                // cost on every visible page, while this reader only needs sharper tiles
                // occasionally during zoom.
                val baseRenderOptions = """      recordImages: mode
    });
    return slot.task.promise.then(function () {"""
                if (!script.contains(baseRenderOptions)) {
                    throw GradleException("Unexpected pinned Gander PDF base-page render options.")
                }

                val tileRenderOptions = """    recordImages: mode,
    viewport: slot.unscaled.clone({ scale: cssScale * req.k }),"""
                if (!script.contains(tileRenderOptions)) {
                    throw GradleException("Unexpected pinned Gander PDF tile render options.")
                }

                // Keep one finished high-resolution tile on the visible page while
                // zoomed back out. The tile is still the same PDF content and can be
                // reused when the reader pinches back to the same area and scale.
                val oldLowZoom = """  if (shrink < 1 || !(k > gateScale() * TILE_GATE)) {
    /* Back at a zoom the page bitmaps cover on their own. A walk of every slot rather
       than a register of which ones are tiled: it is a property read per page, against
       the layout read per page that this function exists to avoid. */
    for (i = 0; i < slots.length; i++) dropTile(slots[i]);
    return;
  }"""
                val newLowZoom = """  if (shrink < 1 || !(k > gateScale() * TILE_GATE)) {
    /*
     * Keep one completed sharp tile on the currently visible page while zooming out.
     * It remains correct at the smaller scale and avoids another display-list render
     * when the reader zooms back into the same page area.
     */
    var keptTile = false;
    for (i = 0; i < slots.length; i++) {
      var cached = slots[i];
      if (!keptTile && cached.state === "drawn" &&
          cached.tile && onScreen(cached)) {
        shed(cached.pending);
        cached.pending = null;
        keptTile = true;
      } else {
        dropTile(cached);
      }
    }
    return;
  }"""
                if (!script.contains(oldLowZoom)) {
                    throw GradleException("Unexpected pinned Gander PDF low-zoom tile cache branch.")
                }
                script = script.replace(oldLowZoom, newLowZoom)

                // Tiles must sharpen a page at any zoom now that the column stays at the
                // 70% floor and the WebView owns the whole pinch. Skipping them whenever
                // the column is shrunk would leave every zoom above the floor unsharpened.
                val oldTileGuard = "  if (shrink < 1 || !(k > gateScale() * TILE_GATE)) {"
                val newTileGuard = "  if (!(k > gateScale() * TILE_GATE)) {"
                if (!script.contains(oldTileGuard)) {
                    throw GradleException("Unexpected pinned Gander PDF tile zoom guard.")
                }
                script = script.replace(oldTileGuard, newTileGuard)

                // A bitmap rendered at a larger page width remains sharp when the
                // page is reduced. Do not rerasterize it on pinch release; only redraw
                // after the page grows enough to expose a real resolution shortfall.
                val oldBitmapPolicy = """function sizedForNow(slot) {
  var r = slot.shrink / shrink;
  return r < TILE_GATE && r > 1 / TILE_GATE;
}"""
                val newBitmapPolicy = """function sizedForNow(slot) {
  var r = slot.shrink / shrink;
  return r > 1 / TILE_GATE;
}"""
                if (!script.contains(oldBitmapPolicy)) {
                    throw GradleException("Unexpected pinned Gander PDF bitmap-size policy.")
                }
                script = script.replace(oldBitmapPolicy, newBitmapPolicy)

                // Do not start expensive high-resolution tile work on the first frame
                // after a gesture. Wait for a quiet period and start from an idle slot;
                // PDF.js then yields its operator-list execution between animation frames.
                val oldTileDelay = "var TILE_SETTLE_MS = 150;"
                val newTileDelay = "var TILE_SETTLE_MS = 150;"
                if (!script.contains(oldTileDelay)) {
                    throw GradleException("Unexpected pinned Gander PDF tile settle delay.")
                }
                script = script.replace(oldTileDelay, newTileDelay)

                val oldScheduleRefine = """var tileTimer = 0;
function scheduleRefine() {
  if (tileTimer) clearTimeout(tileTimer);
  tileTimer = setTimeout(function () { tileTimer = 0; refine(); }, TILE_SETTLE_MS);
}"""
                val newScheduleRefine = """var tileTimer = 0;
var tileIdle = 0;
function scheduleRefine() {
  if (tileTimer) {
    clearTimeout(tileTimer);
    tileTimer = 0;
  }
  if (tileIdle && typeof window.cancelIdleCallback === "function") {
    window.cancelIdleCallback(tileIdle);
    tileIdle = 0;
  }
  tileTimer = setTimeout(function () {
    tileTimer = 0;
    if (typeof window.requestIdleCallback === "function") {
      tileIdle = window.requestIdleCallback(function () {
        tileIdle = 0;
        refine();
      }, { timeout: 400 });
    } else {
      tileTimer = setTimeout(function () {
        tileTimer = 0;
        refine();
      }, 250);
    }
  }, TILE_SETTLE_MS);
}"""
                if (!script.contains(oldScheduleRefine)) {
                    throw GradleException("Unexpected pinned Gander PDF tile scheduling function.")
                }
                script = script.replace(oldScheduleRefine, newScheduleRefine)

                // A two-finger touch no longer holds page drawing. The native WebView
                // zoom does not run endPinch(), so the old hold left pump() frozen and
                // every newly scrolled page white until the app was reopened.
                val oldStartPinch = """function startPinch(e) {
  finishAnimation();"""
                val newStartPinch = """function startPinch(e) {
  if (pinch) endPinch();
  finishAnimation();"""
                if (!script.contains(oldStartPinch)) {
                    throw GradleException("Unexpected pinned Gander PDF pinch-start handler.")
                }
                script = script.replace(oldStartPinch, newStartPinch)

                // The WebView owns pinch at every scale now. Keep the page's pinch
                // bookkeeping from taking a gesture the compositor is already zooming.
                val oldPinchTakeover = """  pinch = {
    apart: apart(e), from: shrink, to: shrink, taken: false,
    at: 0, y0: between(e), y: between(e), sy: window.scrollY
  };"""
                val newPinchTakeover = """  pinch = null;"""
                if (!script.contains(oldPinchTakeover)) {
                    throw GradleException("Unexpected pinned Gander PDF pinch takeover body.")
                }
                script = script.replace(oldPinchTakeover, newPinchTakeover)

                val oldTouchStart = """document.addEventListener("touchstart", function (e) {
  if (!doc || !slots.length) return;
  if (e.touches.length === 2) {
    tap = lastTap = null;
    startPinch(e);
  } else if (e.touches.length === 1 && !pinch && shrink < 1 && pages.contains(e.target)) {"""
                val newTouchStart = """document.addEventListener("touchstart", function (e) {
  if (!doc || !slots.length) return;
  if (e.touches.length >= 2) {
    tap = lastTap = null;
    if (e.touches.length === 2) startPinch(e);
    else if (pinch) endPinch();
  } else if (e.touches.length === 1 && !pinch && shrink < 1 && pages.contains(e.target)) {"""
                if (!script.contains(oldTouchStart)) {
                    throw GradleException("Unexpected pinned Gander PDF touch-start handler.")
                }
                script = script.replace(oldTouchStart, newTouchStart)

                val oldTouchMove = """document.addEventListener("touchmove", function (e) {
  if (pinch && e.touches.length === 2) {
    movePinch(e);
  } else if (tap && e.touches.length === 1 &&"""
                val newTouchMove = """document.addEventListener("touchmove", function (e) {
  if (pinch) {
    if (e.touches.length === 2) movePinch(e);
    else endPinch();
  } else if (tap && e.touches.length === 1 &&"""
                if (!script.contains(oldTouchMove)) {
                    throw GradleException("Unexpected pinned Gander PDF touch-move handler.")
                }
                script = script.replace(oldTouchMove, newTouchMove)

                val oldTouchEnd = """function touchEnded(e) {
  if (pinch && e.touches.length < 2) {
    endPinch();"""
                val newTouchEnd = """function touchEnded(e) {
  if (pinch && e.touches.length !== 2) {
    endPinch();"""
                if (!script.contains(oldTouchEnd)) {
                    throw GradleException("Unexpected pinned Gander PDF touch-end handler.")
                }
                script = script.replace(oldTouchEnd, newTouchEnd)

                // touchcancel does not guarantee a final touchend. Always unwind a live
                // custom pinch so vw-moving/transform state cannot be left stuck.
                val oldTouchCancel = """document.addEventListener("touchcancel", function (e) {
  tap = lastTap = null;
  if (pinch && e.touches.length < 2) endPinch();
}, { passive: true });"""
                val newTouchCancel = """document.addEventListener("touchcancel", function (e) {
  tap = lastTap = null;
  if (pinch) endPinch();
}, { passive: true });"""
                if (!script.contains(oldTouchCancel)) {
                    throw GradleException("Unexpected pinned Gander PDF touch-cancel handler.")
                }
                script = script.replace(oldTouchCancel, newTouchCancel)

                // Larger SAF range reads reduce repeated descriptor opens and seeks for
                // PDFs stored by document providers.
                val oldRangeChunk = "rangeChunkSize: 65536,"
                val newRangeChunk = "rangeChunkSize: 262144,"
                if (!script.contains(oldRangeChunk)) {
                    throw GradleException("Unexpected pinned Gander PDF range chunk size.")
                }
                script = script.replace(oldRangeChunk, newRangeChunk)

                val oldEndPinch = """function endPinch() {
  var g = pinch;
  pinch = null;
  if (g.taken) settle(g.to, g.at, g.y);
}"""
                val newEndPinch = """function endPinch() {
  var g = pinch;
  pinch = null;
  if (g.taken) settle(g.to, g.at, g.y);
  else schedulePageWork(false);
}"""
                if (!script.contains(oldEndPinch)) {
                    throw GradleException("Unexpected pinned Gander PDF pinch-end handler.")
                }
                script = script.replace(oldEndPinch, newEndPinch)

                val oldSettleObserve = "  if (watched) observePages();"
                val newSettleObserve = "  schedulePageWork(watched);"
                if (!script.contains(oldSettleObserve)) {
                    throw GradleException("Unexpected pinned Gander PDF settle observer call.")
                }
                script = script.replace(oldSettleObserve, newSettleObserve)

                val oldSettlePump = """  pump();
  /* The page under the middle of the screen is likely another one now, and the sharp
     patches, if any were up, have nothing left to sharpen. A scroll of nought says
     neither, so both are said here. */"""
                val newSettlePump = """  /* Page work is started later from an idle slot, after the fit-width frame has painted.
     The queued pages keep their old bitmap until their replacement is ready. */"""
                if (!script.contains(oldSettlePump)) {
                    throw GradleException("Unexpected pinned Gander PDF settle render call.")
                }
                script = script.replace(oldSettlePump, newSettlePump)

                val oldMoved = """function moved() {
  schedulePageReport();
  scheduleRefine();
}"""
                val newMoved = """function moved() {
  schedulePageReport();
  scheduleRefine();
  if (pinch) cancelPageWork();
  else if (pageWorkHold && !pageWorkTimer && !pageWorkFrame) schedulePageWork(false);
}"""
                if (!script.contains(oldMoved)) {
                    throw GradleException("Unexpected pinned Gander PDF viewport-moved handler.")
                }
                script = script.replace(oldMoved, newMoved)

                val oldPump = "function pump() {"
                val newPump = """function pump() {
  if (pageWorkHold) return;"""
                if (!script.contains(oldPump)) {
                    throw GradleException("Unexpected pinned Gander PDF render pump.")
                }
                script = script.replace(oldPump, newPump)

                val oldObservePages = "function observePages() {"
                val newObservePages = """var pageWorkTimer = 0;
var pageWorkFrame = 0;
var pageWorkGeneration = 0;
var pageWorkNeedsObserve = false;
var pageWorkHold = false;

function cancelPageWork() {
  pageWorkHold = true;
  pageWorkGeneration++;
  if (pageWorkTimer) {
    clearTimeout(pageWorkTimer);
    pageWorkTimer = 0;
  }
  if (pageWorkFrame && typeof window.cancelAnimationFrame === "function") {
    window.cancelAnimationFrame(pageWorkFrame);
    pageWorkFrame = 0;
  }
}

function schedulePageWork(reobserve) {
  if (reobserve) pageWorkNeedsObserve = true;
  cancelPageWork();
  var generation = pageWorkGeneration;
  pageWorkTimer = setTimeout(function () {
    pageWorkTimer = 0;
    var run = function () {
      pageWorkFrame = 0;
      if (generation !== pageWorkGeneration || pinch) return;
      pageWorkHold = false;
      var needsObserve = pageWorkNeedsObserve;
      pageWorkNeedsObserve = false;
      if (needsObserve) observePages();
      pump();
    };
    if (typeof window.requestAnimationFrame === "function") {
      pageWorkFrame = window.requestAnimationFrame(run);
    } else {
      pageWorkTimer = setTimeout(run, 16);
    }
  }, 32);
}

function observePages() {"""
                if (!script.contains(oldObservePages)) {
                    throw GradleException("Unexpected pinned Gander PDF observer declaration.")
                }
                script = script.replace(oldObservePages, newObservePages)

                // Digital signatures: PDF.js already knows where every signature's byte
                // range and PKCS#7 blob is. Expose them to Kotlin on demand; the app
                // verifies them itself and never trusts this payload for the verdict.
                val oldSignatureHook = """function onCommand(msg) {
  /* The title bar's height and whereabouts, which app.js keeps for every page */
  if (vwBarSaid(msg)) return;"""
                val newSignatureHook = """function fzBase64(bytes) {
  var text = "";
  for (var i = 0; i < bytes.length; i += 0x8000) {
    text += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  }
  return btoa(text);
}

var fzSigTries = 0;

async function requestFzSignatures() {
  if (!searchPort) return;
  if (!doc) {
    if (fzSigTries++ < 40) setTimeout(requestFzSignatures, 250);
    return;
  }
  var out = [];
  try {
    var list = await doc.getSignatures();
    for (var i = 0; i < list.length; i++) {
      var sig = list[i];
      var payload = await doc.getSignatureData(sig.id);
      if (!payload || !payload.pkcs7) continue;
      out.push({
        name: sig.signerName || "",
        fieldName: sig.fieldName || "",
        reason: sig.reason || "",
        location: sig.location || "",
        date: sig.signingTime || "",
        subFilter: sig.subFilter || "",
        coversWhole: !!sig.coversWholeDocument,
        modifications: sig.modificationsAfterSignature || 0,
        byteRange: sig.byteRange || null,
        contents: fzBase64(payload.pkcs7)
      });
    }
  } catch (err) {
    out = [];
  }
  try { searchPort.postMessage("fzsig " + JSON.stringify(out)); } catch (err) {}
}

/*
 * Draw the verdict over the signature widget, the way a desktop reader replaces
 * the signer's "not verified" stamp with its own status panel. The layer is an
 * SVG in page units, so it scales with the page at every zoom instead of being
 * a fixed-size badge that drifts off the field.
 */
var fzVerdicts = null;
var fzSigRects = null;
var FZ_SVG_NS = "http://www.w3.org/2000/svg";

async function fzCollectSignatureRects() {
  if (fzSigRects) return fzSigRects;
  var out = [];
  for (var n = 1; n <= doc.numPages; n++) {
    var page = await doc.getPage(n);
    var annotations = await page.getAnnotations({ intent: "display" });
    if (!annotations || !annotations.length) continue;
    var viewport = page.getViewport({ scale: 1 });
    for (var i = 0; i < annotations.length; i++) {
      var annotation = annotations[i];
      if (annotation.fieldType !== "Sig" || !annotation.rect) continue;
      /* 6.3.289 dropped convertToViewportRectangle; a widget rect is axis aligned
         under the page transform, so two opposite corners are enough. */
      var first = viewport.convertToViewportPoint(annotation.rect[0], annotation.rect[1]);
      var second = viewport.convertToViewportPoint(annotation.rect[2], annotation.rect[3]);
      out.push({
        page: n,
        fieldName: annotation.fieldName || "",
        viewWidth: viewport.width,
        viewHeight: viewport.height,
        x: Math.min(first[0], second[0]),
        y: Math.min(first[1], second[1]),
        width: Math.abs(second[0] - first[0]),
        height: Math.abs(second[1] - first[1])
      });
    }
  }
  /* Only a non-empty answer is worth caching: annotations can still be loading
     when the verdict arrives, and an empty cache would never be retried. */
  if (out.length) fzSigRects = out;
  return out;
}

function fzVerdictFor(fieldName) {
  if (!fzVerdicts) return null;
  for (var i = 0; i < fzVerdicts.length; i++) {
    if ((fzVerdicts[i].fieldName || "") === (fieldName || "")) return fzVerdicts[i];
  }
  return fzVerdicts.length === 1 ? fzVerdicts[0] : null;
}

/* Adobe renders the stamp in Arial/Helvetica. Arimo is metrically identical to
   Arial, so the words break at the same place Adobe breaks them. */
var fzStampFontReady = false;
var fzStampFontLoading = false;

function fzStampFamily() {
  return "FZStamp, Arial, Helvetica, sans-serif";
}

function fzEnsureStampFont() {
  if (fzStampFontReady || fzStampFontLoading) return;
  fzStampFontLoading = true;
  try {
    var face = new FontFace("FZStamp",
      "url('" + vwAssetUrl("./fonts/Arimo.ttf") + "')", { weight: "100 900" });
    document.fonts.add(face);
    face.load().then(function () {
      fzStampFontReady = true;
      fzStampFontLoading = false;
      fzPaintSignatureVerdicts();
    }, function () {
      fzStampFontLoading = false;
    });
  } catch (err) {
    fzStampFontLoading = false;
  }
}

function fzSvgText(layer, x, y, size, colour, weight, text, textLength) {
  var node = document.createElementNS(FZ_SVG_NS, "text");
  node.setAttribute("x", x);
  node.setAttribute("y", y);
  node.setAttribute("font-size", size);
  node.setAttribute("font-family", fzStampFamily());
  node.setAttribute("font-weight", weight);
  node.setAttribute("fill", colour);
  if (textLength) {
    node.setAttribute("textLength", textLength);
    node.setAttribute("lengthAdjust", "spacingAndGlyphs");
  }
  node.textContent = text;
  layer.appendChild(node);
}

function fzMeasure(layer, text, size, weight) {
  var node = document.createElementNS(FZ_SVG_NS, "text");
  node.setAttribute("x", "-10000");
  node.setAttribute("font-size", size);
  node.setAttribute("font-family", fzStampFamily());
  node.setAttribute("font-weight", weight || "400");
  node.textContent = text;
  layer.appendChild(node);
  var width = 0;
  try {
    width = node.getComputedTextLength();
  } catch (err) {
    width = String(text).length * size * 0.5;
  }
  layer.removeChild(node);
  return width;
}

function fzWrap(layer, text, size, maxWidth) {
  var words = String(text).split(" ");
  var out = [];
  var line = "";
  for (var i = 0; i < words.length; i++) {
    var word = words[i];
    if (!line) { line = word; continue; }
    var probe = line + " " + word;
    if (fzMeasure(layer, probe, size, "400") <= maxWidth) line = probe;
    else { out.push(line); line = word; }
  }
  if (line) out.push(line);
  return out;
}

/* Adobe's tick is a tapered brush stroke, not an even one. This outline is
   traced from Adobe's own stamp raster (green pixels, gaps under the text
   closed) in fractions of the tick's bounding box. */
var FZ_TICK_OUTLINE = [
  [0.2946,0.9621],[0.3259,0.9621],[0.3482,0.9844],[0.3795,0.9844],[0.3839,0.971],[0.4263,0.9598],[0.4263,0.9375],[0.4621,0.8973],[0.471,0.8571],
  [0.4978,0.8527],[0.4978,0.8304],[0.5246,0.808],[0.5246,0.7946],[0.5692,0.7455],[0.5781,0.7143],[0.596,0.7098],[0.6049,0.6786],[0.6228,0.6741],
  [0.6228,0.6518],[0.6496,0.6295],[0.6496,0.5982],[0.6652,0.596],[0.6942,0.5446],[0.7299,0.5223],[0.7299,0.5],[0.7656,0.4598],[0.7746,0.4286],
  [0.7879,0.4241],[0.7946,0.3996],[0.8103,0.3973],[0.8192,0.3571],[0.8549,0.3348],[0.8549,0.3036],[0.8817,0.2902],[0.9174,0.2232],[0.9531,0.192],
  [0.9621,0.1607],[0.9844,0.1384],[0.9844,0.0893],[0.9643,0.0871],[0.9063,0.0335],[0.8661,0.0335],[0.8616,0.0112],[0.846,0.0134],[0.8438,0.0335],
  [0.8192,0.0357],[0.8192,0.058],[0.7656,0.125],[0.7478,0.1741],[0.7299,0.1786],[0.7299,0.2054],[0.6763,0.2857],[0.6763,0.317],[0.6496,0.3304],
  [0.6496,0.3527],[0.6228,0.3661],[0.6205,0.3906],[0.6049,0.3929],[0.6049,0.4241],[0.5871,0.4286],[0.5513,0.5134],[0.5156,0.5536],[0.5156,0.5938],
  [0.4799,0.6071],[0.4799,0.6295],[0.4621,0.6429],[0.4621,0.6652],[0.4442,0.6696],[0.4442,0.692],[0.4174,0.7143],[0.4174,0.7366],[0.3996,0.75],
  [0.3884,0.7924],[0.3571,0.7924],[0.3348,0.7656],[0.3192,0.7634],[0.3192,0.7411],[0.2656,0.692],[0.2656,0.6696],[0.25,0.6674],[0.221,0.6384],
  [0.221,0.625],[0.1853,0.6027],[0.1853,0.5804],[0.1674,0.5759],[0.1674,0.5446],[0.1429,0.5424],[0.1406,0.5268],[0.1027,0.4888],[0.0513,0.5446],
  [0.0513,0.558],[0.0156,0.5804],[0.0156,0.6563],[0.0402,0.6763],[0.058,0.6763],[0.0603,0.7009],[0.1049,0.7321],[0.1138,0.7634],[0.1563,0.7835],
  [0.1763,0.817],[0.2277,0.8638],[0.2455,0.8638],[0.2679,0.9085],[0.2924,0.9107],[0.2924,0.9598]
];

function fzTickPoints(w, h) {
  var pts = [];
  for (var i = 0; i < FZ_TICK_OUTLINE.length; i++) {
    pts.push({ x: FZ_TICK_OUTLINE[i][0] * w, y: FZ_TICK_OUTLINE[i][1] * h });
  }
  return pts;
}

function fzTickFit(pts, w, h) {
  var minX = pts[0].x, maxX = pts[0].x, minY = pts[0].y, maxY = pts[0].y;
  for (var i = 1; i < pts.length; i++) {
    if (pts[i].x < minX) minX = pts[i].x;
    if (pts[i].x > maxX) maxX = pts[i].x;
    if (pts[i].y < minY) minY = pts[i].y;
    if (pts[i].y > maxY) maxY = pts[i].y;
  }
  var sx = w / ((maxX - minX) || 1);
  var sy = h / ((maxY - minY) || 1);
  return { sx: sx, sy: sy, tx: -minX * sx, ty: -minY * sy };
}

function fzTickPath(pts, fit, x, y) {
  var d = "";
  for (var i = 0; i < pts.length; i++) {
    d += (i ? " L" : "M") + (x + pts[i].x * fit.sx + fit.tx) +
      " " + (y + pts[i].y * fit.sy + fit.ty);
  }
  return d + " Z";
}

function fzBigTick(layer, x, y, w, h, colour) {
  var base = fzTickPoints(w, h);
  var fit = fzTickFit(base, w, h);
  var green = fzTickPath(base, fit, x, y);
  var rim = w * 0.02;
  var shadow = document.createElementNS(FZ_SVG_NS, "path");
  shadow.setAttribute("d", green);
  shadow.setAttribute("fill", "#343434");
  shadow.setAttribute("stroke", "#343434");
  shadow.setAttribute("stroke-width", rim * 2);
  shadow.setAttribute("stroke-linejoin", "round");
  shadow.setAttribute("transform",
    "translate(" + (w * 0.057) + " " + (h * 0.057) + ")");
  layer.appendChild(shadow);
  var rimNode = document.createElementNS(FZ_SVG_NS, "path");
  rimNode.setAttribute("d", green);
  rimNode.setAttribute("fill", "#343434");
  rimNode.setAttribute("stroke", "#343434");
  rimNode.setAttribute("stroke-width", rim * 2);
  rimNode.setAttribute("stroke-linejoin", "round");
  layer.appendChild(rimNode);
  var inner = document.createElementNS(FZ_SVG_NS, "path");
  inner.setAttribute("d", green);
  inner.setAttribute("fill", colour);
  layer.appendChild(inner);
}

function fzBigCross(layer, x, y, w, h, colour) {
  var d = "M" + (x + w * 0.1) + " " + (y + h * 0.1) +
    " L" + (x + w * 0.9) + " " + (y + h * 0.9) +
    " M" + (x + w * 0.9) + " " + (y + h * 0.1) +
    " L" + (x + w * 0.1) + " " + (y + h * 0.9);
  var path = document.createElementNS(FZ_SVG_NS, "path");
  path.setAttribute("d", d);
  path.setAttribute("fill", "none");
  path.setAttribute("stroke", colour);
  path.setAttribute("stroke-width", w * 0.146);
  path.setAttribute("stroke-linecap", "butt");
  path.setAttribute("opacity", "0.85");
  layer.appendChild(path);
}

/* Adobe's stamp, measured off its own rendering: the text fills the field
   between 1.2 pt margins, the title is 1.954 em tall, the body lines 0.976 em
   apart, and the tick is centred in the field behind them. */
function fzPaintSignatureVerdicts() {
  if (!fzVerdicts || !fzSigRects) return;
  fzEnsureStampFont();
  for (var i = 0; i < fzSigRects.length; i++) {
    var rect = fzSigRects[i];
    var slot = slots[rect.page - 1];
    if (!slot) continue;
    var verdict = fzVerdictFor(rect.fieldName);
    var layer = slot.box.querySelector(".vw-sig-layer");
    if (!verdict) {
      if (layer) layer.remove();
      continue;
    }
    if (!layer) {
      layer = document.createElementNS(FZ_SVG_NS, "svg");
      layer.setAttribute("class", "vw-sig-layer");
      layer.setAttribute("viewBox", "0 0 " + rect.viewWidth + " " + rect.viewHeight);
      /* left/top as well as inset: inset is recent enough that a WebView without
         it falls back to the static position, which is the foot of the page. */
      layer.style.cssText =
        "position:absolute;left:0;top:0;right:0;bottom:0;width:100%;height:100%;" +
        "pointer-events:none;overflow:hidden;z-index:2";
      slot.box.appendChild(layer);
    }
    layer.replaceChildren();
    var trusted = verdict.state === "trusted";
    var valid = trusted || verdict.state === "valid";
    var colour = trusted ? "#2ab254" : verdict.state === "valid" ? "#1f6fb2" :
      verdict.state === "changed" ? "#b26a00" : "#c62828";
    var label = valid ? "Signature valid" :
      verdict.state === "changed" ? "Changed after signing" : "Signature invalid";
    var paper = document.createElementNS(FZ_SVG_NS, "rect");
    paper.setAttribute("x", rect.x);
    paper.setAttribute("y", rect.y);
    paper.setAttribute("width", rect.width);
    paper.setAttribute("height", rect.height);
    paper.setAttribute("fill", "#ffffff");
    layer.appendChild(paper);

    var em = Math.min(rect.width * 0.0827, rect.height * 0.0886);
    if (em < 2) em = 2;
    var pad = Math.max(0.8, rect.width * 0.008);
    var left = rect.x + pad;
    var textWidth = rect.width - pad * 2;
    var titleSize = em * 1.954;
    var linePitch = em * 0.976;
    var titleTop = rect.y + em * 1.056;

    var sources = [];
    if (verdict.signer) sources.push("Digitally signed by " + verdict.signer);
    if (verdict.date) sources.push("Date: " + verdict.date);
    if (verdict.reason) sources.push("Reason: " + verdict.reason);
    if (verdict.location) sources.push("Location: " + verdict.location);
    var lines = [];
    for (var s = 0; s < sources.length; s++) {
      lines = lines.concat(fzWrap(layer, sources[s], em, textWidth));
    }

    var natural = fzMeasure(layer, label, titleSize, "500");
    if (natural > textWidth * 1.12) {
      titleSize = titleSize * textWidth * 1.12 / natural;
      natural = fzMeasure(layer, label, titleSize, "500");
    }
    var titleLength = natural > textWidth ? textWidth : 0;

    var tickW = em * 7.637;
    var tickH = em * 7.573;
    var tickX = rect.x + (rect.width - tickW) / 2;
    var tickY = rect.y + em * 1.977;
    if (valid) fzBigTick(layer, tickX, tickY, tickW, tickH, colour);
    else fzBigCross(layer, tickX, tickY, tickW, tickH, colour);

    fzSvgText(layer, left, titleTop + titleSize * 0.716, titleSize,
      "#333333", "500", label, titleLength);
    for (var k = 0; k < lines.length; k++) {
      fzSvgText(layer, left, rect.y + em * 4.672 + k * linePitch, em,
        "#333333", "400", lines[k]);
    }
  }
}

async function fzShowSignatureVerdict(json, attempt) {
  try { fzVerdicts = JSON.parse(json); } catch (err) {
    return;
  }
  attempt = attempt || 0;
  if (!doc) {
    if (attempt < 20) setTimeout(function () { fzShowSignatureVerdict(json, attempt + 1); }, 250);
    return;
  }
  fzSigRects = null;
  try { await fzCollectSignatureRects(); } catch (err) {
    return;
  }
  fzPaintSignatureVerdicts();
  var layers = document.querySelectorAll(".vw-sig-layer").length;
  if (!layers && attempt < 6) {
    setTimeout(function () { fzShowSignatureVerdict(json, attempt + 1); }, 400);
  }
}

function onCommand(msg) {
  /* The title bar's height and whereabouts, which app.js keeps for every page */
  if (vwBarSaid(msg)) return;
  if (msg.indexOf("fzverdict ") === 0) { fzShowSignatureVerdict(msg.slice(10)); return; }
  if (msg === "fzsig") { requestFzSignatures(); return; }"""
                if (!script.contains(oldSignatureHook)) {
                    throw GradleException("Unexpected pinned Gander PDF command entry point.")
                }
                script = script.replace(oldSignatureHook, newSignatureHook)
                outputBytes = script.toByteArray(Charsets.UTF_8)
            }

            if (asset.path == "viewer/app.js") {
                var script = String(outputBytes, Charsets.UTF_8)
                // A download that is still being finalised answers the first read
                // with an HTTP 404 and works on the next open. Retry instead of
                // handing the reader an error card, and keep the URL in the last
                // message so a screenshot names the resource that failed.
                val oldFetch = """function vwFetchDoc(kind) {
  return fetch(vwDocUrl()).then(function (r) {
    if (!r.ok) throw new Error("Could not read the file (HTTP " + r.status + ")");
    return kind === "text" ? r.text() : r.arrayBuffer();
  }).then(function (doc) {
    return vwShowBusy(doc, kind === "text" ? doc.length : doc.byteLength);
  });
}"""
                val newFetch = """function vwFetchDoc(kind) {
  var attempt = 0;
  function load() {
    return fetch(vwDocUrl()).then(function (r) {
      if (!r.ok) {
        var error = new Error("Could not read the file (HTTP " + r.status +
          " for " + vwDocUrl() + ")");
        error.fzStatus = r.status;
        throw error;
      }
      return kind === "text" ? r.text() : r.arrayBuffer();
    });
  }
  function retry(error) {
    var status = error && error.fzStatus;
    if (attempt >= 6 || (status && status !== 404 && status !== 416 &&
        status !== 500 && status !== 503)) {
      throw error;
    }
    attempt += 1;
    return new Promise(function (resolve) { setTimeout(resolve, 700 * attempt); })
      .then(load).catch(retry);
  }
  return load().catch(retry).then(function (doc) {
    return vwShowBusy(doc, kind === "text" ? doc.length : doc.byteLength);
  });
}"""
                if (!script.contains(oldFetch)) {
                    throw GradleException("Unexpected pinned PDF document fetch.")
                }
                script = script.replace(oldFetch, newFetch)
                outputBytes = script.toByteArray(Charsets.UTF_8)
            }

            if (asset.path == "viewer/pdf.html") {
                var html = String(outputBytes, Charsets.UTF_8)
                // Native WebView pinch owns the gesture at the 70% column too. With
                // pan-x pan-y the page took the pinch and stopped at the fitted width,
                // so growing past it needed a second gesture.
                val oldShrunkTouch = "  html.vw-shrunk { touch-action: pan-x pan-y; }"
                val newShrunkTouch = "  html.vw-shrunk { touch-action: manipulation; }"
                if (!html.contains(oldShrunkTouch)) {
                    throw GradleException("Unexpected pinned PDF shrunk touch-action rule.")
                }
                html = html.replace(oldShrunkTouch, newShrunkTouch)

                // The 8px strip between pages read as one page on a phone: at the
                // 70% column it drew under 6px. Twenty keeps a clear band of the
                // reader's ground between sheets without wasting screen height.
                val oldPageGap = "  :root { --pg-gap: 8px; }"
                val newPageGap = "  :root { --pg-gap: 20px; }"
                if (!html.contains(oldPageGap)) {
                    throw GradleException("Unexpected pinned PDF page gap.")
                }
                html = html.replace(oldPageGap, newPageGap)
                outputBytes = html.toByteArray(Charsets.UTF_8)
            }

            if (asset.path == "viewer/pdf-pages.css") {
                var css = String(outputBytes, Charsets.UTF_8)
                // Night mode paints black pages on a black ground, so its only seam
                // is this divider. One device pixel disappeared on a phone screen;
                // three stay visible and make the page break obvious.
                val oldNightEdge = "  --pg-edge: calc(1px * var(--vw-zoom));"
                val newNightEdge = "  --pg-edge: calc(3px * var(--vw-zoom));"
                if (!css.contains(oldNightEdge)) {
                    throw GradleException("Unexpected pinned PDF night page divider.")
                }
                css = css.replace(oldNightEdge, newNightEdge)
                outputBytes = css.toByteArray(Charsets.UTF_8)
            }

            if (asset.path == "viewer/pptx.html" || asset.path == "viewer/xlsx.html") {
                var html = String(outputBytes, Charsets.UTF_8)
                val styleLink = "<link rel=\"stylesheet\" href=\"app.css\">"
                val scriptLink = "<script src=\"find.js\"></script>"
                if (!html.contains(styleLink) || !html.contains(scriptLink)) {
                    throw GradleException("Unexpected pinned viewer HTML structure: " + asset.path)
                }
                html = html.replace(styleLink, styleLink + "\n<link rel=\"stylesheet\" href=\"hindi-fonts.css\">")
                html = html.replace(scriptLink, scriptLink + "\n<script src=\"hindi-fonts.js\"></script>")
                outputBytes = html.toByteArray(Charsets.UTF_8)
            }

            if (asset.path == "viewer/xlsx.js") {
                var script = String(outputBytes, Charsets.UTF_8)
                val marker = "window.vwSheetZoom = function (z, x, y) {"
                if (!script.contains(marker)) {
                    throw GradleException("Unexpected pinned XLSX zoom API structure.")
                }
                val toolsApi = """
                    window.vwSheetFitWidth = function () {
                      if (!current) return zoom;
                      var target = room().width / Math.max(1, current.fullWidth);
                      zoomTo(target, innerWidth / 2, innerHeight / 2, 0);
                      return zoom;
                    };
                    window.vwSheetPreparePrint = function () {
                      if (!current) return false;
                      for (var k = 0; k < current.pieces.length; k++) place(k);
                      return true;
                    };
                """.trimIndent()
                script = script.replace(marker, toolsApi + "\n" + marker)
                outputBytes = script.toByteArray(Charsets.UTF_8)
            }

            if (asset.path == "viewer/xlsx.html") {
                var html = String(outputBytes, Charsets.UTF_8)
                val printStyle = """
                    <style>
                    @media print {
                      html, body { height: auto !important; overflow: visible !important; }
                      body { padding: 0 !important; }
                      #tabs, #cellbar, .vw-status { display: none !important; }
                      #sheet { display: block !important; width: max-content !important; min-width: 0 !important; }
                      #grid { zoom: 1 !important; }
                      .vw-head, .vw-tl { position: static !important; }
                      .vw-p { content-visibility: visible !important; }
                    }
                    </style>
                """.trimIndent()
                if (!html.contains("</head>")) {
                    throw GradleException("Unexpected pinned XLSX head structure.")
                }
                html = html.replace("</head>", printStyle + "\n</head>")
                outputBytes = html.toByteArray(Charsets.UTF_8)
            }

            val destinationPath = if (asset.path == "licences.md") {
                "viewer/licences.md"
            } else {
                asset.path
            }
            val destination = root.resolve(destinationPath)
            destination.parentFile.mkdirs()
            destination.writeBytes(outputBytes)
        }
    }
}

android {
    namespace = "com.fz.office"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fz.office"
        minSdk = 24
        targetSdk = 35
        versionCode = 75
        versionName = "1.0.35"
    }

    // Keep the same testing certificate across main and feature-branch APKs.
    // Local builds without the CI keystore continue using the normal debug certificate.
    if (hasTestingSigning) {
        signingConfigs {
            create("testing") {
                storeFile = file(testingKeystorePath!!)
                storePassword = testingStorePassword
                keyAlias = testingKeyAlias
                keyPassword = testingKeyPassword
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("testing")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    sourceSets.getByName("main").assets.srcDir(hindiFontAssets.get().asFile)
    sourceSets.getByName("main").assets.srcDir(ganderViewerAssets.get().asFile)

    buildFeatures {
        buildConfig = false
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareHindiFonts, prepareGanderViewerAssets)
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
