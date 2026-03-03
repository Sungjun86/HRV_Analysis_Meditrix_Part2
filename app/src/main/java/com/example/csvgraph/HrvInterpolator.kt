package com.example.csvgraph

import java.util.Locale

object HrvInterpolator {

    fun interpolateToFrequency(samples: List<HrvSample>, targetHz: Float = 4f): List<HrvSample> {
        if (samples.size < 2 || targetHz <= 0f) return emptyList()

        val sorted = samples.sortedBy { it.timeSec }
        val dt = 1f / targetHz
        val startTime = sorted.first().timeSec
        val endTime = sorted.last().timeSec

        if (endTime <= startTime) return emptyList()

        val result = mutableListOf<HrvSample>()
        var t = startTime
        var segment = 0

        while (t <= endTime + 1e-6f) {
            while (segment < sorted.lastIndex - 1 && sorted[segment + 1].timeSec < t) {
                segment++
            }

            val p0 = sorted[segment]
            val p1 = sorted[segment + 1]

            val interpolated = if (p1.timeSec == p0.timeSec) {
                p0.value
            } else {
                val alpha = (t - p0.timeSec) / (p1.timeSec - p0.timeSec)
                p0.value + alpha * (p1.value - p0.value)
            }

            result.add(HrvSample(t, interpolated))
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
}
