# 🔍 Loupe

> No more "is this really purple?" — just tap and see

모바일 앱 내에서 동작하는 디자인 인스펙터입니다.
화면의 아무 컴포넌트나 탭하면 컬러값, 디자인 토큰, 패딩/마진, 타이포그래피를 즉시 확인할 수 있습니다.

웹 브라우저의 DevTools Inspect Element 경험을 모바일 앱에 가져옵니다.

## 플랫폼 지원 범위

| 플랫폼 | 상태 |
|---|---|
| **Android** (XML + Jetpack Compose) | ✅ 지원 중 |
| **iOS** (UIKit + SwiftUI) | 🔜 지원 예정 |
| **Flutter** | 🔜 지원 예정 |

## 기능

### 🎯 인스펙트 모드

탭 한 번으로 뷰의 디자인 속성을 확인합니다.

- **컬러 인스펙션** — 배경색, 텍스트 컬러, 틴트 컬러를 HEX로 표시
- **컬러 토큰 매칭** — `#FF5F0080` → `purple_500 (Primary)` 자동 변환
- **타이포그래피 토큰 매칭** — fontSize + fontWeight → `bold16` 자동 변환
- **패딩 / 마진** — 초록(패딩), 주황(마진) 영역 시각화 + dp 수치
- **뷰 사이즈** — width × height (dp)
- **Compose 지원** — Compose 노드 자동 인스펙션 + `.inspectable()` Modifier 태깅

### 📐 측정 모드

두 뷰를 순서대로 탭하면 간격을 측정합니다.

- **GAP 측정** — 겹치지 않을 때: 가장 가까운 변 사이 간격 (dp)
- **EDGE 측정** — 겹칠 때: 각 변 간 거리 (left↔left, top↔top, ...)
- **겹침 영역** — 수평/수직 겹침 크기 표시

### 🟢 뷰 타입 인디케이터

화면 상단에 현재 화면의 구성을 표시합니다.

- `Compose` / `XML` / `Compose 62% · XML 38%`
- 탭 전환, Fragment 교체 시 자동 업데이트

### 🫧 플로팅 토글 버튼

- 드래그로 위치 이동 (좌/우 가장자리 자동 스냅)
- 싱글 탭: 인스펙터 on/off
- Activity 전환 시 자동으로 따라감

## 시작하기

### 1. 모듈 추가

```kotlin
// settings.gradle.kts
include(":loupe:android")

// app/build.gradle.kts
dependencies {
    debugImplementation(project(":loupe:android"))
}
```

### 2. 초기화

```kotlin
// Application.onCreate()
if (BuildConfig.DEBUG) {
    DesignInspector.init(
        application = this,
        colorTokens = YourDesignTokens.colors(),          // 선택
        typographyTokens = YourDesignTokens.typography(),  // 선택
    )
}
```

### 3. 활성화

```kotlin
// Debug 설정 화면에서
DesignInspector.showToggle(activity)  // 플로팅 버튼 표시
DesignInspector.hideToggle()          // 플로팅 버튼 숨김

// Compose UI
LoupeToggle()  // 토글 스위치 Composable
```

### 4. 토큰 등록

**컬러 토큰**

```kotlin
val colorTokens = listOf(
    ColorToken("purple_500", "Primary", 0xFF5F0080.toInt(), "메인 브랜드 컬러"),
    ColorToken("gray_900",   "Gray",    0xFF212121.toInt()),
    // ...
)
```

**타이포그래피 토큰**

```kotlin
val typographyTokens = listOf(
    TypographyToken(sizeSp = 16, weight = 700, name = "bold16"),
    TypographyToken(sizeSp = 14, weight = 400, name = "regular14"),
    // ...
)
```

미등록 컬러는 HEX만 표시됩니다. Figma 토큰을 등록하면 `→ purple_500 (Primary)` 형태로 매칭됩니다.

## 모듈 구조

```
loupe/
└── android/
    └── src/main/java/com/kurly/loupe/
        ├── DesignInspector.kt                ← 진입점 (싱글톤)
        ├── core/
        │   ├── DesignInfo.kt                 ← 추출 데이터 모델
        │   ├── ViewInspector.kt              ← XML View 속성 추출
        │   ├── ComposeNodeInspector.kt       ← Compose 노드 인스펙션
        │   ├── ComposeInspectorModifiers.kt  ← .inspectable() 확장
        │   ├── InspectorMode.kt              ← INSPECT / MEASURE 모드
        │   └── MeasurementResult.kt          ← 뷰 간 간격 측정 결과
        ├── token/
        │   ├── ColorTokenRegistry.kt         ← 컬러 토큰 레지스트리
        │   ├── TypographyTokenRegistry.kt    ← 타이포 토큰 레지스트리
        │   └── KurlyDesignTokens.kt          ← 토큰 정의 샘플
        ├── ui/
        │   ├── InspectorOverlayView.kt       ← 오버레이 렌더링
        │   ├── FloatingToggleButton.kt       ← 플로팅 토글 버튼
        │   ├── ModeChipView.kt               ← 모드 전환 칩
        │   ├── ViewTypeIndicatorBar.kt       ← XML/Compose 비율 바
        │   ├── DebugSettingsCompose.kt       ← Compose 토글 UI
        │   └── InspectorToggleFragment.kt    ← XML 토글 UI
        └── util/
            └── DimensionUtil.kt
```

## 릴리즈 빌드 안전성

`debugImplementation`으로 추가하므로 **release 빌드에는 코드가 포함되지 않습니다.**

## 라이선스

TBD
