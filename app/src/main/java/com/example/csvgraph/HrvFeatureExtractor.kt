package com.example.csvgraph

import kotlin.math.ceil
import kotlin.math.sqrt

object HrvFeatureExtractor {

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
