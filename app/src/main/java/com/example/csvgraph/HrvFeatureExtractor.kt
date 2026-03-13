package com.example.csvgraph

import kotlin.math.ceil
import kotlin.math.sqrt

object HrvFeatureExtractor {

    data class PoincareMetrics(
        val sd1: Float,
        val sd2: Float,
        val sd1Sd2Ratio: Float
    )


    data class FftMetrics(
        val pLf: Float,
        val pHf: Float,
        val lfHfRatio: Float,
        val vLf: Float,
        val lf: Float,
        val hf: Float
    )

    /**
     * MATLAB HR(RR,num,segment) 동작을 Kotlin으로 구현.
     * RR 단위: seconds
     */
    fun hr(rrInput: List<Float>, num: Int = 0, segment: Int = 0): List<Float> {
        val rr = rrInput.toList()
        if (rr.isEmpty()) return emptyList()

        return if (num == 0) {
            val valid = rr.filter { !it.isNaN() }
            val total = valid.sum()
            val avg = if (total > 0f) 60f * valid.size / total else Float.NaN
            List(rr.size) { avg }
        } else {
            if (segment == 0) {
                constantIntervalWindow(rr, num)
            } else {
                rollingSecondWindow(rr, num)
            }
        }
    }

    /**
     * MATLAB SDNN(RR,num,flag,overlap) 동작 참조 구현.
     * flag=1 -> n 으로 정규화, flag=0 -> (n-1) 정규화
     */
    fun sdnn(rrInput: List<Float>, num: Int = 0, flag: Int = 1, overlap: Float = 1f): List<Float> {
        val rr = rrInput.toList()
        if (rr.isEmpty()) return emptyList()

        return if (num == 0) {
            val global = nanStd(rr, flag)
            List(rr.size) { global }
        } else {
            val step = ceil(num * (1f - overlap)).toInt()
            if (step > 1) {
                val out = MutableList(rr.size) { Float.NaN }
                var i = step
                while (i <= rr.size) {
                    val start = maxOf(0, i - num)
                    val window = rr.subList(start, i)
                    val validCount = window.count { !it.isNaN() }
                    out[i - 1] = if (validCount < 5) Float.NaN else nanStd(window, flag)
                    i += step
                }
                out
            } else {
                val out = MutableList(rr.size) { Float.NaN }
                for (i in rr.indices) {
                    val start = maxOf(0, i - num + 1)
                    val window = rr.subList(start, i + 1)
                    val validCount = window.count { !it.isNaN() }
                    out[i] = if (validCount < 5) Float.NaN else nanStd(window, flag)
                }
                out
            }
        }
    }

    /**
     * MATLAB RMSSD(RR,num,flag,overlap) 동작 참조 구현.
     * RR 단위: seconds
     */
    fun rmssd(rrInput: List<Float>, num: Int = 0, flag: Int = 1, overlap: Float = 1f): List<Float> {
        val rr = rrInput.toList()
        if (rr.size < 2) return List(rr.size) { Float.NaN }

        val drr2 = MutableList(rr.size - 1) { i ->
            val a = rr[i]
            val b = rr[i + 1]
            if (a.isNaN() || b.isNaN()) Float.NaN else (b - a) * (b - a)
        }

        return if (num == 0) {
            val valid = drr2.filter { !it.isNaN() }
            val n = valid.size
            val denom = n - 1 + flag
            val g = if (denom > 0) sqrt(valid.sum() / denom) else Float.NaN
            List(rr.size) { g }
        } else {
            val step = ceil(num * (1f - overlap)).toInt()
            val out = MutableList(rr.size) { Float.NaN }

            if (step > 1) {
                var i = step
                while (i <= drr2.size) {
                    val start = maxOf(0, i - num)
                    val window = drr2.subList(start, i)
                    val valid = window.filter { !it.isNaN() }
                    out[i] = if (valid.size < 5) {
                        Float.NaN
                    } else {
                        val denom = valid.size - 1 + flag
                        if (denom > 0) sqrt(valid.sum() / denom) else Float.NaN
                    }
                    i += step
                }
            } else {
                for (i in rr.indices) {
                    if (i == 0) {
                        out[i] = Float.NaN
                        continue
                    }
                    val end = i
                    val start = maxOf(0, end - num)
                    val window = drr2.subList(start, end)
                    val valid = window.filter { !it.isNaN() }
                    out[i] = if (valid.size < 5) {
                        Float.NaN
                    } else {
                        val denom = valid.size - 1 + flag
                        if (denom > 0) sqrt(valid.sum() / denom) else Float.NaN
                    }
                }
            }
            out
        }
    }

    /** pNNx: abs(diff(RR)) > x(ms) 비율 */
    fun pNnx(rrInput: List<Float>, num: Int = 0, xMs: Int, flag: Int = 1, overlap: Float = 1f): List<Float> {
        val rr = rrInput.toList()
        if (rr.size < 2) return List(rr.size) { Float.NaN }

        val nnx = MutableList(rr.size - 1) { i ->
            val a = rr[i]
            val b = rr[i + 1]
            if (a.isNaN() || b.isNaN()) Float.NaN else if (kotlin.math.abs(b - a) > xMs / 1000f) 1f else 0f
        }

        if (num == 0) {
            val valid = nnx.filter { !it.isNaN() }
            val n = valid.size
            val denom = n - 1 + flag
            val v = if (denom > 0) valid.sum() / denom else Float.NaN
            return List(rr.size) { v }
        }

        val out = MutableList(rr.size) { Float.NaN }
        val step = ceil(num * (1f - overlap)).toInt()

        if (step > 1) {
            var i = step
            while (i <= nnx.size) {
                val start = maxOf(0, i - num)
                val window = nnx.subList(start, i)
                val valid = window.filter { !it.isNaN() }
                out[i] = if (valid.size < 5) Float.NaN else {
                    val denom = valid.size - 1 + flag
                    if (denom > 0) valid.sum() / denom else Float.NaN
                }
                i += step
            }
        } else {
            for (i in rr.indices) {
                if (i == 0) {
                    out[i] = Float.NaN
                    continue
                }
                val end = i
                val start = maxOf(0, end - num)
                val window = nnx.subList(start, end)
                val valid = window.filter { !it.isNaN() }
                out[i] = if (valid.size < 5) Float.NaN else {
                    val denom = valid.size - 1 + flag
                    if (denom > 0) valid.sum() / denom else Float.NaN
                }
            }
        }

        return out
    }

    fun fHrAverage(rrInput: List<Float>): Float = hr(rrInput, num = 0, segment = 0).firstOrNull() ?: Float.NaN
    fun fSdnn(rrInput: List<Float>, flag: Int = 1): Float = sdnn(rrInput, num = 0, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN
    fun fRmssd(rrInput: List<Float>, flag: Int = 1): Float = rmssd(rrInput, num = 0, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN

    fun fPnn10(rrInput: List<Float>, flag: Int = 1): Float = pNnx(rrInput, num = 0, xMs = 10, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN
    fun fPnn20(rrInput: List<Float>, flag: Int = 1): Float = pNnx(rrInput, num = 0, xMs = 20, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN
    fun fPnn30(rrInput: List<Float>, flag: Int = 1): Float = pNnx(rrInput, num = 0, xMs = 30, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN
    fun fPnn40(rrInput: List<Float>, flag: Int = 1): Float = pNnx(rrInput, num = 0, xMs = 40, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN
    fun fPnn50(rrInput: List<Float>, flag: Int = 1): Float = pNnx(rrInput, num = 0, xMs = 50, flag = flag, overlap = 1f).firstOrNull() ?: Float.NaN

    /**
     * MATLAB reference: alpha = DFA(HRV_Percentage)
     * 기본 box size: short=4..16, long=16..64, grade=1
     * 반환값은 alpha(1) (short-term scaling exponent) 사용.
     */
    fun fAlpha(
        rrInput: List<Float>,
        boxsizeShort: IntRange = 4..16,
        boxsizeLong: IntRange = 16..64,
        grade: Int = 1
    ): Float {
        val rr = rrInput.filter { !it.isNaN() }
        if (rr.size < 8 || grade < 1) return Float.NaN

        val meanRr = rr.sum() / rr.size
        val y = MutableList(rr.size) { 0f }
        var cumulative = 0f
        for (i in rr.indices) {
            cumulative += rr[i] - meanRr
            y[i] = cumulative
        }

        val boxSizes = (boxsizeShort.toList() + boxsizeLong.toList()).distinct().filter { it >= 2 }
        if (boxSizes.isEmpty()) return Float.NaN

        val fValues = mutableListOf<Float>()
        val validSizes = mutableListOf<Int>()

        for (bs in boxSizes) {
            val trend = FloatArray(rr.size)
            val lastSegment = (rr.size - 2) / bs
            for (segment in 0..lastSegment) {
                val start = segment * bs
                val endInclusive = if (segment == lastSegment) rr.lastIndex else ((segment + 1) * bs - 1).coerceAtMost(rr.lastIndex)
                val x = IntArray(endInclusive - start + 1) { idx -> start + idx + 1 }
                val ySeg = FloatArray(x.size) { idx -> y[start + idx] }
                val coeff = polyfitLinear(x, ySeg)
                if (coeff == null) continue
                val (slope, intercept) = coeff
                for (idx in x.indices) {
                    trend[start + idx] = slope * x[idx] + intercept
                }
            }

            var sumSq = 0.0
            for (i in rr.indices) {
                val d = y[i] - trend[i]
                sumSq += (d * d)
            }
            val f = kotlin.math.sqrt(sumSq / rr.size).toFloat()
            if (!f.isNaN() && f > 0f) {
                validSizes += bs
                fValues += f
            }
        }

        val shortPairs = validSizes.zip(fValues).filter { (bs, _) -> bs in boxsizeShort }
        if (shortPairs.size < 2) return Float.NaN

        val xLog = shortPairs.map { kotlin.math.ln(it.first.toFloat()) }
        val yLog = shortPairs.map { kotlin.math.ln(it.second) }
        val alpha = linearRegressionSlope(xLog, yLog)

        return alpha
    }

    /**
     * MATLAB: [f_pLF, f_pHF, f_LFHF, f_VLF, f_LF, f_HF] = fft_val_fun(HRV_Percentage, 500)
     */
    fun fFftMetrics(rrInput: List<Float>, fs: Float = 500f): FftMetrics {
        if (rrInput.size < 2 || fs <= 0f || rrInput.any { it.isNaN() }) {
            return FftMetrics(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        }

        val ann = mutableListOf<Float>()
        var sum = 0f
        rrInput.forEachIndexed { idx, v ->
            sum += v
            ann += if (idx == 0) 0f else sum - rrInput.first()
        }

        val end = ann.last()
        if (end <= 0f) {
            return FftMetrics(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        }

        val step = 1f / fs
        val rrResampled = splineResample(ann, rrInput, step)
        if (rrResampled.isEmpty()) {
            return FftMetrics(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        }

        val z = nanZScore(rrResampled, opt = 0)
        val l = z.size
        if (l == 0) {
            return FftMetrics(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        }

        val nfft = nextPow2(l)
        val power = FloatArray(nfft / 2 + 1)

        for (k in 0..(nfft / 2)) {
            var real = 0.0
            var imag = 0.0
            for (n in 0 until l) {
                val angle = -2.0 * Math.PI * k * n / nfft
                val sample = z[n].toDouble()
                real += sample * kotlin.math.cos(angle)
                imag += sample * kotlin.math.sin(angle)
            }
            real /= l
            imag /= l
            val amp = 2.0 * kotlin.math.sqrt(real * real + imag * imag)
            power[k] = (amp * amp).toFloat()
        }

        fun bandSum(maxHz: Float): Float {
            var acc = 0f
            for (k in power.indices) {
                val f = fs / 2f * (k.toFloat() / (nfft / 2f))
                if (f <= maxHz) acc += power[k]
            }
            return acc
        }

        val vlf = bandSum(0.04f)
        val lfTotal = bandSum(0.15f)
        val hfTotal = bandSum(0.4f)

        val lf = lfTotal - vlf
        val hf = hfTotal - vlf - lf
        val tp = hfTotal

        val denom = tp - vlf
        val pLf = if (denom > 0f) lf / denom * 100f else Float.NaN
        val pHf = if (denom > 0f) hf / denom * 100f else Float.NaN
        val lfHfRatio = if (hf != 0f) lf / hf else Float.NaN

        return FftMetrics(
            pLf = pLf,
            pHf = pHf,
            lfHfRatio = lfHfRatio,
            vLf = vlf,
            lf = lf,
            hf = hf
        )
    }

    /**
     * MATLAB: [f_SD1, f_SD2, f_SD1SD2] = returnmap_val(HRV_Percentage,0,0)
     */
    fun fPoincare(rrInput: List<Float>): PoincareMetrics {
        if (rrInput.size < 2) {
            return PoincareMetrics(Float.NaN, Float.NaN, Float.NaN)
        }

        val x1 = mutableListOf<Float>()
        val x2 = mutableListOf<Float>()
        for (i in 0 until rrInput.lastIndex) {
            x1 += rrInput[i]
            x2 += rrInput[i + 1]
        }

        val c = 0.70710677f // cos(-45°) = sin(45°)
        val xr1 = MutableList(x1.size) { i ->
            val a = x1[i]
            val b = x2[i]
            if (a.isNaN() || b.isNaN()) Float.NaN else c * (a + b)
        }
        val xr2 = MutableList(x1.size) { i ->
            val a = x1[i]
            val b = x2[i]
            if (a.isNaN() || b.isNaN()) Float.NaN else c * (b - a)
        }

        // returnmap_val(..., 0, 0): flag=0 -> n-1 normalization
        val sd2 = nanStd(xr1, flag = 0)
        val sd1 = nanStd(xr2, flag = 0)
        val ratio = if (sd2 == 0f || sd2.isNaN()) Float.NaN else sd1 / sd2

        return PoincareMetrics(sd1 = sd1, sd2 = sd2, sd1Sd2Ratio = ratio)
    }

    private fun constantIntervalWindow(rr: List<Float>, num: Int): List<Float> {
        val out = MutableList(rr.size) { Float.NaN }
        for (i in rr.indices) {
            var count = 0
            var sum = 0f
            val start = (i - num + 1).coerceAtLeast(0)
            for (j in start..i) {
                val v = rr[j]
                if (!v.isNaN()) {
                    count++
                    sum += v
                }
            }
            out[i] = if (count > 0 && sum > 0f) 60f * count / sum else Float.NaN
        }
        return out
    }

    private fun rollingSecondWindow(rr: List<Float>, seconds: Int): List<Float> {
        val out = MutableList(rr.size) { Float.NaN }
        for (i in rr.indices) {
            var count = 0
            var sum = 0f
            var t = 0f
            var j = i
            while (j >= 0 && t <= seconds) {
                val v = rr[j]
                if (!v.isNaN()) {
                    count++
                    sum += v
                    t += v
                }
                j--
            }
            out[i] = if (count > 0 && sum > 0f) 60f * count / sum else Float.NaN
        }
        return out
    }


    private fun polyfitLinear(x: IntArray, y: FloatArray): Pair<Float, Float>? {
        if (x.size != y.size || x.isEmpty()) return null

        val n = x.size.toFloat()
        val sumX = x.sum().toFloat()
        val sumY = y.sum()
        var sumXX = 0f
        var sumXY = 0f
        for (i in x.indices) {
            val xv = x[i].toFloat()
            val yv = y[i]
            sumXX += xv * xv
            sumXY += xv * yv
        }

        val denom = n * sumXX - sumX * sumX
        if (denom == 0f) return null

        val slope = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n
        return slope to intercept
    }

    private fun linearRegressionSlope(x: List<Float>, y: List<Float>): Float {
        if (x.size != y.size || x.size < 2) return Float.NaN

        val n = x.size.toFloat()
        val meanX = x.sum() / n
        val meanY = y.sum() / n

        var numerator = 0f
        var denominator = 0f
        for (i in x.indices) {
            val dx = x[i] - meanX
            numerator += dx * (y[i] - meanY)
            denominator += dx * dx
        }

        if (denominator == 0f) return Float.NaN
        return numerator / denominator
    }

    private fun splineResample(x: List<Float>, y: List<Float>, step: Float): List<Float> {
        if (x.size < 2 || y.size < 2 || x.size != y.size) return emptyList()

        val spline = NaturalCubicSpline(
            x.map { it.toDouble() },
            y.map { it.toDouble() }
        )

        val out = mutableListOf<Float>()
        var t = 0f
        val end = x.last()
        while (t <= end + 1e-6f) {
            out += spline.evaluate(t.toDouble()).toFloat()
            t += step
        }
        return out
    }

    private fun nanMean(values: List<Float>): Float {
        val valid = values.filter { !it.isNaN() }
        return if (valid.isEmpty()) Float.NaN else valid.sum() / valid.size
    }

    private fun nanZScore(values: List<Float>, opt: Int = 0): List<Float> {
        val m = nanMean(values)
        val s = nanStd(values, opt)
        if (m.isNaN() || s.isNaN() || s == 0f) return List(values.size) { 0f }
        return values.map { v -> if (v.isNaN()) Float.NaN else (v - m) / s }
    }

    private fun nextPow2(value: Int): Int {
        var n = 1
        while (n < value) n = n shl 1
        return n
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
            for (i in 1 until n - 1) {
                l[i] = 2.0 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1]
                mu[i] = h[i] / l[i]
                z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i]
            }

            l[n - 1] = 1.0
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

    private fun nanStd(values: List<Float>, flag: Int): Float {
        val valid = values.filter { !it.isNaN() }
        val n = valid.size
        if (n == 0) return Float.NaN
        if (n == 1) return 0f

        val mean = valid.sum() / n
        val varianceNumerator = valid.sumOf { ((it - mean) * (it - mean)).toDouble() }
        val denominator = if (flag == 0) (n - 1).toDouble() else n.toDouble()
        if (denominator <= 0.0) return Float.NaN

        return sqrt(varianceNumerator / denominator).toFloat()
    }
}
