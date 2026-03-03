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

        val points = CsvParser.parseNumericSeries(contentResolver, uri)
        if (points.isEmpty()) {
            Toast.makeText(this, "CSV에서 숫자 데이터를 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.graphView.setValues(points)
    }
}
