package com.fz.office.engine

import java.io.File

/**
 * Narrow JNI boundary for the FZ Rust document engine.
 *
 * A missing native library is non-fatal during staged rollout: current Kotlin
 * readers remain available and native-only enhancements fall back cleanly.
 */
object FzNative {
    private val loaded: Boolean = try {
        System.loadLibrary("fzcore")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    val isAvailable: Boolean
        get() = loaded

    fun engineVersion(): String? =
        if (loaded) runCatching { nativeVersion() }.getOrNull() else null

    fun detectFormat(fileName: String, header: ByteArray): String? =
        if (loaded) runCatching { nativeDetectFormat(fileName, header) }.getOrNull() else null

    fun inspectPackage(file: File): String? =
        if (loaded && file.isFile) {
            runCatching { nativeInspectPackage(file.absolutePath) }.getOrNull()
        } else {
            null
        }

    @JvmStatic private external fun nativeVersion(): String
    @JvmStatic private external fun nativeDetectFormat(fileName: String, header: ByteArray): String
    @JvmStatic private external fun nativeInspectPackage(filePath: String): String
}
