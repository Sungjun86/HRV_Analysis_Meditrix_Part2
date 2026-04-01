package com.example.csvgraph

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.csvgraph.databinding.ActivityMainBinding
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var latestProcessedMetrics: ProcessedMetrics? = null

    companion object {
        private const val MAX_PROCESSING_SAMPLES = 20000
    }

    private data class ProcessedMetrics(
        val rawValues: List<Float>,
        val startXSec: Float,
        val stepXSec: Float,
        val fHr: Float,
        val fSdnn: Float,
        val fRmssd: Float,
        val fPnn10: Float,
        val fPnn20: Float,
        val fPnn30: Float,
        val fPnn40: Float,
        val fPnn50: Float,
        val poincare: HrvFeatureExtractor.PoincareMetrics,
        val fft: HrvFeatureExtractor.FftMetrics,
        val dfa: HrvFeatureExtractor.DfaMetrics,
        val tri: HrvFeatureExtractor.TriangularMetrics,
        val fCd: Float,
        val fSampen: Float,
        val fApen: Float,
        val shann: HrvFeatureExtractor.ShannMetrics,
        val fM1: Float,
        val fM2: Float,
        val fM3: Float,
        val fAutoc: Float,
        val originalSampleCount: Int,
        val usedSampleCount: Int
    )

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

        setupInputControls()
        setLoadingState(false)

        binding.buttonLoadCsv.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/csv"))
        }

        binding.buttonSaveFeatureCsv.setOnClickListener {
            if (!isInputReady() || latestProcessedMetrics == null) {
                Toast.makeText(this, "INSUM/AGE/GENDER 입력 후 CSV를 불러와 Feature를 계산하세요.", Toast.LENGTH_SHORT).show()
            } else {
                createFeatureCsvLauncher.launch("hrv_features.csv")
            }
        }
    }

    private fun setupInputControls() {
        val insumOptions = listOf("INSUM 선택") + (1..30).map { it.toString() }
        val ageOptions = listOf("AGE 선택") + (1..120).map { it.toString() }

        binding.spinnerInsum.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, insumOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerAge.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ageOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSaveButtonEnabled()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                updateSaveButtonEnabled()
            }
        }

        binding.spinnerInsum.onItemSelectedListener = spinnerListener
        binding.spinnerAge.onItemSelectedListener = spinnerListener
        binding.radioGender.clearCheck()
        binding.radioGender.setOnCheckedChangeListener { _, _ -> updateSaveButtonEnabled() }
        updateSaveButtonEnabled()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressProcessing.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonLoadCsv.isEnabled = !isLoading
        updateSaveButtonEnabled(isLoading)
    }

    private fun isInputReady(): Boolean {
        val insum = getSelectedInsum()
        val age = getSelectedAge()
        val gender = getGenderCode()
        return insum.isNotEmpty() && age.isNotEmpty() && gender.isNotEmpty()
    }

    private fun updateSaveButtonEnabled(isLoading: Boolean = binding.progressProcessing.visibility == View.VISIBLE) {
        binding.buttonSaveFeatureCsv.isEnabled = !isLoading && isInputReady() && latestProcessedMetrics != null
    }

    private fun renderCsv(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        setLoadingState(true)

        Thread {
            runCatching { processCsv(uri) }
                .onSuccess { metrics ->
                    runOnUiThread {
                        try {
                            if (metrics == null) {
                                Toast.makeText(this, "HRV 데이터가 충분하지 않습니다. (최소 2개)", Toast.LENGTH_SHORT).show()
                                latestProcessedMetrics = null
                                return@runOnUiThread
                            }
                            latestProcessedMetrics = metrics
                            renderMetrics(metrics)
                            if (metrics.originalSampleCount > metrics.usedSampleCount) {
                                Toast.makeText(this, "대용량 CSV로 인해 ${metrics.originalSampleCount}개 중 ${metrics.usedSampleCount}개 샘플로 처리했습니다.", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            setLoadingState(false)
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        try {
                            latestProcessedMetrics = null
                            Toast.makeText(this, "CSV 처리 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            setLoadingState(false)
                        }
                    }
                }
        }.start()
    }

    private fun downsampleSamples(samples: List<HrvSample>, maxSamples: Int): List<HrvSample> {
        if (samples.size <= maxSamples) return samples
        val stride = ceil(samples.size / maxSamples.toDouble()).toInt().coerceAtLeast(1)
        val downsampled = ArrayList<HrvSample>(maxSamples)
        var i = 0
        while (i < samples.size) {
            downsampled.add(samples[i])
            i += stride
        }
        return downsampled
    }

    private fun processCsv(uri: Uri): ProcessedMetrics? {
        val parsedSamples = CsvParser.parseHrvSamples(contentResolver, uri, MAX_PROCESSING_SAMPLES)
        if (parsedSamples.size < 2) return null

        val rawSamples = downsampleSamples(parsedSamples, MAX_PROCESSING_SAMPLES)

        val rawStep = if (rawSamples.size > 1) {
            ((rawSamples.last().timeSec - rawSamples.first().timeSec) / (rawSamples.size - 1)).coerceAtLeast(0.0001f)
        } else {
            1f
        }

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
        val fft = HrvFeatureExtractor.fFftMetrics(hrvPercentageValues, fs = 4f)
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

        return ProcessedMetrics(
            rawValues = rawSamples.map { it.value },
            startXSec = rawSamples.first().timeSec,
            stepXSec = rawStep,
            fHr = fHr,
            fSdnn = fSdnn,
            fRmssd = fRmssd,
            fPnn10 = fPnn10,
            fPnn20 = fPnn20,
            fPnn30 = fPnn30,
            fPnn40 = fPnn40,
            fPnn50 = fPnn50,
            poincare = poincare,
            fft = fft,
            dfa = dfa,
            tri = tri,
            fCd = fCd,
            fSampen = fSampen,
            fApen = fApen,
            shann = shann,
            fM1 = fM1,
            fM2 = fM2,
            fM3 = fM3,
            fAutoc = fAutoc,
            originalSampleCount = parsedSamples.size,
            usedSampleCount = rawSamples.size
        )
    }

    private fun renderMetrics(metrics: ProcessedMetrics) {
        binding.rawGraphView.setValues(
            newValues = metrics.rawValues,
            startXSec = metrics.startXSec,
            stepXSec = metrics.stepXSec
        )

        binding.textFhrValue.text = if (metrics.fHr.isNaN()) "f_HR: 계산 불가" else String.format("f_HR: %.2f bpm", metrics.fHr)
        binding.textFsdnnValue.text = if (metrics.fSdnn.isNaN()) "f_SDNN: 계산 불가" else String.format("f_SDNN: %.4f s", metrics.fSdnn)
        binding.textFrmssdValue.text = if (metrics.fRmssd.isNaN()) "f_RMSSD: 계산 불가" else String.format("f_RMSSD: %.4f s", metrics.fRmssd)

        binding.textFpnnValues.text = if (
            metrics.fPnn10.isNaN() && metrics.fPnn20.isNaN() && metrics.fPnn30.isNaN() && metrics.fPnn40.isNaN() && metrics.fPnn50.isNaN()
        ) {
            "f_pNN10: 계산 불가\n" +
                "f_pNN20: 계산 불가\n" +
                "f_pNN30: 계산 불가\n" +
                "f_pNN40: 계산 불가\n" +
                "f_pNN50: 계산 불가"
        } else {
            String.format(
                "f_pNN10: %.4f\nf_pNN20: %.4f\nf_pNN30: %.4f\nf_pNN40: %.4f\nf_pNN50: %.4f",
                metrics.fPnn10, metrics.fPnn20, metrics.fPnn30, metrics.fPnn40, metrics.fPnn50
            )
        }

        binding.textPoincareValues.text = if (
            metrics.poincare.sd1.isNaN() && metrics.poincare.sd2.isNaN() && metrics.poincare.sd1Sd2Ratio.isNaN()
        ) {
            "f_SD1: 계산 불가\nf_SD2: 계산 불가\nf_SD1SD2: 계산 불가"
        } else {
            String.format(
                "f_SD1: %.4f\nf_SD2: %.4f\nf_SD1SD2: %.4f",
                metrics.poincare.sd1,
                metrics.poincare.sd2,
                metrics.poincare.sd1Sd2Ratio
            )
        }

        binding.textFftValues.text = if (
            metrics.fft.pLf.isNaN() && metrics.fft.pHf.isNaN() && metrics.fft.lfHfRatio.isNaN() &&
            metrics.fft.vLf.isNaN() && metrics.fft.lf.isNaN() && metrics.fft.hf.isNaN()
        ) {
            "f_pLF: 계산 불가\nf_pHF: 계산 불가\nf_LFHF: 계산 불가\nf_VLF: 계산 불가\nf_LF: 계산 불가\nf_HF: 계산 불가"
        } else {
            String.format(
                "f_pLF: %.4f\nf_pHF: %.4f\nf_LFHF: %.4f\nf_VLF: %.4f\nf_LF: %.4f\nf_HF: %.4f",
                metrics.fft.pLf,
                metrics.fft.pHf,
                metrics.fft.lfHfRatio,
                metrics.fft.vLf,
                metrics.fft.lf,
                metrics.fft.hf
            )
        }

        binding.textFalphaValue.text = if (metrics.dfa.alpha1.isNaN() && metrics.dfa.alpha2.isNaN()) {
            "f_alpha_1: 계산 불가\nf_alpha_2: 계산 불가"
        } else {
            String.format("f_alpha_1: %.4f\nf_alpha_2: %.4f", metrics.dfa.alpha1, metrics.dfa.alpha2)
        }

        binding.textFtriTinnValue.text = if (metrics.tri.tri.isNaN() && metrics.tri.tinn.isNaN()) {
            "f_TRI: 계산 불가\nf_TINN: 계산 불가"
        } else {
            String.format("f_TRI: %.4f\nf_TINN: %.4f s", metrics.tri.tri, metrics.tri.tinn)
        }

        binding.textFcdValue.text = if (metrics.fCd.isNaN()) "f_cd: 계산 불가" else String.format("f_cd: %.4f", metrics.fCd)
        binding.textFsampenValue.text = if (metrics.fSampen.isNaN()) "f_sampen: 계산 불가" else String.format("f_sampen: %.4f", metrics.fSampen)
        binding.textFapenValue.text = if (metrics.fApen.isNaN()) "f_apen: 계산 불가" else String.format("f_apen: %.4f", metrics.fApen)

        binding.textFshannValues.text = if (metrics.shann.shann1.isNaN() && metrics.shann.shann2.isNaN()) {
            "f_shann1: 계산 불가\nf_shann2: 계산 불가"
        } else {
            String.format("f_shann1: %.4f\nf_shann2: %.4f", metrics.shann.shann1, metrics.shann.shann2)
        }

        binding.textFmValues.text = if (metrics.fM1.isNaN() && metrics.fM2.isNaN() && metrics.fM3.isNaN()) {
            "f_m1: 계산 불가\nf_m2: 계산 불가\nf_m3: 계산 불가"
        } else {
            String.format("f_m1: %.4f\nf_m2: %.4f\nf_m3: %.4f", metrics.fM1, metrics.fM2, metrics.fM3)
        }

        binding.textFautocValue.text = if (metrics.fAutoc.isNaN()) {
            "f_autoc: 계산 불가"
        } else {
            String.format("f_autoc: %.4f", metrics.fAutoc)
        }

        updateSaveButtonEnabled()
    }

    private fun toRrSeconds(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        val probeSize = minOf(values.size, 1024)
        val probe = values.subList(0, probeSize)
        val mean = probe.sum() / probeSize.toFloat()
        val assumeMs = mean > 10f
        return if (assumeMs) values.map { it / 1000f } else values
    }

    private fun getSelectedInsum(): String {
        return binding.spinnerInsum.selectedItem?.toString()?.takeIf { it != "INSUM 선택" } ?: ""
    }

    private fun getSelectedAge(): String {
        return binding.spinnerAge.selectedItem?.toString()?.takeIf { it != "AGE 선택" } ?: ""
    }

    private fun getGenderCode(): String {
        return when (binding.radioGender.checkedRadioButtonId) {
            binding.radioMale.id -> "0"
            binding.radioFemale.id -> "2"
            else -> ""
        }
    }

    private fun formatCsvValue(value: Float): String {
        return if (value.isNaN() || value.isInfinite()) "" else String.format("%.3f", value)
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
        val metrics = latestProcessedMetrics ?: return
        val insum = getSelectedInsum()
        val age = getSelectedAge()
        val genderCode = getGenderCode()
        if (insum.isEmpty() || age.isEmpty() || genderCode.isEmpty()) return

        val csvText = buildFeatureCsv(
            insum = insum,
            age = age,
            genderCode = genderCode,
            fHr = metrics.fHr,
            fSdnn = metrics.fSdnn,
            fRmssd = metrics.fRmssd,
            fPnn10 = metrics.fPnn10,
            fPnn20 = metrics.fPnn20,
            fPnn30 = metrics.fPnn30,
            fPnn40 = metrics.fPnn40,
            fPnn50 = metrics.fPnn50,
            fSd1 = metrics.poincare.sd1,
            fSd2 = metrics.poincare.sd2,
            fSd1Sd2 = metrics.poincare.sd1Sd2Ratio,
            fPLf = metrics.fft.pLf,
            fPHf = metrics.fft.pHf,
            fLfHf = metrics.fft.lfHfRatio,
            fVLf = metrics.fft.vLf,
            fLf = metrics.fft.lf,
            fHf = metrics.fft.hf,
            fAlpha1 = metrics.dfa.alpha1,
            fAlpha2 = metrics.dfa.alpha2,
            fCd = metrics.fCd,
            fTri = metrics.tri.tri,
            fTinn = metrics.tri.tinn,
            fApen = metrics.fApen,
            fSampen = metrics.fSampen,
            fShann1 = metrics.shann.shann1,
            fShann2 = metrics.shann.shann2,
            fM1 = metrics.fM1,
            fM3 = metrics.fM3,
            fAutoc = metrics.fAutoc
        )

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
