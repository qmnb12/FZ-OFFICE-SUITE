package com.fz.office.engine

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * FZ Office's Kotlin-owned document signature detector.
 *
 * This class intentionally uses only the Java/Kotlin runtime. Format renderers
 * are being replaced in separate, verified stages; this core does not delegate
 * file identification to a third-party office application.
 */
object FzCore {
    private val PDF_SIGNATURE = "%PDF-".toByteArray(StandardCharsets.US_ASCII)
    private val OLE_SIGNATURE = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )
    private val ZIP_SIGNATURES = listOf(
        byteArrayOf(0x50, 0x4B, 0x03, 0x04),
        byteArrayOf(0x50, 0x4B, 0x05, 0x06),
        byteArrayOf(0x50, 0x4B, 0x07, 0x08)
    )

    /**
     * Detects a document format from its binary signature and filename.
     *
     * A filename suffix is never enough to classify a binary PDF or Office
     * package: the corresponding container signature must also be present.
     */
    fun detectFormat(fileName: String, header: ByteArray): String {
        if (header.startsWithBytes(PDF_SIGNATURE)) return "PDF"

        val extension = fileName
            .substringAfterLast('.', "")
            .trim()
            .lowercase(Locale.ROOT)

        if (header.startsWithBytes(OLE_SIGNATURE)) {
            return when (extension) {
                "doc", "dot", "xls", "ppt", "pps" -> "LEGACY_OFFICE"
                else -> "OLE_CONTAINER"
            }
        }

        if (ZIP_SIGNATURES.any { signature -> header.startsWithBytes(signature) }) {
            return when (extension) {
                "docx", "dotx", "docm" -> "DOCX"
                "xlsx", "xlsm", "xlsb", "xltx" -> "XLSX"
                "pptx", "pptm", "ppsx", "potx" -> "PPTX"
                "odt", "ods", "odp" -> "OPEN_DOCUMENT"
                else -> "ZIP"
            }
        }

        return when (extension) {
            "txt" -> "TXT"
            "csv" -> "CSV"
            else -> "UNKNOWN"
        }
    }

    private fun ByteArray.startsWithBytes(signature: ByteArray): Boolean {
        if (size < signature.size) return false
        for (index in signature.indices) {
            if (this[index] != signature[index]) return false
        }
        return true
    }
}
