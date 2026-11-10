import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}


// MJ PDF 3.1 source pin supplied for FZ Office integration.
val mjPdfRevision = "286277c6a85a4068cbbfbd18c93a742f0b2087ed"
val mjPdfProjectPath = "mudlej_android%2Fmj_pdf_reader"
val mjPdfRoot = File(settingsDir, "third_party/mj_pdf")
val mjPdfRevisionFile = File(mjPdfRoot, "SOURCE_REVISION")

fun downloadMjPdfSourceArchive(destination: File) {
    val archiveUrl = URL(
        "https://gitlab.com/api/v4/projects/" + mjPdfProjectPath +
            "/repository/archive.zip?sha=" + mjPdfRevision
    )
    val connection = archiveUrl.openConnection() as HttpURLConnection
    connection.connectTimeout = 25000
    connection.readTimeout = 180000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "FZ-Office-MJ-PDF-bootstrap")
    val wantedRoots = setOf("AndroidPdfViewer", "PdfiumAndroid", "build_dependencies")
    val requiredFiles = setOf(
        "AndroidPdfViewer/LICENSE",
        "PdfiumAndroid/LICENSE",
        "build_dependencies/values.py"
    )
    val extracted = HashSet<String>()
    try {
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw GradleException(
                "Could not download pinned MJ PDF source archive (HTTP " +
                    connection.responseCode + "). Check internet access and retry Gradle sync."
            )
        }
        ZipInputStream(connection.inputStream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                val parts = normalizedName.split('/').filter { it.isNotBlank() }
                val rootIndex = parts.indexOfFirst { it in wantedRoots }
                val relative = if (rootIndex >= 0) {
                    parts.drop(rootIndex).joinToString("/")
                } else if (parts.lastOrNull() == "build_dependencies.py") {
                    "build_dependencies.py"
                } else {
                    ""
                }
                if (!entry.isDirectory && relative.isNotBlank()) {
                    val target = File(destination, relative).canonicalFile
                    val root = destination.canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) {
                        throw GradleException("Unsafe path in pinned MJ PDF source archive.")
                    }
                    target.parentFile.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                    extracted.add(relative)
                }
                zip.closeEntry()
            }
        }
    } finally {
        connection.disconnect()
    }
    val missing = requiredFiles.filterNot { it in extracted }
    if (missing.isNotEmpty() || "build_dependencies.py" !in extracted) {
        throw GradleException(
            "Pinned MJ PDF archive is incomplete. Missing: " +
                (missing + if ("build_dependencies.py" !in extracted) listOf("build_dependencies.py") else emptyList())
                    .joinToString(", ")
        )
    }
}

fun patchMjPdfModuleBuildFiles(root: File) {
    val pdfiumBuild = File(root, "PdfiumAndroid/build.gradle")
    var pdfiumText = pdfiumBuild.readText(Charsets.UTF_8)
    pdfiumText = pdfiumText.replace(
        "\nrepositories {\n    google()\n    mavenCentral()\n}\n",
        "\n"
    )
    val nativeTaskMarker = "buildMjPdfNativeDependencies"
    if (!pdfiumText.contains(nativeTaskMarker)) {
        pdfiumText += """

// FZ Office integration: build the pinned PDFium JNI libraries before packaging.
def mjPdfRoot = rootProject.file('third_party/mj_pdf')
def mjPdfAbis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
def mjPdfNativeNames = ['libmodpdfium.so', 'libmodft2.so', 'libmodpng.so', 'libc++_shared.so', 'libjniPdfium.so']
def mjPdfNativeOutputs = mjPdfAbis.collectMany { abi ->
    mjPdfNativeNames.collect { name -> new File(projectDir, 'src/main/libs/' + abi + '/' + name) }
}
def buildMjPdfNativeDependencies = tasks.register('buildMjPdfNativeDependencies', Exec) {
    group = 'build setup'
    description = 'Builds the pinned MJ PDF PDFium and JNI libraries for supported Android ABIs.'
    workingDir(mjPdfRoot)
    commandLine('python3', 'build_dependencies.py', '--pdfium', 'prebuilt')
    outputs.files(mjPdfNativeOutputs)
    onlyIf { mjPdfNativeOutputs.any { !it.isFile() || it.length() == 0L } }
    doFirst {
        def ndk = System.getenv('ANDROID_NDK')
        if (!ndk) {
            def localProperties = new Properties()
            def localPropertiesFile = rootProject.file('local.properties')
            if (localPropertiesFile.isFile()) {
                localPropertiesFile.withInputStream { localProperties.load(it) }
            }
            def sdkRoot = System.getenv('ANDROID_SDK_ROOT') ?:
                System.getenv('ANDROID_HOME') ?: localProperties.getProperty('sdk.dir')
            if (sdkRoot) {
                def candidates = new File(sdkRoot, 'ndk').listFiles()?.findAll {
                    new File(it, 'ndk-build').isFile()
                } ?: []
                if (!candidates.isEmpty()) {
                    ndk = candidates.sort { a, b -> a.name <=> b.name }.last().absolutePath
                }
            }
        }
        if (!ndk || !new File(ndk, 'ndk-build').isFile()) {
            throw new GradleException(
                'MJ PDF requires Android NDK. Install NDK 30.0.14904198 (or a compatible side-by-side NDK) ' +
                'and set ANDROID_NDK, or install it in the Android SDK used by this project.'
            )
        }
        environment('ANDROID_NDK', ndk)
    }
}
tasks.named('preBuild').configure {
    dependsOn(buildMjPdfNativeDependencies)
}
"""
    }
    pdfiumBuild.writeText(pdfiumText, Charsets.UTF_8)

    val applicationMk = File(root, "PdfiumAndroid/src/main/jni/Application.mk")
    val appMkText = applicationMk.readText(Charsets.UTF_8)
    if (appMkText.contains("APP_PLATFORM = android-19")) {
        applicationMk.writeText(
            appMkText.replace(
                "APP_PLATFORM = android-19",
                "# FZ Office modification: match the host app's minSdk 24.\nAPP_PLATFORM = android-24"
            ),
            Charsets.UTF_8
        )
    }

    val viewerBuild = File(root, "AndroidPdfViewer/android-pdf-viewer/build.gradle")
    val viewerText = viewerBuild.readText(Charsets.UTF_8)
    val upstreamDependency = "implementation project(path: ':PdfiumAndroid')"
    val fzDependency = "implementation project(path: ':third_party:mj_pdf:PdfiumAndroid')"
    if (viewerText.contains(upstreamDependency)) {
        viewerBuild.writeText(viewerText.replace(upstreamDependency, fzDependency), Charsets.UTF_8)
    } else if (!viewerText.contains(fzDependency)) {
        throw GradleException("MJ PDF AndroidPdfViewer project dependency could not be patched safely.")
    }
}

val mjPdfAlreadyPrepared =
    mjPdfRevisionFile.isFile &&
        mjPdfRevisionFile.readText(Charsets.UTF_8).trim() == mjPdfRevision &&
        File(mjPdfRoot, "AndroidPdfViewer/android-pdf-viewer/build.gradle").isFile &&
        File(mjPdfRoot, "PdfiumAndroid/build.gradle").isFile &&
        File(mjPdfRoot, "build_dependencies.py").isFile &&
        File(mjPdfRoot, "build_dependencies/values.py").isFile

if (!mjPdfAlreadyPrepared) {
    val staging = File(settingsDir, ".gradle/mj-pdf-bootstrap")
    if (staging.exists()) staging.deleteRecursively()
    staging.mkdirs()
    downloadMjPdfSourceArchive(staging)
    patchMjPdfModuleBuildFiles(staging)
    File(staging, "SOURCE_REVISION").writeText(mjPdfRevision + "\n", Charsets.UTF_8)

    if (mjPdfRoot.exists()) mjPdfRoot.deleteRecursively()
    mjPdfRoot.parentFile.mkdirs()
    if (!staging.renameTo(mjPdfRoot)) {
        staging.copyRecursively(mjPdfRoot, overwrite = true)
        staging.deleteRecursively()
    }
}

include(":app")
include(":third_party:mj_pdf:PdfiumAndroid")
include(":third_party:mj_pdf:AndroidPdfViewer:android-pdf-viewer")
