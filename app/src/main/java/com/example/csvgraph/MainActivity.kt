package com.example.csvgraph

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.csvgraph.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val openCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                renderCsv(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLoadCsv.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/csv"))
        }
    }

    private fun renderCsv(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val rawSamples = CsvParser.parseHrvSamples(contentResolver, uri)
        if (rawSamples.size < 2) {
            Toast.makeText(this, "HRV 데이터가 충분하지 않습니다. (최소 2개)", Toast.LENGTH_SHORT).show()
            return
        }

        val rawStep = if (rawSamples.size > 1) {
            ((rawSamples.last().timeSec - rawSamples.first().timeSec) / (rawSamples.size - 1)).coerceAtLeast(0.0001f)
        } else {
            1f
        }

        binding.rawGraphView.setValues(
            newValues = rawSamples.map { it.value },
            startXSec = rawSamples.first().timeSec,
            stepXSec = rawStep
        )

        val interpolated = HrvInterpolator.interpolateToFrequency(rawSamples, targetHz = 4f)
        if (interpolated.isEmpty()) {
            Toast.makeText(this, "4Hz 보간 결과가 비어 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val start = interpolated.first().timeSec
        val step = if (interpolated.size > 1) {
            interpolated[1].timeSec - interpolated[0].timeSec
        } else {
            0.25f
        }

        binding.interpolatedGraphView.setValues(
            newValues = interpolated.map { it.value },
            startXSec = start,
            stepXSec = step
        )
    }
}
