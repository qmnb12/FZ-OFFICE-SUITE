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
    layout.projectDirectory.file("vendor/offline-pdf-viewer-3.manifest").asFile
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
                val cMapConfig = """opts.cMapUrl = vwAssetUrl("./lib/cmaps/");
  opts.cMapPacked = true;
  """
                if (!script.contains(cMapConfig) || !script.contains("opts.wasmUrl = vwAssetUrl(\"./lib/wasm/\");")) {
                    throw GradleException("Unexpected pinned Gander PDF.js asset configuration.")
                }
                // CMaps are optional for PDFs with embedded fonts. Keep the bundled
                // WASM decoders (JPEG 2000/JBIG2) but don't request a CMap directory
                // that is not shipped in this compact offline bundle.
                script = script.replace(cMapConfig, "")
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

            val destination = root.resolve(asset.path)
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
        versionCode = 37
        versionName = "0.9.7"
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
