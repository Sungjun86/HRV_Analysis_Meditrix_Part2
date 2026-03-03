package com.example.csvgraph

import android.content.ContentResolver
import android.net.Uri

object CsvParser {
    fun parseNumericSeries(contentResolver: ContentResolver, uri: Uri): List<Float> {
        val values = mutableListOf<Float>()

        contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                line.split(',')
                    .map { it.trim() }
                    .firstOrNull { token -> token.toFloatOrNull() != null }
                    ?.toFloatOrNull()
                    ?.let(values::add)
            }
        }

        return values
    }
}
