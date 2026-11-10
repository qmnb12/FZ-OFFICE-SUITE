package com.fz.office.viewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream
import java.io.IOException

/**
 * Print adapter for a PDF already stored by the document provider.
 *
 * It streams the original, unchanged PDF bytes to Android's print spooler,
 * avoiding rasterizing pages or creating a second PDF rendering pipeline.
 */
class UriPdfPrintAdapter(
    private val context: Context,
    private val documentUri: Uri,
    private val jobName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("The print destination is unavailable.")
            return
        }

        try {
            val input = context.contentResolver.openInputStream(documentUri)
                ?: throw IOException("The original PDF cannot be read.")
            input.use { source ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (cancellationSignal?.isCanceled == true) {
                            throw OperationCanceledException()
                        }
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (_: OperationCanceledException) {
            callback?.onWriteCancelled()
        } catch (error: Exception) {
            callback?.onWriteFailed(
                error.message?.takeIf { it.isNotBlank() } ?: "Could not print this PDF."
            )
        }
    }
}
