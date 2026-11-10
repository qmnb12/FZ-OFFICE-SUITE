package com.fz.office.viewer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Shared reader shell for every document format.
 * Rendering stays format-specific; navigation chrome and touch-safe layout are shared.
 */
class ReaderDashboard(
    private val activity: Activity,
    documentName: String,
    subtitle: String,
    onBack: () -> Unit
) {
    companion object {
        private const val BACKGROUND = 0xfff1f4f2.toInt()
        private const val SURFACE = Color.WHITE
        private const val INK = 0xff18352d.toInt()
        private const val MUTED = 0xff60766d.toInt()
        private const val ACCENT = 0xff176b5b.toInt()
    }

    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BACKGROUND)
    }

    val toolbar = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setBackgroundColor(SURFACE)
    }

    val titleView = TextView(activity).apply {
        text = documentName
        textSize = 14f
        setTextColor(INK)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
    }

    val subtitleView = TextView(activity).apply {
        text = subtitle
        textSize = 9f
        setTextColor(ACCENT)
        letterSpacing = 0.08f
    }

    val progress = ProgressBar(
        activity, null, android.R.attr.progressBarStyleHorizontal
    ).apply {
        isIndeterminate = true
        visibility = View.GONE
    }

    val content = FrameLayout(activity).apply {
        setBackgroundColor(BACKGROUND)
        clipToPadding = false
    }

    val controls = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(7), dp(5), dp(7), dp(5))
        setBackgroundColor(SURFACE)
    }

    val status = TextView(activity).apply {
        text = "Ready · Original file is never changed"
        textSize = 10f
        setTextColor(MUTED)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(7), dp(13), dp(7))
        setBackgroundColor(SURFACE)
        maxLines = 2
    }

    init {
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)))

        val back = TextView(activity).apply {
            text = "‹"
            textSize = 28f
            setTextColor(INK)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { onBack() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(40), dp(44)))

        val titleColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(5), 0)
        }
        toolbar.addView(titleColumn, LinearLayout.LayoutParams(0, -1, 1f))
        titleColumn.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        titleColumn.addView(subtitleView, LinearLayout.LayoutParams(-1, -2))

        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
    }

    fun addToolbarAction(label: String, description: String = label, onClick: () -> Unit): TextView {
        val button = TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(INK)
            gravity = Gravity.CENTER
            isSingleLine = true
            isClickable = true
            isFocusable = true
            contentDescription = description
            background = rounded(0xffeef3f0.toInt(), 11)
            setOnClickListener { onClick() }
        }
        toolbar.addView(button, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            leftMargin = dp(4)
        })
        return button
    }

    fun addControl(
        label: String,
        weight: Float = 1f,
        onClick: () -> Unit
    ): TextView {
        val button = TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(INK)
            gravity = Gravity.CENTER
            isSingleLine = true
            isClickable = true
            isFocusable = true
            background = rounded(0xffeef3f0.toInt(), 10)
            setOnClickListener { onClick() }
        }
        controls.addView(button, LinearLayout.LayoutParams(0, dp(40), weight).apply {
            leftMargin = dp(3)
            rightMargin = dp(3)
        })
        return button
    }

    fun setDocumentView(view: View) {
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun setStatus(message: CharSequence) {
        status.text = message
    }

    fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}
