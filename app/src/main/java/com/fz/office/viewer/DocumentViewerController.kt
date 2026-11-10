package com.fz.office.viewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.PrintDocumentAdapter
import android.view.View
import android.widget.EditText
import android.widget.Toast

/**
 * Format-specific rendering surface exposed to the shared FZ document controls.
 *
 * A renderer implements only the low-level operations it owns. Dialogs, toolbar
 * actions, share/print dispatch and common user behaviour stay in one controller.
 */
interface DocumentViewerSurface {
    val view: View
    val supportsFind: Boolean get() = false
    val supportsZoom: Boolean get() = true
    val supportsPageNavigation: Boolean get() = false
    val supportsPrint: Boolean get() = false

    fun find(query: String, forward: Boolean) {}
    fun clearFind() {}
    fun zoomIn() {}
    fun zoomOut() {}
    fun fitToWidth() {}
    fun pageCount(onResult: (Int?) -> Unit) { onResult(null) }
    fun goToPage(index: Int) {}
    fun createPrintAdapter(jobName: String): PrintDocumentAdapter? = null
}

/**
 * One shared toolbar/controller for DOCX, TXT, XLSX, PPTX, PDF and future formats.
 *
 * Document-specific code supplies a surface adapter; it does not reimplement
 * Find, Zoom, Fit, Go to Page, Print or Share dialogs.
 */
class DocumentViewerController(
    private val activity: Activity,
    dashboard: ReaderDashboard,
    private val surface: DocumentViewerSurface,
    private val documentUri: Uri?,
    private val documentName: String,
    private val mimeType: String?
) {
    init {
        dashboard.addToolbarAction("Tools", "Common document tools") {
            showTools()
        }
    }

    private fun showTools() {
        val actions = ArrayList<Pair<String, () -> Unit>>()
        if (surface.supportsFind) actions.add("Find" to { showFind() })
        if (surface.supportsZoom) {
            actions.add("Zoom in" to { surface.zoomIn() })
            actions.add("Zoom out" to { surface.zoomOut() })
            actions.add("Fit to width" to { surface.fitToWidth() })
        }
        if (surface.supportsPageNavigation) actions.add("Go to page" to { showGoToPage() })
        if (surface.supportsPrint) actions.add("Print" to { printDocument() })
        if (documentUri != null) actions.add("Share" to { shareDocument() })

        val labels = actions.map { it.first }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Document tools")
            .setItems(labels) { _, selected -> actions.getOrNull(selected)?.second?.invoke() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showFind() {
        val input = EditText(activity).apply {
            isSingleLine = true
            hint = "Find text in this document"
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Find in document")
            .setView(input)
            .setNegativeButton("Close") { _, _ -> surface.clearFind() }
            .setNeutralButton("Previous", null)
            .setPositiveButton("Next", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                surface.find(input.text.toString().trim(), true)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                surface.find(input.text.toString().trim(), false)
            }
        }
        dialog.show()
    }

    private fun showGoToPage() {
        surface.pageCount { count ->
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (count == null || count < 1) {
                    Toast.makeText(activity, "Page information is not available yet.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val input = EditText(activity).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "Page number (1-$count)"
                    setText("1")
                    setSelection(text.length)
                }
                AlertDialog.Builder(activity)
                    .setTitle("Go to page")
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Go") { _, _ ->
                        val requested = input.text.toString().toIntOrNull()
                        if (requested == null || requested !in 1..count) {
                            Toast.makeText(
                                activity,
                                "Enter a page from 1 to $count.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            surface.goToPage(requested - 1)
                        }
                    }
                    .show()
            }
        }
    }

    private fun printDocument() {
        val adapter = surface.createPrintAdapter(documentName)
        if (adapter == null) {
            Toast.makeText(activity, "Printing is not available for this document.", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = activity.getSystemService(Activity.PRINT_SERVICE) as? PrintManager
        if (manager == null) {
            Toast.makeText(activity, "No print service is available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            manager.print(documentName, adapter, PrintAttributes.Builder().build())
        } catch (error: Exception) {
            Toast.makeText(
                activity,
                error.message?.takeIf { it.isNotBlank() } ?: "Could not start printing.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareDocument() {
        val uri = documentUri ?: return
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = mimeType?.takeIf { it.isNotBlank() }
                    ?: activity.contentResolver.getType(uri)
                    ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(share, "Share document"))
        } catch (_: Exception) {
            Toast.makeText(activity, "This document cannot be shared right now.", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Shared adapter for Android WebView-backed FZ readers (DOCX/TXT/XLSX/PPTX).
 * The page-count/navigation bridge is optional because not every HTML format
 * represents its content as paginated physical pages.
 */
class WebViewDocumentSurface(
    override val view: android.webkit.WebView,
    private val pageCountScript: String? = null,
    private val pageCommand: ((Int) -> String)? = null
) : DocumentViewerSurface {
    private var lastQuery = ""

    override val supportsFind: Boolean = true
    override val supportsZoom: Boolean = true
    override val supportsPageNavigation: Boolean =
        pageCountScript != null && pageCommand != null
    override val supportsPrint: Boolean = true

    override fun find(query: String, forward: Boolean) {
        val value = query.trim()
        if (value.isEmpty()) return
        if (value != lastQuery) {
            lastQuery = value
            view.findAllAsync(value)
            view.findNext(forward)
        } else {
            view.findNext(forward)
        }
    }

    override fun clearFind() {
        lastQuery = ""
        view.clearMatches()
    }

    override fun zoomIn() {
        view.zoomIn()
    }

    override fun zoomOut() {
        view.zoomOut()
    }

    override fun fitToWidth() {
        val currentScale = view.scale
        if (currentScale > 0f) view.zoomBy(1f / currentScale)
    }

    override fun pageCount(onResult: (Int?) -> Unit) {
        val script = pageCountScript
        if (script.isNullOrBlank()) {
            onResult(null)
            return
        }
        view.evaluateJavascript(script) { raw ->
            onResult(raw?.trim()?.trim('"')?.toIntOrNull())
        }
    }

    override fun goToPage(index: Int) {
        val command = pageCommand ?: return
        view.evaluateJavascript(command(index.coerceAtLeast(0)), null)
    }

    override fun createPrintAdapter(jobName: String): PrintDocumentAdapter =
        view.createPrintDocumentAdapter(jobName)
}
