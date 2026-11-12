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

                // Capture operator bounds during the already-required full-page draw.
                // PDF.js can then skip display-list operations that cannot intersect a
                // high-resolution zoom tile, instead of replaying the entire page.
                val oldMaxTiles = "var MAX_TILES = 2;"
                val newMaxTiles = "var MAX_TILES = 1;"
                if (!script.contains(oldMaxTiles)) {
                    throw GradleException("Unexpected pinned Gander PDF tile concurrency limit.")
                }
                script = script.replace(oldMaxTiles, newMaxTiles)

                val oldBaseRender = """      recordImages: mode
    });
    return slot.task.promise.then(function () {"""
                val newBaseRender = """      recordImages: mode,
      recordOperations: !page.recordedBBoxes
    });
    return slot.task.promise.then(function () {"""
                if (!script.contains(oldBaseRender)) {
                    throw GradleException("Unexpected pinned Gander PDF base-page render options.")
                }
                script = script.replace(oldBaseRender, newBaseRender)

                val oldTileStart = "function drawTile(req) {"
                val newTileStart = """function tileOperationsFilter(slot, req) {
  var boxes = slot.page && slot.page.recordedBBoxes;
  if (!boxes || !boxes.length ||
      typeof boxes.isEmpty !== "function" ||
      typeof boxes.minX !== "function" ||
      typeof boxes.maxX !== "function" ||
      typeof boxes.minY !== "function" ||
      typeof boxes.maxY !== "function") return null;

  /* BBoxReader stores coordinates as fractions of the original full-page canvas. */
  var left = Math.max(0, req.x / req.boxW - 1 / 256);
  var top = Math.max(0, req.y / req.boxH - 1 / 256);
  var right = Math.min(1, (req.x + req.w) / req.boxW + 1 / 256);
  var bottom = Math.min(1, (req.y + req.h) / req.boxH + 1 / 256);

  return function (index) {
    /* Keep unbounded state/dependency operations conservatively. */
    if (index < 0 || index >= boxes.length || boxes.isEmpty(index)) return true;
    return boxes.maxX(index) >= left && boxes.minX(index) <= right &&
      boxes.maxY(index) >= top && boxes.minY(index) <= bottom;
  };
}

function drawTile(req) {"""
                if (!script.contains(oldTileStart)) {
                    throw GradleException("Unexpected pinned Gander PDF tile-render function.")
                }
                script = script.replace(oldTileStart, newTileStart)

                val oldTileRender = """    recordImages: mode,
    viewport: slot.unscaled.clone({ scale: cssScale * req.k }),"""
                val newTileRender = """    recordImages: mode,
    operationsFilter: tileOperationsFilter(slot, req),
    viewport: slot.unscaled.clone({ scale: cssScale * req.k }),"""
                if (!script.contains(oldTileRender)) {
                    throw GradleException("Unexpected pinned Gander PDF tile render options.")
                }
                script = script.replace(oldTileRender, newTileRender)

                val oldStartPinch = """function startPinch(e) {
  finishAnimation();"""
                val newStartPinch = """function startPinch(e) {
  if (pinch) endPinch();
  finishAnimation();"""
                if (!script.contains(oldStartPinch)) {
                    throw GradleException("Unexpected pinned Gander PDF pinch-start handler.")
                }
                script = script.replace(oldStartPinch, newStartPinch)

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
                outputBytes = script.toByteArray(Charsets.UTF_8)
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
        versionCode = 38
        versionName = "0.9.8"
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
