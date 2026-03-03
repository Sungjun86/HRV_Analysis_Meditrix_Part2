package com.example.csvgraph

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.csvgraph.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

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

        configureChart()
    }

    private fun configureChart() = with(binding.lineChart) {
        description.isEnabled = false
        setNoDataText("CSV를 불러오면 그래프가 표시됩니다.")
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        axisRight.isEnabled = false
    }

    private fun renderCsv(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val points = CsvParser.parseNumericSeries(contentResolver, uri)
        if (points.isEmpty()) {
            Toast.makeText(this, "CSV에서 숫자 데이터를 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val entries = points.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val dataSet = LineDataSet(entries, "CSV Value").apply {
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.invalidate()
    }
}
