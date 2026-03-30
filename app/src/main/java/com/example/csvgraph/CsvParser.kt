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

        val estimatedRows = countNumericRows(contentResolver, uri)
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

                val numericTokens = line
                    .split(',', ';', '\t')
                    .map { it.trim() }
                    .mapNotNull { it.toFloatOrNull() }

                when {
                    numericTokens.size >= 2 -> {
                        val t = numericTokens[0]
                        val v = numericTokens[1]
                        if (rowIndex % stride == 0) {
                            appendIfNewTime(samples, HrvSample(timeSec = t, value = v))
                        }
                        rowIndex++
                    }

                    numericTokens.size == 1 -> {
                        val rrMs = numericTokens[0]
                        val time = cumulativeSec
                        val value = rrMs
                        if (rowIndex % stride == 0) {
                            appendIfNewTime(samples, HrvSample(timeSec = time, value = value))
                        }
                        cumulativeSec += (rrMs / 1000f).coerceAtLeast(0f)
                        rowIndex++
                    }
                }
            }
        }

        return samples
    }

    private fun countNumericRows(contentResolver: ContentResolver, uri: Uri): Int {
        var count = 0
        contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val hasNumeric = line
                    .split(',', ';', '\t')
                    .asSequence()
                    .map { it.trim() }
                    .any { it.toFloatOrNull() != null }
                if (hasNumeric) count++
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
