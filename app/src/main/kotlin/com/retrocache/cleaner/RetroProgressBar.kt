package com.retrocache.cleaner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * RetroProgressBar — Windows NT4-style segmented progress bar.
 *
 * Renders a series of solid blue blocks with narrow gaps on a black background,
 * exactly as seen in Windows 95/NT4 progress dialogs.
 *
 * Usage in XML:
 *   <com.retrocache.cleaner.RetroProgressBar
 *       android:id="@+id/retro_progress_bar"
 *       android:layout_width="match_parent"
 *       android:layout_height="18dp" />
 *
 * Control progress via [setProgress] (0..100).
 */
class RetroProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0   // current value: 0..100

    // ── Paints ────────────────────────────────────────────────────────────
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val blockPaint = Paint().apply {
        color = Color.parseColor("#0000AA")   // Windows NT4 progress blue
        style = Paint.Style.FILL
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Set the fill level (0..100). Triggers a redraw. */
    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        invalidate()
    }

    fun getProgress(): Int = progress

    // ── Drawing ───────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Black track
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        if (progress <= 0) return

        val density = resources.displayMetrics.density

        // Block dimensions (classic 12 dp wide, 2 dp gap)
        val blockW = 12f * density
        val gapW   =  2f * density
        val stepW  = blockW + gapW

        // Vertical margins (1 px top and bottom so the blocks don't touch the border)
        val vMargin = 1f * density

        // How far right the fill should extend
        val fillRight = w * progress / 100f

        var x = 0f
        while (x < fillRight) {
            val right = minOf(x + blockW, fillRight)
            canvas.drawRect(x, vMargin, right, h - vMargin, blockPaint)
            x += stepW
        }
    }
}
