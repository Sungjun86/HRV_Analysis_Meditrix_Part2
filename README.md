# CSV Graph Android App (Kotlin)

Android Studio에서 바로 열 수 있는 간단한 샘플 프로젝트입니다.

## 기능
- 기기에서 HRV CSV 파일 선택
- CSV에서 `time,value` 또는 RR 간격(단일 값) 형태 파싱
- HRV 값을 **4Hz(0.25s)** 로 선형 보간(Interpolation)
- 보간 결과를 그래프에 표시 (X축: sec, Y축: HRV 값)
- 보간 결과 CSV(`time_sec,hrv_value`)를 앱 하단에 미리보기 출력
- X/Y 축 라벨 표시, 핀치 줌, 한 손가락 드래그 이동

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
   - 4Hz 보간 그래프 표시
   - 하단 텍스트 영역에 보간 CSV 프리뷰 표시

## 핵심 코드
- 파싱: `CsvParser.parseHrvSamples`
- 보간: `HrvInterpolator.interpolateToFrequency(..., 4f)`
- CSV 출력: `HrvInterpolator.toCsv`
- 그래프: `LineGraphView.setValues(values, startXSec, stepXSec)`
