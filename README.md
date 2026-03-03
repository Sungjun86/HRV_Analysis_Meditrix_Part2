# CSV Graph Android App (Kotlin)

Android Studio에서 바로 열 수 있는 간단한 샘플 프로젝트입니다.

## 기능
- 기기에서 CSV 파일 선택
- 각 행에서 첫 번째 숫자 값을 추출
- MPAndroidChart `LineChart`로 시각화

## CSV 예시
```csv
time,value
0,72.4
1,75.2
2,71.8
3,80.1
```

## 사용 방법
1. Android Studio에서 프로젝트 열기
2. Gradle Sync 실행
3. 앱 실행 후 **CSV 불러오기** 버튼 선택
4. CSV 파일을 선택하면 그래프에 표시

## 핵심 코드
- 파일 선택: `ActivityResultContracts.OpenDocument`
- 파싱: `CsvParser.parseNumericSeries`
- 그래프: `LineDataSet`, `LineChart`
