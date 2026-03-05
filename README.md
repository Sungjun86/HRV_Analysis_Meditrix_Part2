# CSV Graph Android App (Kotlin)

Android Studio에서 바로 열 수 있는 간단한 샘플 프로젝트입니다.

## 기능
- 기기에서 HRV CSV 파일 선택
- CSV에서 `time,value` 또는 RR 간격(단일 값) 형태 파싱
- HRV 값을 **4Hz(0.25s)** 로 Spline 보간(Interpolation)
- Interpolation 데이터에 20% Percentage Filter 적용
- Filter 결과에 Linear Detrend 적용
- Interpolation 그래프에 원본(파랑) + 처리결과(빨강) 오버레이 표시
- 그래프 2개 표시
  - 상단: 원본 CSV 그래프
  - 하단: 4Hz Interpolation 그래프
- 각 그래프는 화면 높이의 약 **25%** 크기
- X/Y 축 라벨 표시, 핀치 줌, 한 손가락 드래그 이동
- 4Hz interpolation 결과를 `CSV` 파일로 저장 버튼 제공
- f_HR(평균 HR) 계산 후 화면 텍스트로 표시
- f_SDNN(표준편차, SDNN) 계산 후 화면 텍스트로 표시

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

## 사용 방법
1. Android Studio에서 프로젝트 열기
2. Gradle Sync 실행
3. 앱 실행 후 **CSV 불러오기** 버튼 선택
4. HRV CSV 파일을 선택하면
   - 원본 CSV 그래프(상단) 표시
   - 4Hz 보간 그래프(하단) 표시
5. **4Hz CSV 저장** 버튼을 눌러 보간 결과를 파일로 저장

## 핵심 코드
- 파싱: `CsvParser.parseHrvSamples`
- 보간: `HrvInterpolator.interpolateTo4HzCubicSpline(...)` (Natural Cubic Spline)
- 그래프: `rawGraphView`, `interpolatedGraphView`
- 후처리: `HrvSignalProcessor.apply20PercentFilter` + `HrvSignalProcessor.detrendLinear`
- HR 파라미터: `HrvFeatureExtractor.fHrAverage`
- SDNN 파라미터: `HrvFeatureExtractor.fSdnn`
- 저장: `ActivityResultContracts.CreateDocument` + `HrvInterpolator.toCsv`
