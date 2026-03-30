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

                val parsed = parseUpToTwoNumbers(line)
                when (parsed.size) {
                    2 -> {
                        val t = parsed[0]
                        val v = parsed[1]
                        if (rowIndex % stride == 0) {
                            appendIfNewTime(samples, HrvSample(timeSec = t, value = v))
                        }
                        rowIndex++
                    }

                    1 -> {
                        val rrMs = parsed[0]
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

        return samples
    }

    private fun countNumericRows(contentResolver: ContentResolver, uri: Uri): Int {
        var count = 0
        contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                if (parseUpToTwoNumbers(line).isNotEmpty()) count++
            }
        }
        return count
    }

    private fun parseUpToTwoNumbers(line: String): List<Float> {
        val out = ArrayList<Float>(2)
        val token = StringBuilder()

        fun flushToken() {
            if (token.isEmpty()) return
            val value = token.toString().trim().toFloatOrNull()
            if (value != null) out.add(value)
            token.setLength(0)
        }

        for (c in line) {
            if (c == ',' || c == ';' || c == '\t') {
                flushToken()
                if (out.size >= 2) break
            } else {
                token.append(c)
            }
        }

        if (out.size < 2) flushToken()
        return out
    }

    private fun appendIfNewTime(samples: MutableList<HrvSample>, sample: HrvSample) {
        val last = samples.lastOrNull()
        if (last == null || last.timeSec != sample.timeSec) {
            samples.add(sample)
        }
    }
}
