# CSV Graph Android App (Kotlin)

Android Studio에서 바로 열 수 있는 간단한 샘플 프로젝트입니다.

## 처리 순서
1. 기기에서 HRV CSV 파일 선택
2. CSV에서 `time,value` 또는 RR 간격(단일 값) 형태 파싱
3. 원신호에 20% Percentage Filter 적용
4. 필터 신호를 `HRV_Percentage`로 정의
5. `HRV_Percentage`를 **4Hz(0.25s)** Cubic Spline 보간
6. 보간 신호에 Linear Detrend 적용
7. Detrend 결과를 `HRV_Interpolation`으로 정의

## 기능
- 그래프 2개 표시
  - 상단: 원본 CSV 그래프
  - 하단: 4Hz Interpolation 그래프
- 하단 그래프 오버레이
  - 파랑: 4Hz 보간(`HRV_Percentage` 기반)
  - 빨강: `HRV_Interpolation` (Detrend 결과)
- 각 그래프는 화면 높이의 약 **25%** 크기
- X/Y 축 라벨 표시, 핀치 줌, 한 손가락 드래그 이동
- `HRV_Interpolation` 결과를 `CSV` 파일로 저장 버튼 제공
- f_HR(평균 HR) 계산 후 화면 텍스트로 표시 (Input: `HRV_Percentage`)
- f_SDNN(표준편차, SDNN) 계산 후 화면 텍스트로 표시 (Input: `HRV_Percentage`)
- f_RMSSD 계산 후 화면 텍스트로 표시 (Input: `HRV_Percentage`)
- f_pNN10, f_pNN20, f_pNN30, f_pNN40, f_pNN50 화면 텍스트로 표시 (Input: `HRV_Percentage`)

## CSV 예시
```csv
time_sec,rr_ms
0.0,820
1.0,840
2.0,790
3.0,810
```

또는
```csv
820
840
790
810
```
(단일 컬럼일 때는 RR(ms) 누적으로 시간축 생성)

## 핵심 코드
- 파싱: `CsvParser.parseHrvSamples`
- 필터: `HrvSignalProcessor.apply20PercentFilter`
- 보간: `HrvInterpolator.interpolateTo4HzCubicSpline(...)`
- Detrend: `HrvSignalProcessor.detrendLinear`
- HR 파라미터: `HrvFeatureExtractor.fHrAverage`
- SDNN 파라미터: `HrvFeatureExtractor.fSdnn`
- RMSSD 파라미터: `HrvFeatureExtractor.fRmssd`
- pNN 파라미터: `HrvFeatureExtractor.fPnn10`~`fPnn50`
- 저장: `ActivityResultContracts.CreateDocument` + `HrvInterpolator.toCsv`
