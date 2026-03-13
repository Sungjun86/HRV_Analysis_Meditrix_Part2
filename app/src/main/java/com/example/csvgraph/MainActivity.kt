package com.example.csvgraph

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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
        setLoadingState(false)

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

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressProcessing.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonLoadCsv.isEnabled = !isLoading
        binding.buttonSaveInterpolatedCsv.isEnabled = !isLoading
    }

    private fun renderCsv(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        setLoadingState(true)
        binding.root.post {
            try {
                val rawSamples = CsvParser.parseHrvSamples(contentResolver, uri)
                if (rawSamples.size < 2) {
                    Toast.makeText(this, "HRV 데이터가 충분하지 않습니다. (최소 2개)", Toast.LENGTH_SHORT).show()
                    return@post
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
                val fft = HrvFeatureExtractor.fFftMetrics(hrvPercentage.map { it.value }, fs = 500f)
                val dfa = HrvFeatureExtractor.dfaMetrics(hrvPercentage.map { it.value })
                val tri = HrvFeatureExtractor.fTriangular(hrvPercentage.map { it.value })
                val fCd = HrvFeatureExtractor.fCd(hrvPercentage.map { it.value })
                val fSampen = HrvFeatureExtractor.fSampen(hrvPercentage.map { it.value }, m = 2, r = 0.2f)
                val fApen = HrvFeatureExtractor.fApen(hrvPercentage.map { it.value })
                val shann = HrvFeatureExtractor.fShann(hrvPercentage.map { it.value })
                val fM1 = HrvFeatureExtractor.fM1(hrvPercentage.map { it.value })
                val fM2 = HrvFeatureExtractor.fM2(hrvPercentage.map { it.value })
                val fM3 = HrvFeatureExtractor.fM3(hrvPercentage.map { it.value })
                val fAutoc = HrvFeatureExtractor.fAutoc(hrvPercentage.map { it.value })

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

                binding.textFftValues.text = if (
                    fft.pLf.isNaN() && fft.pHf.isNaN() && fft.lfHfRatio.isNaN() &&
                    fft.vLf.isNaN() && fft.lf.isNaN() && fft.hf.isNaN()
                ) {
                    "f_pLF: 계산 불가\nf_pHF: 계산 불가\nf_LFHF: 계산 불가\nf_VLF: 계산 불가\nf_LF: 계산 불가\nf_HF: 계산 불가"
                } else {
                    String.format(
                        "f_pLF: %.4f\nf_pHF: %.4f\nf_LFHF: %.4f\nf_VLF: %.4f\nf_LF: %.4f\nf_HF: %.4f",
                        fft.pLf,
                        fft.pHf,
                        fft.lfHfRatio,
                        fft.vLf,
                        fft.lf,
                        fft.hf
                    )
                }

                binding.textFalphaValue.text = if (dfa.alpha1.isNaN() && dfa.alpha2.isNaN()) {
                    "f_alpha_1: 계산 불가\nf_alpha_2: 계산 불가"
                } else {
                    String.format("f_alpha_1: %.4f\nf_alpha_2: %.4f", dfa.alpha1, dfa.alpha2)
                }

                binding.textFtriTinnValue.text = if (tri.tri.isNaN() && tri.tinn.isNaN()) {
                    "f_TRI: 계산 불가\nf_TINN: 계산 불가"
                } else {
                    String.format("f_TRI: %.4f\nf_TINN: %.4f s", tri.tri, tri.tinn)
                }

                binding.textFcdValue.text = if (fCd.isNaN()) {
                    "f_cd: 계산 불가"
                } else {
                    String.format("f_cd: %.4f", fCd)
                }

                binding.textFsampenValue.text = if (fSampen.isNaN()) {
                    "f_sampen: 계산 불가"
                } else {
                    String.format("f_sampen: %.4f", fSampen)
                }

                binding.textFapenValue.text = if (fApen.isNaN()) {
                    "f_apen: 계산 불가"
                } else {
                    String.format("f_apen: %.4f", fApen)
                }

                binding.textFshannValues.text = if (shann.shann1.isNaN() && shann.shann2.isNaN()) {
                    "f_shann1: 계산 불가\nf_shann2: 계산 불가"
                } else {
                    String.format("f_shann1: %.4f\nf_shann2: %.4f", shann.shann1, shann.shann2)
                }

                binding.textFmValues.text = if (fM1.isNaN() && fM2.isNaN() && fM3.isNaN()) {
                    "f_m1: 계산 불가\nf_m2: 계산 불가\nf_m3: 계산 불가"
                } else {
                    String.format("f_m1: %.4f\nf_m2: %.4f\nf_m3: %.4f", fM1, fM2, fM3)
                }

                binding.textFautocValue.text = if (fAutoc.isNaN()) {
                    "f_autoc: 계산 불가"
                } else {
                    String.format("f_autoc: %.4f", fAutoc)
                }

                // 4) interpolate HRV_Percentage by 4Hz cubic spline
                val interpolated = HrvInterpolator.interpolateTo4HzCubicSpline(hrvPercentage)
                if (interpolated.isEmpty()) {
                    Toast.makeText(this, "4Hz 보간 결과가 비어 있습니다.", Toast.LENGTH_SHORT).show()
                    return@post
                }

                // 5) detrend on interpolated signal -> HRV_Interpolation
                val hrvInterpolation = HrvSignalProcessor.detrendLinear(interpolated)
                latestHrvInterpolation = hrvInterpolation

                // 4Hz Interpolation 그래프는 UI에서 제거되었으므로
                // 보간/디트렌드 결과는 CSV 저장용 데이터만 유지
            } finally {
                setLoadingState(false)
            }
        }
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
