package com.usagecompanion.claude.wear

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt

class WearMainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val snapshot = WearSnapshotStore(this).read()
        if (snapshot.hasUsage && snapshot.watchStyle == WearSnapshotStore.STYLE_RING) {
            setContentView(FitRingView(this, snapshot))
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 14, 20, 14)
            setBackgroundColor("#000000".toColorInt())
        }

        if (!snapshot.hasUsage) {
            root.addView(text("Set up on phone", 15, "#FFFFFF", bold = true))
            root.addView(text("Open phone app", 13, COLOR_TEXT_DIM, bold = false))
            root.addView(text("OAuth token required", 12, COLOR_TEXT_FAINT, bold = false))
        } else {
            when (snapshot.watchStyle) {
                WearSnapshotStore.STYLE_BAR -> renderBar(root, snapshot)
                WearSnapshotStore.STYLE_COMPACT -> renderCompact(root, snapshot)
                else -> renderRing(root, snapshot)
            }
        }

        setContentView(root)
    }

    private fun renderRing(root: LinearLayout, snapshot: WearSnapshot) {
        val accent = usageColor(snapshot)
        root.addView(text("Claude Usage", 15, "#FFFFFF", bold = true))
        root.addView(text("5h ${snapshot.fiveHourPercent}% used", 22, accent, bold = true))
        root.addView(text("7d ${snapshot.sevenDayPercent}% used", 14, COLOR_TEXT_DIM, bold = false))
        root.addView(text("reset ${snapshot.fiveHourResetLabel} / ${snapshot.sevenDayResetLabel}", 12, COLOR_TEXT_FAINT, bold = false))
    }

    private fun renderBar(root: LinearLayout, snapshot: WearSnapshot) {
        val accent = usageColor(snapshot)
        root.addView(text("Claude Usage", 14, COLOR_TEXT_DIM, bold = false))
        root.addView(text("5h ${snapshot.fiveHourPercent}% used", 28, accent, bold = true))
        root.addView(
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = snapshot.fiveHourPercent
                progressTintList = android.content.res.ColorStateList.valueOf(accent.toColorInt())
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf("#3C4043".toColorInt())
                layoutParams = LinearLayout.LayoutParams(132, 10).apply {
                    topMargin = 6
                    bottomMargin = 8
                }
            },
        )
        root.addView(text("7d ${snapshot.sevenDayPercent}% used", 14, COLOR_TEXT_DIM, bold = true))
        root.addView(text("reset ${snapshot.fiveHourResetLabel} / ${snapshot.sevenDayResetLabel}", 12, COLOR_TEXT_FAINT, bold = false))
    }

    private fun renderCompact(root: LinearLayout, snapshot: WearSnapshot) {
        val accent = usageColor(snapshot)
        root.addView(text("5h ${snapshot.fiveHourPercent}%", 26, accent, bold = true))
        root.addView(text("7d ${snapshot.sevenDayPercent}%", 20, COLOR_TEXT_DIM, bold = true))
        root.addView(text("${snapshot.fiveHourResetLabel} / ${snapshot.sevenDayResetLabel}", 13, COLOR_TEXT_FAINT, bold = false))
    }

    private fun text(value: String, sizeSp: Int, color: String, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(color.toColorInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    private class FitRingView(
        context: Context,
        private val snapshot: WearSnapshot,
    ) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.BLACK)

            val centerX = width / 2f
            val centerY = height / 2f
            val minSize = minOf(width, height).toFloat()
            val accent = usageColor(snapshot)
            val stroke = minSize * 0.045f
            val outer = RectF(
                centerX - minSize * 0.37f,
                centerY - minSize * 0.37f,
                centerX + minSize * 0.37f,
                centerY + minSize * 0.37f,
            )

            drawRing(canvas, outer, stroke, COLOR_TRACK, 100)
            drawRing(canvas, outer, stroke, accent, snapshot.fiveHourPercent)

            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textSize = minSize * 0.052f
            textPaint.color = Color.WHITE
            canvas.drawText("Claude Usage", centerX, centerY - minSize * 0.18f, textPaint)

            textPaint.typeface = android.graphics.Typeface.DEFAULT
            textPaint.textSize = minSize * 0.03f
            textPaint.color = Color.parseColor(CLAUDE_ORANGE)
            canvas.drawText(snapshot.planLabel, centerX, centerY - minSize * 0.125f, textPaint)

            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textSize = minSize * 0.145f
            textPaint.color = Color.WHITE
            canvas.drawText("${snapshot.fiveHourPercent}%", centerX, centerY + minSize * 0.035f, textPaint)

            textPaint.typeface = android.graphics.Typeface.DEFAULT
            textPaint.textSize = minSize * 0.037f
            textPaint.color = Color.parseColor(COLOR_TEXT_DIM)
            canvas.drawText("5h used · reset ${snapshot.fiveHourResetLabel}", centerX, centerY + minSize * 0.14f, textPaint)

            textPaint.textSize = minSize * 0.032f
            textPaint.color = Color.parseColor(COLOR_TEXT_FAINT)
            canvas.drawText("7d ${snapshot.sevenDayPercent}% · reset ${snapshot.sevenDayResetLabel}", centerX, centerY + minSize * 0.195f, textPaint)
        }

        private fun drawRing(canvas: Canvas, rect: RectF, stroke: Float, color: String, percent: Int) {
            ringPaint.strokeWidth = stroke
            ringPaint.color = Color.parseColor(color)
            canvas.drawArc(rect, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, ringPaint)
        }

    }

    private companion object {
        const val CLAUDE_ORANGE = "#D97250"
        const val COLOR_YELLOW = "#FACC15"
        const val COLOR_DEEP_ORANGE = "#F97316"
        const val COLOR_RED = "#EF4444"
        const val COLOR_TRACK = "#2B2B2B"
        const val COLOR_TEXT_DIM = "#C9C3BF"
        const val COLOR_TEXT_FAINT = "#8F8782"

        fun usageColor(snapshot: WearSnapshot): String =
            when {
                snapshot.fiveHourPercent > 100 || snapshot.sevenDayPercent > 100 -> COLOR_RED
                snapshot.fiveHourPercent >= 90 || snapshot.sevenDayPercent >= 90 -> COLOR_DEEP_ORANGE
                snapshot.fiveHourPercent >= 70 || snapshot.sevenDayPercent >= 70 -> COLOR_YELLOW
                else -> CLAUDE_ORANGE
            }
    }
}
