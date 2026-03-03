package com.example.csvgraph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class LineGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 32f
    }

    private var values: List<Float> = emptyList()

    fun setValues(newValues: List<Float>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 80f
        val top = 40f
        val right = width - 30f
        val bottom = height - 80f

        if (width <= 0 || height <= 0) return

        // Axis
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        if (values.isEmpty()) {
            canvas.drawText("CSV를 불러오면 그래프가 표시됩니다.", left, height / 2f, textPaint)
            return
        }

        val minY = values.minOrNull() ?: 0f
        val maxY = values.maxOrNull() ?: 0f
        val range = max(1f, maxY - minY)
        val stepX = if (values.size == 1) 0f else (right - left) / (values.size - 1)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = left + i * stepX
            val normalized = (v - minY) / range
            val y = bottom - normalized * (bottom - top)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, linePaint)
        canvas.drawText("min: ${"%.2f".format(minY)}", left, height - 20f, textPaint)
        canvas.drawText("max: ${"%.2f".format(maxY)}", min(right - 220f, left + 260f), top + 30f, textPaint)
    }
}
