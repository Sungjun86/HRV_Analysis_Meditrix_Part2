package com.example.csvgraph

import kotlin.math.abs

object HrvSignalProcessor {

    fun apply20PercentFilter(samples: List<HrvSample>): List<HrvSample> {
        if (samples.isEmpty()) return emptyList()

        val filtered = mutableListOf(samples.first())
        for (i in 1 until samples.size) {
            val prev = filtered.last().value
            val curr = samples[i].value

            val newValue = if (prev == 0f) {
                curr
            } else {
                val maxDelta = abs(prev) * 0.2f
                val delta = (curr - prev).coerceIn(-maxDelta, maxDelta)
                prev + delta
            }

            filtered.add(samples[i].copy(value = newValue))
        }

        return filtered
    }

    fun detrendLinear(samples: List<HrvSample>): List<HrvSample> {
        if (samples.size < 2) return samples

        val n = samples.size.toDouble()
        val sumX = samples.sumOf { it.timeSec.toDouble() }
        val sumY = samples.sumOf { it.value.toDouble() }
        val sumXY = samples.sumOf { it.timeSec.toDouble() * it.value.toDouble() }
        val sumX2 = samples.sumOf { it.timeSec.toDouble() * it.timeSec.toDouble() }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return samples

        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n

        return samples.map { s ->
            val trend = slope * s.timeSec + intercept
            s.copy(value = (s.value - trend).toFloat())
        }
    }
}
