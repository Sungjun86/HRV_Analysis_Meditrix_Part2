package com.example.csvgraph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max

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

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val overlayLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 28f
    }

    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#607D8B")
        textSize = 26f
    }

    private var values: List<Float> = emptyList()
    private var overlayValues: List<Float> = emptyList()
    private var xStartSec: Float = 0f
    private var xStepSec: Float = 1f

    private var fullXMin = 0f
    private var fullXMax = 1f
    private var fullYMin = 0f
    private var fullYMax = 1f

    private var viewXMin = 0f
    private var viewXMax = 1f
    private var viewYMin = 0f
    private var viewYMax = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (values.isEmpty()) return false

                val scale = detector.scaleFactor.coerceIn(0.8f, 1.25f)
                val plot = getPlotBounds()

                val focusRatioX = ((detector.focusX - plot.left) / plot.width).coerceIn(0f, 1f)
                val focusRatioY = ((detector.focusY - plot.top) / plot.height).coerceIn(0f, 1f)

                val focusDataX = viewXMin + focusRatioX * (viewXMax - viewXMin)
                val focusDataY = viewYMax - focusRatioY * (viewYMax - viewYMin)

                val minXRange = max(1f, (fullXMax - fullXMin) / 50f)
                val minYRange = max(0.1f, (fullYMax - fullYMin) / 50f)

                val newXRange = ((viewXMax - viewXMin) / scale).coerceIn(minXRange, fullXMax - fullXMin)
                val newYRange = ((viewYMax - viewYMin) / scale).coerceIn(minYRange, fullYMax - fullYMin)

                viewXMin = focusDataX - focusRatioX * newXRange
                viewXMax = viewXMin + newXRange

                viewYMax = focusDataY + focusRatioY * newYRange
                viewYMin = viewYMax - newYRange

                clampViewToBounds()
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (values.isEmpty() || scaleDetector.isInProgress) return false

                val plot = getPlotBounds()
                if (plot.width <= 0f || plot.height <= 0f) return false

                val xRange = viewXMax - viewXMin
                val yRange = viewYMax - viewYMin

                val dataShiftX = (distanceX / plot.width) * xRange
                val dataShiftY = -(distanceY / plot.height) * yRange

                viewXMin += dataShiftX
                viewXMax += dataShiftX
                viewYMin += dataShiftY
                viewYMax += dataShiftY

                clampViewToBounds()
                invalidate()
                return true
            }
        }
    )

    fun setValues(
        newValues: List<Float>,
        startXSec: Float = 0f,
        stepXSec: Float = 1f,
        overlayValues: List<Float> = emptyList()
    ) {
        values = newValues
        this.overlayValues = overlayValues
        xStartSec = startXSec
        xStepSec = stepXSec.coerceAtLeast(0.0001f)

        if (values.isEmpty()) {
            viewXMin = 0f
            viewXMax = 1f
            viewYMin = 0f
            viewYMax = 1f
            invalidate()
            return
        }

        fullXMin = xStartSec
        fullXMax = xStartSec + (values.size - 1) * xStepSec

        val combined = values + overlayValues
        val minValue = combined.minOrNull() ?: 0f
        val maxValue = combined.maxOrNull() ?: 0f
        val pad = max(1f, (maxValue - minValue) * 0.1f)

        fullYMin = minValue - pad
        fullYMax = maxValue + pad

        resetZoom()
    }

    private fun resetZoom() {
        viewXMin = fullXMin
        viewXMax = fullXMax
        viewYMin = fullYMin
        viewYMax = fullYMax
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val plot = getPlotBounds()
        if (plot.width <= 0f || plot.height <= 0f) return

        canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, axisPaint)
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, axisPaint)

        if (values.isEmpty()) {
            canvas.drawText("CSV를 불러오면 그래프가 표시됩니다.", plot.left, height / 2f, textPaint)
            return
        }

        drawYAxisLabels(canvas, plot)
        drawXAxisLabels(canvas, plot)
        drawSeries(canvas, plot, values, linePaint)

        if (overlayValues.isNotEmpty()) {
            drawSeries(canvas, plot, overlayValues, overlayLinePaint)
        }

        canvas.drawText("핀치 줌 + 드래그 | X축: sec", plot.left, 34f, infoPaint)
    }

    private fun drawSeries(canvas: Canvas, plot: PlotBounds, series: List<Float>, paint: Paint) {
        val path = Path()
        var started = false

        series.forEachIndexed { index, value ->
            val x = xStartSec + index * xStepSec
            if (x < viewXMin || x > viewXMax) return@forEachIndexed

            val px = plot.left + (x - viewXMin) / (viewXMax - viewXMin) * plot.width
            val py = plot.bottom - (value - viewYMin) / (viewYMax - viewYMin) * plot.height

            if (!started) {
                path.moveTo(px, py)
                started = true
            } else {
                path.lineTo(px, py)
            }
        }

        if (started) canvas.drawPath(path, paint)
    }

    private fun drawYAxisLabels(canvas: Canvas, plot: PlotBounds) {
        val tickCount = 5
        val step = (viewYMax - viewYMin) / tickCount

        for (i in 0..tickCount) {
            val value = viewYMin + i * step
            val y = plot.bottom - (i.toFloat() / tickCount) * plot.height

            canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
            canvas.drawText(String.format("%.2f", value), 8f, y + 8f, textPaint)
        }
    }

    private fun drawXAxisLabels(canvas: Canvas, plot: PlotBounds) {
        val tickCount = 6
        val step = (viewXMax - viewXMin) / tickCount

        for (i in 0..tickCount) {
            val value = viewXMin + i * step
            val x = plot.left + (i.toFloat() / tickCount) * plot.width

            canvas.drawLine(x, plot.top, x, plot.bottom, gridPaint)
            canvas.drawText(String.format("%.1f", value), x - 24f, plot.bottom + 34f, textPaint)
        }
    }

    private fun clampViewToBounds() {
        val xRange = viewXMax - viewXMin
        val yRange = viewYMax - viewYMin

        if (viewXMin < fullXMin) {
            viewXMin = fullXMin
            viewXMax = viewXMin + xRange
        }
        if (viewXMax > fullXMax) {
            viewXMax = fullXMax
            viewXMin = viewXMax - xRange
        }

        if (viewYMin < fullYMin) {
            viewYMin = fullYMin
            viewYMax = viewYMin + yRange
        }
        if (viewYMax > fullYMax) {
            viewYMax = fullYMax
            viewYMin = viewYMax - yRange
        }

        viewXMin = viewXMin.coerceIn(fullXMin, fullXMax)
        viewXMax = viewXMax.coerceIn(fullXMin, fullXMax)
        viewYMin = viewYMin.coerceIn(fullYMin, fullYMax)
        viewYMax = viewYMax.coerceIn(fullYMin, fullYMax)
    }

    private fun getPlotBounds(): PlotBounds {
        val left = 120f
        val top = 60f
        val right = width - 30f
        val bottom = height - 100f
        return PlotBounds(left, top, right, bottom)
    }

    private data class PlotBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }
}
