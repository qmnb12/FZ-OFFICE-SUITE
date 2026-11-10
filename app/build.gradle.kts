import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

val googleFontsCommit = "86aced91de05cd4f96a06838124cca45be337b2b"
val hindiFontAssets = layout.buildDirectory.dir("generated/assets/hindiFonts")

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


val bundledHindiFonts = listOf(
    mapOf("family" to "notosansdevanagari", "file" to "NotoSansDevanagari[wdth,wght].ttf", "output" to "NotoSansDevanagari-VF.ttf", "size" to "641944", "sha" to "163f04bddc5a96fc523e4080466cac5c588f6594"),
    mapOf("family" to "notoserifdevanagari", "file" to "NotoSerifDevanagari[wdth,wght].ttf", "output" to "NotoSerifDevanagari-VF.ttf", "size" to "757692", "sha" to "3d90da279fe70b5017d9b51f58772f0c9ea4146e"),
    mapOf("family" to "mukta", "file" to "Mukta-Regular.ttf", "output" to "Mukta-Regular.ttf", "size" to "432248", "sha" to "ccb22cfb2cbc43cd516d6376492ad6dde3a94c2f"),
    mapOf("family" to "mukta", "file" to "Mukta-Bold.ttf", "output" to "Mukta-Bold.ttf", "size" to "418416", "sha" to "d7256f58bd2f8344c494e0df3be08dcd00d348c8"),
    mapOf("family" to "hind", "file" to "Hind-Regular.ttf", "output" to "Hind-Regular.ttf", "size" to "299532", "sha" to "d354dbbcd295b1b7e09b015f0bd490864ad47aca"),
    mapOf("family" to "hind", "file" to "Hind-Bold.ttf", "output" to "Hind-Bold.ttf", "size" to "286604", "sha" to "4469b8415f1dcbced9dfc3f2bb24e2f2f2618c5d"),
    mapOf("family" to "tirodevanagarihindi", "file" to "TiroDevanagariHindi-Regular.ttf", "output" to "TiroDevanagariHindi-Regular.ttf", "size" to "423224", "sha" to "9a55b8c823496ca170aad4c4fced67891541fff0"),
    mapOf("family" to "tirodevanagarihindi", "file" to "TiroDevanagariHindi-Italic.ttf", "output" to "TiroDevanagariHindi-Italic.ttf", "size" to "425636", "sha" to "b0a5bd9916bf6d912ce75bee30c24d9db42e5c41"),
    mapOf("family" to "kalam", "file" to "Kalam-Regular.ttf", "output" to "Kalam-Regular.ttf", "size" to "427360", "sha" to "0c956a29348e69bff24a1ae8952fd0b42569311c"),
    mapOf("family" to "sahitya", "file" to "Sahitya-Regular.ttf", "output" to "Sahitya-Regular.ttf", "size" to "261652", "sha" to "be1a414eff64e0ba15e8eb5852f35a66da2923c9"),
    mapOf("family" to "sahitya", "file" to "Sahitya-Bold.ttf", "output" to "Sahitya-Bold.ttf", "size" to "267140", "sha" to "c9f249fcc9f4664f439c159ac1ac720a1624c416"),
    mapOf("family" to "martel", "file" to "Martel-Regular.ttf", "output" to "Martel-Regular.ttf", "size" to "170196", "sha" to "7bd3ef19172c35f443476ce5657e1247c614ca66"),
    mapOf("family" to "baloo2", "file" to "Baloo2[wght].ttf", "output" to "Baloo2-VF.ttf", "size" to "683200", "sha" to "bc1b9f1191c0d6d23cb2ce0af66aba82ddbf8d6c"),
    mapOf("family" to "khand", "file" to "Khand-Regular.ttf", "output" to "Khand-Regular.ttf", "size" to "316808", "sha" to "0d8e9536f19b4a9842d95b699c33b7ac40f433cb"),
    mapOf("family" to "gotu", "file" to "Gotu-Regular.ttf", "output" to "Gotu-Regular.ttf", "size" to "697792", "sha" to "8182906d0266999a9df466a5370a2a9a06935fea"),
    mapOf("family" to "glegoo", "file" to "Glegoo-Regular.ttf", "output" to "Glegoo-Regular.ttf", "size" to "271368", "sha" to "388d1fe3c0985cabbf04eb5977fc6e6d0c553345")
)

val fontLicenses = listOf(
    mapOf("family" to "notosansdevanagari", "name" to "Noto-Sans-Devanagari-OFL.txt", "size" to "4386", "sha" to "cd2cc5c94b4151933eba6ee508e3975274d6fa07"),
    mapOf("family" to "notoserifdevanagari", "name" to "Noto-Serif-Devanagari-OFL.txt", "size" to "4386", "sha" to "cd2cc5c94b4151933eba6ee508e3975274d6fa07"),
    mapOf("family" to "mukta", "name" to "Mukta-OFL.txt", "size" to "4367", "sha" to "12057d5a1c20c7476c62a7509756036dc6fb6ed8"),
    mapOf("family" to "hind", "name" to "Hind-OFL.txt", "size" to "4373", "sha" to "6dd809659a8ccace7ccee108d024d33cd4f936da"),
    mapOf("family" to "tirodevanagarihindi", "name" to "Tiro-Devanagari-Hindi-OFL.txt", "size" to "4387", "sha" to "9acb0fd8b8e54a98007113da015d33419180ca05"),
    mapOf("family" to "kalam", "name" to "Kalam-OFL.txt", "size" to "4373", "sha" to "6dd809659a8ccace7ccee108d024d33cd4f936da"),
    mapOf("family" to "sahitya", "name" to "Sahitya-OFL.txt", "size" to "4393", "sha" to "6dc46a4ed56ad2d3fc10295f7253c646ec468d98"),
    mapOf("family" to "martel", "name" to "Martel-OFL.txt", "size" to "4436", "sha" to "8b5d372e76d3aec70a2c8debef29f4b9c8c98c30"),
    mapOf("family" to "baloo2", "name" to "Baloo-2-OFL.txt", "size" to "4384", "sha" to "72adb492d4f0ede2a9dbdefed607f33ad3a6fc68"),
    mapOf("family" to "khand", "name" to "Khand-OFL.txt", "size" to "4373", "sha" to "6dd809659a8ccace7ccee108d024d33cd4f936da"),
    mapOf("family" to "gotu", "name" to "Gotu-OFL.txt", "size" to "4349", "sha" to "2d2aa47cca45768c93b3a9b65752f7b7b9163587"),
    mapOf("family" to "glegoo", "name" to "Glegoo-OFL.txt", "size" to "4446", "sha" to "838d39e3aabb5e99b6163cd3ce4d1cb585444ef6")
)

val downloadHindiFonts = tasks.register("downloadHindiFonts") {
    outputs.dir(hindiFontAssets)
    doLast {
        val root = hindiFontAssets.get().asFile
        val fontDirectory = root.resolve("writer/fonts")
        val licenseDirectory = fontDirectory.resolve("licenses")
        fontDirectory.mkdirs()
        licenseDirectory.mkdirs()

        fun downloadVerified(relativePath: String, destination: java.io.File,
                             expectedSize: Long, expectedSha: String) {
            fun gitBlobSha(bytes: ByteArray): String {
                val header = ("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8)
                val digest = MessageDigest.getInstance("SHA-1")
                digest.update(header)
                digest.update(bytes)
                return digest.digest().joinToString("") { "%02x".format(it) }
            }

            if (destination.isFile && destination.length() == expectedSize) {
                val existing = destination.readBytes()
                if (gitBlobSha(existing) == expectedSha) return
            }

            val encodedPath = relativePath.replace("[", "%5B").replace("]", "%5D")
            val url = URL("https://raw.githubusercontent.com/google/fonts/" + googleFontsCommit + "/" + encodedPath)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 45000
            connection.instanceFollowRedirects = true
            try {
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw GradleException("Could not download Hindi font asset: " + relativePath
                            + " (HTTP " + connection.responseCode + ")")
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size.toLong() != expectedSize) {
                    throw GradleException("Unexpected size for " + relativePath + ": " + bytes.size)
                }
                val actualSha = gitBlobSha(bytes)
                if (actualSha != expectedSha) {
                    throw GradleException("Checksum mismatch for Hindi font asset: " + relativePath)
                }
                destination.parentFile.mkdirs()
                destination.writeBytes(bytes)
            } finally {
                connection.disconnect()
            }
        }

        bundledHindiFonts.forEach { font ->
            val family = font.getValue("family")
            val file = font.getValue("file")
            val output = font.getValue("output")
            downloadVerified(
                "ofl/" + family + "/" + file,
                fontDirectory.resolve(output),
                font.getValue("size").toLong(),
                font.getValue("sha")
            )
        }

        fontLicenses.forEach { license ->
            downloadVerified(
                "ofl/" + license.getValue("family") + "/OFL.txt",
                licenseDirectory.resolve(license.getValue("name")),
                license.getValue("size").toLong(),
                license.getValue("sha")
            )
        }
    }
}


val ganderViewerCommit = "73cf325435868cab1683d8de8cc1b89533e767b3"
val ganderHindiPatchCommit = "01afbbba5454860ae121b07d9b1cc3bdf184162e"
val ganderViewerAssets = layout.buildDirectory.dir("generated/assets/ganderViewer")
val ganderViewerFiles = listOf(
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/app.css", "output" to "viewer/app.css", "size" to "9951", "sha" to "b37458718aaf4ba2b1562dfa0afcddb98a623197"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/app.js", "output" to "viewer/app.js", "size" to "23310", "sha" to "cb1d7de66b578d66c5ceef7df69d67649dd5b62f"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/find.js", "output" to "viewer/find.js", "size" to "17380", "sha" to "530bc32f58c2a896addb86195dd2899b14172937"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/pptx.html", "output" to "viewer/pptx.html", "size" to "7319", "sha" to "80a2272423aafb310e4210cc533522503907ce09"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/pptx.js", "output" to "viewer/pptx.js", "size" to "29141", "sha" to "2374ed228760922f5437de2aee64fe1cf791ea56"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/symbol-fonts.js", "output" to "viewer/symbol-fonts.js", "size" to "8626", "sha" to "d39344196090081789eed4810d322038b6087751"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/xlsx.html", "output" to "viewer/xlsx.html", "size" to "13389", "sha" to "bf2b500194e754fabe0b8f55b17017f8efa7161c"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/xlsx.js", "output" to "viewer/xlsx.js", "size" to "75133", "sha" to "b87fe8739ca8178f824199c35976511eb3ed0524"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/xlsx-rules.js", "output" to "viewer/xlsx-rules.js", "size" to "24496", "sha" to "899e131c3250c688fe8a650817dc2cb6f3443072"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/xlsx-styles.js", "output" to "viewer/xlsx-styles.js", "size" to "70897", "sha" to "9adbf3ccbddeb943208d6e528852ca72c616afb4"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/xlsx-tables.js", "output" to "viewer/xlsx-tables.js", "size" to "8145", "sha" to "0741fd7364f89c4c5135bde8d9aa5e2e45087177"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/jquery.min.js", "output" to "viewer/lib/pptx/jquery.min.js", "size" to "95957", "sha" to "0f60b7bd0d9c395e1748cc962810d2dee9bb9fc2"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/jszip2.min.js", "output" to "viewer/lib/pptx/jszip2.min.js", "size" to "75490", "sha" to "767d8c11d272330741951ba78b0829a2ec83baf4"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/filereader.js", "output" to "viewer/lib/pptx/filereader.js", "size" to "14528", "sha" to "24bf8c6b7afc0776abd666b3072c03c8b85d61e7"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/d3.min.js", "output" to "viewer/lib/pptx/d3.min.js", "size" to "151661", "sha" to "5ae5160f2db1ecf7a0bb8aec9d8834db5204f730"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/nv.d3.min.js", "output" to "viewer/lib/pptx/nv.d3.min.js", "size" to "215800", "sha" to "415441b471b626c518f48d1b1f137abd34f6f1f6"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/nv.d3.min.css", "output" to "viewer/lib/pptx/nv.d3.min.css", "size" to "9345", "sha" to "7d63faf94d729ba1b01e5ad3f864be8536755689"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/pptxjs.css", "output" to "viewer/lib/pptx/pptxjs.css", "size" to "2584", "sha" to "a722685e05b8d38b16b10af5e91dea93d60171ed"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/pptxjs.js", "output" to "viewer/lib/pptx/pptxjs.js", "size" to "815945", "sha" to "11431453baf221fd90ce1754d23b53af133ddb9c"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/pptx/divs2slides.js", "output" to "viewer/lib/pptx/divs2slides.js", "size" to "37767", "sha" to "ee174d8192119ffaad021ae077d22a8ea9597f1b"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/viewer/lib/xlsx.full.min.js", "output" to "viewer/lib/xlsx.full.min.js", "size" to "951904", "sha" to "21471af69ef0e4cda1613c2702c54101b92f48d2"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "app/src/main/assets/licences.md", "output" to "viewer/licenses/THIRD_PARTY.md", "size" to "18155", "sha" to "97c2879d6c50797f9eef6cbdc6f5b01e219d06ef"),
    mapOf("repo" to "mokshablr/gander", "commit" to "73cf325435868cab1683d8de8cc1b89533e767b3", "path" to "LICENSE", "output" to "viewer/licenses/GANDER-MIT-LICENSE.txt", "size" to "1071", "sha" to "7d0697747e99d4b195d9613684f010ed2c7bc47d"),
    mapOf("repo" to "qmnb12/gander", "commit" to "01afbbba5454860ae121b07d9b1cc3bdf184162e", "path" to "app/src/main/assets/viewer/hindi-fonts.js", "output" to "viewer/hindi-fonts.js", "size" to "3255", "sha" to "879a883d8b8826912e94342b3df88aae02843536")
)

val downloadGanderViewerAssets = tasks.register("downloadGanderViewerAssets") {
    inputs.property("ganderViewerCommit", ganderViewerCommit)
    inputs.property("ganderHindiPatchCommit", ganderHindiPatchCommit)
    outputs.dir(ganderViewerAssets)
    doLast {
        val root = ganderViewerAssets.get().asFile
        root.mkdirs()
        fun gitBlobSha(bytes: ByteArray): String {
            val header = ("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(header)
            digest.update(bytes)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        ganderViewerFiles.forEach { asset ->
            val repoName = asset.getValue("repo")
            val commit = asset.getValue("commit")
            val path = asset.getValue("path")
            val output = asset.getValue("output")
            val expectedSize = asset.getValue("size").toLong()
            val expectedSha = asset.getValue("sha")
            val destination = root.resolve(output)
            var bytes: ByteArray? = null
            if (destination.isFile && destination.length() == expectedSize) {
                val existing = destination.readBytes()
                if (gitBlobSha(existing) == expectedSha) bytes = existing
            }
            if (bytes == null) {
                val url = URL("https://raw.githubusercontent.com/" + repoName + "/" + commit + "/" + path)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = true
                try {
                    connection.connect()
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw GradleException("Could not download viewer asset: " + path
                                + " (HTTP " + connection.responseCode + ")")
                    }
                    bytes = connection.inputStream.use { it.readBytes() }
                } finally { connection.disconnect() }
            }
            if (bytes!!.size.toLong() != expectedSize || gitBlobSha(bytes!!) != expectedSha) {
                throw GradleException("Viewer asset checksum mismatch: " + path)
            }
            var outputBytes = bytes!!
            if (output == "viewer/pptx.html" || output == "viewer/xlsx.html") {
                var html = String(outputBytes, Charsets.UTF_8)
                val styleLink = "<link rel=\"stylesheet\" href=\"app.css\">"
                val scriptLink = "<script src=\"find.js\"></script>"
                if (!html.contains(styleLink) || !html.contains(scriptLink)) {
                    throw GradleException("Unexpected upstream viewer HTML structure: " + path)
                }
                html = html.replace(styleLink, styleLink + "\n<link rel=\"stylesheet\" href=\"hindi-fonts.css\">")
                html = html.replace(scriptLink, scriptLink + "\n<script src=\"hindi-fonts.js\"></script>")
                outputBytes = html.toByteArray(Charsets.UTF_8)
            }
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
        versionCode = 36
        versionName = "0.9.6"
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
    dependsOn(downloadHindiFonts, downloadGanderViewerAssets)
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(project(":third_party:mj_pdf:AndroidPdfViewer:android-pdf-viewer"))
    implementation(project(":third_party:mj_pdf:PdfiumAndroid"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
