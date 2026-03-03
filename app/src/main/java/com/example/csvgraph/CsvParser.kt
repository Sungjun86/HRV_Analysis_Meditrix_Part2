package com.example.csvgraph

import android.content.ContentResolver
import android.net.Uri

data class HrvSample(
    val timeSec: Float,
    val value: Float
)

object CsvParser {

    fun parseHrvSamples(contentResolver: ContentResolver, uri: Uri): List<HrvSample> {
        val samples = mutableListOf<HrvSample>()
        var cumulativeSec = 0f

        contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach

                val numericTokens = line
                    .split(',', ';', '\t')
                    .map { it.trim() }
                    .mapNotNull { it.toFloatOrNull() }

                when {
                    // time,value 형태
                    numericTokens.size >= 2 -> {
                        val t = numericTokens[0]
                        val v = numericTokens[1]
                        samples.add(HrvSample(timeSec = t, value = v))
                    }

                    // value 하나만 있는 RR 시계열(ms 가정)
                    numericTokens.size == 1 -> {
                        val rrMs = numericTokens[0]
                        val value = rrMs
                        samples.add(HrvSample(timeSec = cumulativeSec, value = value))
                        cumulativeSec += (rrMs / 1000f).coerceAtLeast(0f)
                    }
                }
            }
        }

        return samples
            .sortedBy { it.timeSec }
            .distinctBy { it.timeSec }
    }
}
