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
    private var latestFeatureCsv: String? = null

    private val openCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                renderCsv(uri)
            }
        }

    private val createFeatureCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            if (uri != null) {
                saveFeatureCsv(uri)
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

        binding.buttonSaveFeatureCsv.setOnClickListener {
            if (latestFeatureCsv == null) {
                Toast.makeText(this, "먼저 CSV를 불러와 Feature를 계산하세요.", Toast.LENGTH_SHORT).show()
            } else {
                createFeatureCsvLauncher.launch("hrv_features.csv")
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressProcessing.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonLoadCsv.isEnabled = !isLoading
        binding.buttonSaveFeatureCsv.isEnabled = !isLoading
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

                val hrvPercentage = HrvSignalProcessor.apply20PercentFilter(rawSamples)
                val hrvPercentageValues = hrvPercentage.map { it.value }
                val rrSeconds = toRrSeconds(hrvPercentageValues)

                val fHr = HrvFeatureExtractor.fHrAverage(rrSeconds)
                val fSdnn = HrvFeatureExtractor.fSdnn(rrSeconds, flag = 1)
                val fRmssd = HrvFeatureExtractor.fRmssd(rrSeconds, flag = 1)
                val fPnn10 = HrvFeatureExtractor.fPnn10(rrSeconds, flag = 1)
                val fPnn20 = HrvFeatureExtractor.fPnn20(rrSeconds, flag = 1)
                val fPnn30 = HrvFeatureExtractor.fPnn30(rrSeconds, flag = 1)
                val fPnn40 = HrvFeatureExtractor.fPnn40(rrSeconds, flag = 1)
                val fPnn50 = HrvFeatureExtractor.fPnn50(rrSeconds, flag = 1)
                val poincare = HrvFeatureExtractor.fPoincare(hrvPercentageValues)
                val fft = HrvFeatureExtractor.fFftMetrics(hrvPercentageValues, fs = 500f)
                val dfa = HrvFeatureExtractor.dfaMetrics(hrvPercentageValues)
                val tri = HrvFeatureExtractor.fTriangular(hrvPercentageValues)
                val fCd = HrvFeatureExtractor.fCd(hrvPercentageValues)
                val fSampen = HrvFeatureExtractor.fSampen(hrvPercentageValues, m = 2, r = 0.2f)
                val fApen = HrvFeatureExtractor.fApen(hrvPercentageValues)
                val shann = HrvFeatureExtractor.fShann(hrvPercentageValues)
                val fM1 = HrvFeatureExtractor.fM1(hrvPercentageValues)
                val fM2 = HrvFeatureExtractor.fM2(hrvPercentageValues)
                val fM3 = HrvFeatureExtractor.fM3(hrvPercentageValues)
                val fAutoc = HrvFeatureExtractor.fAutoc(hrvPercentageValues)

                binding.textFhrValue.text = if (fHr.isNaN()) "f_HR: 계산 불가" else String.format("f_HR: %.2f bpm", fHr)
                binding.textFsdnnValue.text = if (fSdnn.isNaN()) "f_SDNN: 계산 불가" else String.format("f_SDNN: %.4f s", fSdnn)
                binding.textFrmssdValue.text = if (fRmssd.isNaN()) "f_RMSSD: 계산 불가" else String.format("f_RMSSD: %.4f s", fRmssd)

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

                binding.textFcdValue.text = if (fCd.isNaN()) "f_cd: 계산 불가" else String.format("f_cd: %.4f", fCd)
                binding.textFsampenValue.text = if (fSampen.isNaN()) "f_sampen: 계산 불가" else String.format("f_sampen: %.4f", fSampen)
                binding.textFapenValue.text = if (fApen.isNaN()) "f_apen: 계산 불가" else String.format("f_apen: %.4f", fApen)

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

                latestFeatureCsv = buildFeatureCsv(
                    insum = binding.editInsum.text?.toString().orEmpty(),
                    age = binding.editAge.text?.toString().orEmpty(),
                    genderCode = getGenderCode(),
                    fHr = fHr,
                    fSdnn = fSdnn,
                    fRmssd = fRmssd,
                    fPnn10 = fPnn10,
                    fPnn20 = fPnn20,
                    fPnn30 = fPnn30,
                    fPnn40 = fPnn40,
                    fPnn50 = fPnn50,
                    fSd1 = poincare.sd1,
                    fSd2 = poincare.sd2,
                    fSd1Sd2 = poincare.sd1Sd2Ratio,
                    fPLf = fft.pLf,
                    fPHf = fft.pHf,
                    fLfHf = fft.lfHfRatio,
                    fVLf = fft.vLf,
                    fLf = fft.lf,
                    fHf = fft.hf,
                    fAlpha1 = dfa.alpha1,
                    fAlpha2 = dfa.alpha2,
                    fCd = fCd,
                    fTri = tri.tri,
                    fTinn = tri.tinn,
                    fApen = fApen,
                    fSampen = fSampen,
                    fShann1 = shann.shann1,
                    fShann2 = shann.shann2,
                    fM1 = fM1,
                    fM3 = fM3,
                    fAutoc = fAutoc
                )
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

    private fun getGenderCode(): String {
        return if (binding.radioFemale.isChecked) "2" else "0"
    }

    private fun formatCsvValue(value: Float): String {
        return if (value.isNaN() || value.isInfinite()) "" else String.format("%.6f", value)
    }

    private fun buildFeatureCsv(
        insum: String,
        age: String,
        genderCode: String,
        fHr: Float,
        fSdnn: Float,
        fRmssd: Float,
        fPnn10: Float,
        fPnn20: Float,
        fPnn30: Float,
        fPnn40: Float,
        fPnn50: Float,
        fSd1: Float,
        fSd2: Float,
        fSd1Sd2: Float,
        fPLf: Float,
        fPHf: Float,
        fLfHf: Float,
        fVLf: Float,
        fLf: Float,
        fHf: Float,
        fAlpha1: Float,
        fAlpha2: Float,
        fCd: Float,
        fTri: Float,
        fTinn: Float,
        fApen: Float,
        fSampen: Float,
        fShann1: Float,
        fShann2: Float,
        fM1: Float,
        fM3: Float,
        fAutoc: Float
    ): String {
        val header = listOf(
            "INSUM", "f_HR", "f_SDNN", "f_RMSSD", "f_pNN10", "f_pNN20", "f_pNN30", "f_pNN40", "f_pNN50",
            "f_SD1", "f_SD2", "f_SD1SD2", "f_pLF", "f_pHF", "f_LFHF", "f_VLF", "f_LF", "f_HF",
            "f_alpha1", "f_alpha2", "f_cd", "f_TRI", "f_TINN", "f_apen", "f_sampen", "f_shann1", "f_shann2",
            "AGE", "GENDER", "f_m1", "f_m3", "f_autoc"
        ).joinToString(",")

        val row = listOf(
            insum,
            formatCsvValue(fHr),
            formatCsvValue(fSdnn),
            formatCsvValue(fRmssd),
            formatCsvValue(fPnn10),
            formatCsvValue(fPnn20),
            formatCsvValue(fPnn30),
            formatCsvValue(fPnn40),
            formatCsvValue(fPnn50),
            formatCsvValue(fSd1),
            formatCsvValue(fSd2),
            formatCsvValue(fSd1Sd2),
            formatCsvValue(fPLf),
            formatCsvValue(fPHf),
            formatCsvValue(fLfHf),
            formatCsvValue(fVLf),
            formatCsvValue(fLf),
            formatCsvValue(fHf),
            formatCsvValue(fAlpha1),
            formatCsvValue(fAlpha2),
            formatCsvValue(fCd),
            formatCsvValue(fTri),
            formatCsvValue(fTinn),
            formatCsvValue(fApen),
            formatCsvValue(fSampen),
            formatCsvValue(fShann1),
            formatCsvValue(fShann2),
            age,
            genderCode,
            formatCsvValue(fM1),
            formatCsvValue(fM3),
            formatCsvValue(fAutoc)
        ).joinToString(",")

        return "$header\n$row\n"
    }

    private fun saveFeatureCsv(uri: Uri) {
        val csvText = latestFeatureCsv ?: return

        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(csvText)
            }
        }.onSuccess {
            Toast.makeText(this, "HRV Feature CSV 저장 완료", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "CSV 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
