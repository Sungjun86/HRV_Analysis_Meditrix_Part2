package com.example.csvgraph

import android.content.ContentResolver
import android.net.Uri
import kotlin.math.ceil

data class HrvSample(
    val timeSec: Float,
    val value: Float
)

object CsvParser {

    fun parseHrvSamples(
        contentResolver: ContentResolver,
        uri: Uri,
        maxSamples: Int = Int.MAX_VALUE
    ): List<HrvSample> {
        if (maxSamples <= 0) return emptyList()

        val estimatedRows = countValidRows(contentResolver, uri)
        val stride = if (estimatedRows > maxSamples) {
            ceil(estimatedRows / maxSamples.toDouble()).toInt().coerceAtLeast(1)
        } else {
            1
        }

        val samples = ArrayList<HrvSample>(minOf(estimatedRows, maxSamples))
        var cumulativeSec = 0f
        var rowIndex = 0

        contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach

                val cols = line.split(',', ';', '\t', limit = 4).map { it.trim() }

                when {
                    // 1번 column = time, 3번 column = value
                    cols.size >= 3 -> {
                        val time = cols[0].toFloatOrNull()
                        val value = cols[2].toFloatOrNull()
                        if (time != null && value != null) {
                            if (rowIndex % stride == 0) {
                                appendIfNewTime(samples, HrvSample(timeSec = time, value = value))
                            }
                            rowIndex++
                        }
                    }

                    // single-column RR(ms)
                    cols.size == 1 -> {
                        val rrMs = cols[0].toFloatOrNull()
                        if (rrMs != null) {
                            val time = cumulativeSec
                            if (rowIndex % stride == 0) {
                                appendIfNewTime(samples, HrvSample(timeSec = time, value = rrMs))
                            }
                            cumulativeSec += (rrMs / 1000f).coerceAtLeast(0f)
                            rowIndex++
                        }
                    }
                }
            }
        }

        return samples
    }

    private fun countValidRows(contentResolver: ContentResolver, uri: Uri): Int {
        var count = 0
        contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val cols = line.split(',', ';', '\t', limit = 4).map { it.trim() }
                val valid = when {
                    cols.size >= 3 -> cols[0].toFloatOrNull() != null && cols[2].toFloatOrNull() != null
                    cols.size == 1 -> cols[0].toFloatOrNull() != null
                    else -> false
                }
                if (valid) count++
            }
        }
        return count
    }

    private fun appendIfNewTime(samples: MutableList<HrvSample>, sample: HrvSample) {
        val last = samples.lastOrNull()
        if (last == null || last.timeSec != sample.timeSec) {
            samples.add(sample)
        }
    }
}
