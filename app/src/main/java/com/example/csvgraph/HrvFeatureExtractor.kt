package com.example.csvgraph

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

    fun fHrAverage(rrInput: List<Float>): Float {
        return hr(rrInput, num = 0, segment = 0).firstOrNull() ?: Float.NaN
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
}
