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
    private var latestHrvInterpolation: List<HrvSample> = emptyList()

    private val openCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                renderCsv(uri)
            }
        }

    private val createInterpolatedCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            if (uri != null) {
                saveInterpolatedCsv(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLoadCsv.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/csv"))
        }

        binding.buttonSaveInterpolatedCsv.setOnClickListener {
            if (latestHrvInterpolation.isEmpty()) {
                Toast.makeText(this, "먼저 CSV를 불러와 처리 데이터를 생성하세요.", Toast.LENGTH_SHORT).show()
            } else {
                createInterpolatedCsvLauncher.launch("hrv_interpolation_4hz.csv")
            }
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

        // 3) 20% filter on raw HRV -> HRV_Percentage
        val hrvPercentage = HrvSignalProcessor.apply20PercentFilter(rawSamples)

        // f_HR / f_SDNN input: HRV_Percentage
        val rrSeconds = toRrSeconds(hrvPercentage.map { it.value })

        val fHr = HrvFeatureExtractor.fHrAverage(rrSeconds)
        binding.textFhrValue.text = if (fHr.isNaN()) {
            "f_HR: 계산 불가"
        } else {
            String.format("f_HR: %.2f bpm", fHr)
        }

        val fSdnn = HrvFeatureExtractor.fSdnn(rrSeconds, flag = 1)
        binding.textFsdnnValue.text = if (fSdnn.isNaN()) {
            "f_SDNN: 계산 불가"
        } else {
            String.format("f_SDNN: %.4f s", fSdnn)
        }

        val fRmssd = HrvFeatureExtractor.fRmssd(rrSeconds, flag = 1)
        binding.textFrmssdValue.text = if (fRmssd.isNaN()) {
            "f_RMSSD: 계산 불가"
        } else {
            String.format("f_RMSSD: %.4f s", fRmssd)
        }

        val fPnn10 = HrvFeatureExtractor.fPnn10(rrSeconds, flag = 1)
        val fPnn20 = HrvFeatureExtractor.fPnn20(rrSeconds, flag = 1)
        val fPnn30 = HrvFeatureExtractor.fPnn30(rrSeconds, flag = 1)
        val fPnn40 = HrvFeatureExtractor.fPnn40(rrSeconds, flag = 1)
        val fPnn50 = HrvFeatureExtractor.fPnn50(rrSeconds, flag = 1)
        val poincare = HrvFeatureExtractor.fPoincare(hrvPercentage.map { it.value })

        binding.textFpnnValues.text = if (
            fPnn10.isNaN() && fPnn20.isNaN() && fPnn30.isNaN() && fPnn40.isNaN() && fPnn50.isNaN()
        ) {
            "f_pNN10: 계산 불가\n" +
                "f_pNN20: 계산 불가\n" +
                "f_pNN30: 계산 불가\n" +
                "f_pNN40: 계산 불가\n" +
                "f_pNN50: 계산 불가"
        } else {
            String.format(
                "f_pNN10: %.4f\nf_pNN20: %.4f\nf_pNN30: %.4f\nf_pNN40: %.4f\nf_pNN50: %.4f",
                fPnn10, fPnn20, fPnn30, fPnn40, fPnn50
            )
        }

        binding.textPoincareValues.text = if (
            poincare.sd1.isNaN() && poincare.sd2.isNaN() && poincare.sd1Sd2Ratio.isNaN()
        ) {
            "f_SD1: 계산 불가\nf_SD2: 계산 불가\nf_SD1SD2: 계산 불가"
        } else {
            String.format(
                "f_SD1: %.4f\nf_SD2: %.4f\nf_SD1SD2: %.4f",
                poincare.sd1,
                poincare.sd2,
                poincare.sd1Sd2Ratio
            )
        }

        // 4) interpolate HRV_Percentage by 4Hz cubic spline
        val interpolated = HrvInterpolator.interpolateTo4HzCubicSpline(hrvPercentage)
        if (interpolated.isEmpty()) {
            Toast.makeText(this, "4Hz 보간 결과가 비어 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 5) detrend on interpolated signal -> HRV_Interpolation
        val hrvInterpolation = HrvSignalProcessor.detrendLinear(interpolated)
        latestHrvInterpolation = hrvInterpolation

        val start = interpolated.first().timeSec
        val step = if (interpolated.size > 1) {
            interpolated[1].timeSec - interpolated[0].timeSec
        } else {
            0.25f
        }

        // Blue: interpolated(HRV_Percentage), Red: HRV_Interpolation(detrended)
        binding.interpolatedGraphView.setValues(
            newValues = interpolated.map { it.value },
            startXSec = start,
            stepXSec = step,
            overlayValues = hrvInterpolation.map { it.value }
        )
    }

    private fun toRrSeconds(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        val median = values.sorted()[values.size / 2]
        val assumeMs = median > 10f
        return if (assumeMs) values.map { it / 1000f } else values
    }

    private fun saveInterpolatedCsv(uri: Uri) {
        if (latestHrvInterpolation.isEmpty()) return

        runCatching {
            val csvText = HrvInterpolator.toCsv(latestHrvInterpolation)
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(csvText)
            }
        }.onSuccess {
            Toast.makeText(this, "HRV_Interpolation CSV 저장 완료", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "CSV 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
