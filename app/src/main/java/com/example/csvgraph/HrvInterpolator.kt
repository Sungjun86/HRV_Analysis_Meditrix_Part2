package com.example.csvgraph

import java.util.Locale

object HrvInterpolator {

    fun interpolateToFrequency(samples: List<HrvSample>, targetHz: Float = 4f): List<HrvSample> {
        if (samples.size < 2 || targetHz <= 0f) return emptyList()

        val sorted = samples
            .sortedBy { it.timeSec }
            .distinctBy { it.timeSec }

        if (sorted.size < 2) return emptyList()

        val x = sorted.map { it.timeSec.toDouble() }
        val y = sorted.map { it.value.toDouble() }

        val spline = NaturalCubicSpline(x, y)

        val dt = 1f / targetHz
        val startTime = sorted.first().timeSec
        val endTime = sorted.last().timeSec

        if (endTime <= startTime) return emptyList()

        val result = mutableListOf<HrvSample>()
        var t = startTime
        while (t <= endTime + 1e-6f) {
            val v = spline.evaluate(t.toDouble()).toFloat()
            result.add(HrvSample(t, v))
            t += dt
        }

        return result
    }

    fun toCsv(samples: List<HrvSample>): String {
        val sb = StringBuilder()
        sb.append("time_sec,hrv_value\n")
        samples.forEach { sample ->
            sb.append(String.format(Locale.US, "%.3f,%.6f\n", sample.timeSec, sample.value))
        }
        return sb.toString()
    }

    private class NaturalCubicSpline(
        private val x: List<Double>,
        private val y: List<Double>
    ) {
        private val n = x.size
        private val a = y.toMutableList()
        private val b = MutableList(n - 1) { 0.0 }
        private val c = MutableList(n) { 0.0 }
        private val d = MutableList(n - 1) { 0.0 }

        init {
            val h = MutableList(n - 1) { i -> x[i + 1] - x[i] }
            val alpha = MutableList(n) { 0.0 }

            for (i in 1 until n - 1) {
                alpha[i] = (3.0 / h[i]) * (a[i + 1] - a[i]) - (3.0 / h[i - 1]) * (a[i] - a[i - 1])
            }

            val l = MutableList(n) { 0.0 }
            val mu = MutableList(n) { 0.0 }
            val z = MutableList(n) { 0.0 }

            l[0] = 1.0
            mu[0] = 0.0
            z[0] = 0.0

            for (i in 1 until n - 1) {
                l[i] = 2.0 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1]
                mu[i] = h[i] / l[i]
                z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i]
            }

            l[n - 1] = 1.0
            z[n - 1] = 0.0
            c[n - 1] = 0.0

            for (j in n - 2 downTo 0) {
                c[j] = z[j] - mu[j] * c[j + 1]
                b[j] = (a[j + 1] - a[j]) / h[j] - h[j] * (c[j + 1] + 2.0 * c[j]) / 3.0
                d[j] = (c[j + 1] - c[j]) / (3.0 * h[j])
            }
        }

        fun evaluate(xq: Double): Double {
            if (xq <= x.first()) return y.first()
            if (xq >= x.last()) return y.last()

            var low = 0
            var high = n - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                when {
                    xq < x[mid] -> high = mid - 1
                    xq > x[mid] -> low = mid + 1
                    else -> return y[mid]
                }
            }

            val i = (low - 1).coerceIn(0, n - 2)
            val dx = xq - x[i]
            return a[i] + b[i] * dx + c[i] * dx * dx + d[i] * dx * dx * dx
        }
    }
}
