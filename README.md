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


## 빌드 오류 해결 (중요)
`Execution failed for task ':app:dataBindingMergeDependencyArtifactsDebug'` 오류가 나는 경우,
대부분 `MPAndroidChart` 의존성 저장소(`jitpack.io`)가 빠져서 발생합니다.
이 프로젝트는 `settings.gradle.kts`의 `repositories`에 아래를 포함하도록 수정되어 있습니다.

```kotlin
maven(url = "https://jitpack.io")
```
