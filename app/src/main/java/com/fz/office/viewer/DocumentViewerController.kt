package com.fz.office.viewer

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.PrintDocumentAdapter
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.webkit.WebMessage
import android.webkit.WebMessagePort

/**
 * Format-specific rendering surface exposed to the shared FZ document controls.
 *
 * A renderer implements only the low-level operations it owns. Dialogs, toolbar
 * actions, share/print dispatch and common user behaviour stay in one controller.
 */
interface DocumentViewerSurface {
    val view: View
    val supportsFind: Boolean get() = false
    val supportsScrollNavigation: Boolean get() = false
    val supportsZoom: Boolean get() = true
    val supportsPageNavigation: Boolean get() = false
    val supportsPrint: Boolean get() = false
    val printLabel: String get() = "Print"

    fun find(query: String, forward: Boolean) {}
    fun preparePrint(onReady: (Boolean) -> Unit) { onReady(true) }
    fun dispose() {}
    fun clearFind() {}
    fun scrollBackward() {}
    fun scrollForward() {}
    fun scrollToStart() {}
    fun scrollToEnd() {}
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
        if (surface.supportsScrollNavigation) {
            actions.add("Scroll up" to { surface.scrollBackward() })
            actions.add("Scroll down" to { surface.scrollForward() })
            actions.add("Go to top" to { surface.scrollToStart() })
            actions.add("Go to bottom" to { surface.scrollToEnd() })
        }
        if (surface.supportsZoom) {
            actions.add("Zoom in" to { surface.zoomIn() })
            actions.add("Zoom out" to { surface.zoomOut() })
            actions.add("Fit to width" to { surface.fitToWidth() })
        }
        if (surface.supportsPageNavigation) actions.add("Go to page" to { showGoToPage() })
        if (surface.supportsPrint) actions.add(surface.printLabel to { printDocument() })
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
        dialog.setOnCancelListener { surface.clearFind() }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString().trim()
                if (query.isEmpty()) Toast.makeText(activity, "Enter text to find.", Toast.LENGTH_SHORT).show()
                else surface.find(query, true)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val query = input.text.toString().trim()
                if (query.isEmpty()) Toast.makeText(activity, "Enter text to find.", Toast.LENGTH_SHORT).show()
                else surface.find(query, false)
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
        surface.preparePrint { prepared ->
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (!prepared) {
                    Toast.makeText(activity, "This sheet could not be prepared for printing.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val adapter = surface.createPrintAdapter(documentName)
                if (adapter == null) {
                    Toast.makeText(activity, "Printing is not available for this document.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val manager = activity.getSystemService(Activity.PRINT_SERVICE) as? PrintManager
                if (manager == null) {
                    Toast.makeText(activity, "No print service is available on this device.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
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
                clipData = ClipData.newUri(activity.contentResolver, documentName, uri)
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
    private val pageCommand: ((Int) -> String)? = null,
    private val zoomInScript: String? = null,
    private val zoomOutScript: String? = null,
    private val fitZoomScript: String? = null,
    private val findBridgeOrigin: Uri? = null,
    private val onFindStatus: ((Int, Int, Boolean) -> Unit)? = null,
    private val fitScaleProvider: (() -> Float?)? = null,
    private val preparePrintScript: String? = null,
    override val printLabel: String = "Print"
) : DocumentViewerSurface {
    private var lastQuery = ""
    private var findPort: WebMessagePort? = null
    private var findBridgeConnecting = false
    private val pendingFindCommands = ArrayList<String>()

    override val supportsFind: Boolean = true
    override val supportsScrollNavigation: Boolean = true
    override val supportsZoom: Boolean = true
    override val supportsPageNavigation: Boolean = pageCountScript != null && pageCommand != null
    override val supportsPrint: Boolean = true

    /** Gander Find searches virtualized workbook rows and every slide, not just visible DOM. */
    fun connectFindBridge() {
        val origin = findBridgeOrigin ?: return
        if (findPort != null || findBridgeConnecting) return
        findBridgeConnecting = true
        val channel = try { view.createWebMessageChannel() } catch (_: Exception) {
            findBridgeConnecting = false
            return
        }
        val appPort = channel[1]
        appPort.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
            override fun onMessage(port: WebMessagePort, message: WebMessage) {
                val fields = message.data.orEmpty().trim().split(Regex("\\s+"))
                val active = fields.getOrNull(0)?.toIntOrNull() ?: return
                val total = fields.getOrNull(1)?.toIntOrNull() ?: return
                val ready = fields.getOrNull(2)?.toIntOrNull() == 1
                view.post { onFindStatus?.invoke(active, total, ready) }
            }
        })
        findPort = appPort
        try {
            view.postWebMessage(WebMessage("fz-find-channel", arrayOf(channel[0])), origin)
            findBridgeConnecting = false
            val queued = pendingFindCommands.toList()
            pendingFindCommands.clear()
            queued.forEach(::postFindCommand)
        } catch (_: Exception) {
            findPort = null
            findBridgeConnecting = false
            runCatching { channel[0].close() }
            runCatching { channel[1].close() }
        }
    }

    private fun postFindCommand(command: String) {
        val port = findPort
        if (port == null) pendingFindCommands.add(command)
        else try { port.postMessage(WebMessage(command)) }
        catch (_: Exception) { pendingFindCommands.add(command) }
    }

    override fun find(query: String, forward: Boolean) {
        val value = query.trim()
        if (value.isEmpty()) return
        if (findBridgeOrigin != null) {
            if (value != lastQuery) {
                lastQuery = value
                postFindCommand("q" + value)
                if (!forward) postFindCommand("p")
            } else postFindCommand(if (forward) "n" else "p")
            return
        }
        if (value != lastQuery) {
            lastQuery = value
            view.findAllAsync(value)
            view.findNext(forward)
        } else view.findNext(forward)
    }

    override fun clearFind() {
        lastQuery = ""
        if (findBridgeOrigin != null) {
            pendingFindCommands.clear()
            postFindCommand("c")
        } else view.clearMatches()
    }

    override fun scrollBackward() { view.pageUp(true) }
    override fun scrollForward() { view.pageDown(true) }
    override fun scrollToStart() {
        view.evaluateJavascript("window.scrollTo(window.scrollX||0,0);", null)
    }
    override fun scrollToEnd() {
        view.evaluateJavascript(
            "window.scrollTo(window.scrollX||0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));",
            null
        )
    }
    override fun zoomIn() {
        val script = zoomInScript
        if (script.isNullOrBlank()) view.zoomIn() else view.evaluateJavascript(script, null)
    }
    override fun zoomOut() {
        val script = zoomOutScript
        if (script.isNullOrBlank()) view.zoomOut() else view.evaluateJavascript(script, null)
    }
    override fun fitToWidth() {
        val script = fitZoomScript
        if (!script.isNullOrBlank()) {
            view.evaluateJavascript(script, null)
            return
        }
        val targetScale = fitScaleProvider?.invoke()
        if (targetScale != null && targetScale > 0f) {
            val currentScale = view.scale
            if (currentScale > 0f) view.zoomBy(targetScale / currentScale)
            return
        }
        if (fitScaleProvider != null) return
        val currentScale = view.scale
        if (currentScale > 0f) view.zoomBy(1f / currentScale)
    }
    override fun pageCount(onResult: (Int?) -> Unit) {
        val script = pageCountScript
        if (script.isNullOrBlank()) { onResult(null); return }
        view.evaluateJavascript(script) { raw -> onResult(raw?.trim()?.trim('"')?.toIntOrNull()) }
    }
    override fun goToPage(index: Int) {
        pageCommand?.let { view.evaluateJavascript(it(index.coerceAtLeast(0)), null) }
    }
    override fun preparePrint(onReady: (Boolean) -> Unit) {
        val script = preparePrintScript
        if (script.isNullOrBlank()) onReady(true)
        else view.evaluateJavascript(script) { raw -> onReady(raw?.trim() == "true") }
    }
    override fun createPrintAdapter(jobName: String): PrintDocumentAdapter =
        view.createPrintDocumentAdapter(jobName)
    override fun dispose() {
        findPort?.let { runCatching { it.close() } }
        findPort = null
        pendingFindCommands.clear()
    }
}
