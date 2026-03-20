# 🔍 Design Inspector Module

Android 앱 내에서 최상위 윈도우로 뜨는 디자인 인스펙터입니다.  
화면의 아무 뷰나 터치하면 **컬러값, 패딩/마진, 디자인 토큰명**을 즉시 확인할 수 있습니다.

## 기능

| 기능 | 설명 |
|------|------|
| 🎨 컬러 인스펙션 | 배경색, 텍스트 컬러, 틴트 컬러를 HEX 값으로 표시 |
| 🏷️ 컬러 토큰 매칭 | `#FF5F0080` → `purple_500 (Primary)` 토큰명 자동 매칭 |
| 📐 패딩 & 마진 | 시각적으로 패딩(초록)/마진(주황) 영역 표시 + dp 수치 |
| 📏 사이즈 | 뷰의 width × height (dp) |
| 🔤 텍스트 속성 | 폰트 크기(sp), weight, line height |
| 📱 하이브리드 지원 | XML View + Jetpack Compose 모두 지원 |

## 세팅 방법

### 1. 모듈 추가

`design-inspector/` 폴더를 프로젝트 루트에 복사하고 `settings.gradle.kts`에 추가:

```kotlin
// settings.gradle.kts
include(":design-inspector")
```

### 2. 앱 모듈 의존성 추가

```kotlin
// app/build.gradle.kts
dependencies {
    // Debug 빌드에서만 포함 (release에는 미포함)
    debugImplementation(project(":design-inspector"))
}
```

### 3. 토큰 등록 (Application)

```kotlin
class KurlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            // 기본 Kurly 토큰 사용
            DesignInspector.init()

            // 또는 커스텀 토큰 등록
            // DesignInspector.init(customTokens = MyDesignTokens.allTokens())
        }
    }
}
```

### 4. Debug 설정 화면에 토글 추가

**Compose 화면인 경우:**
```kotlin
@Composable
fun DebugSettingsScreen() {
    Column {
        Text("Debug Settings", style = MaterialTheme.typography.headlineSmall)
        DesignInspectorToggle()  // ← 이것만 추가!
    }
}
```

**XML 화면인 경우:**
```xml
<fragment
    android:name="com.kurly.designinspector.ui.InspectorToggleFragment"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

**또는 코드에서 직접:**
```kotlin
// 켜기
DesignInspector.show(activity)
// 끄기
DesignInspector.hide()
// 토글
DesignInspector.toggle(activity)
```

## 컬러 토큰 커스터마이징

`KurlyDesignTokens.kt`에 프로젝트의 디자인 토큰을 등록하세요.  
Figma에서 export한 값을 그대로 넣으면 됩니다.

```kotlin
// 예시: 토큰 추가
ColorToken(
    name = "purple_500",       // 토큰 이름
    group = "Primary",          // 그룹명
    colorInt = 0xFF5F0080.toInt(), // ARGB 컬러값
    description = "메인 브랜드 컬러",
)
```

인스펙터가 활성화되면, 뷰의 컬러값이 등록된 토큰과 자동 매칭되어 표시됩니다:
```
Background   ■ #FF5F0080
             → purple_500 (Primary)
```

## Compose 뷰에서 토큰 태깅

Compose에서는 `.inspectable()` Modifier로 명시적으로 토큰 정보를 태깅할 수 있습니다:

```kotlin
Box(
    modifier = Modifier
        .background(KurlyTheme.colors.purple500)
        .padding(16.dp)
        .inspectable(
            backgroundColor = KurlyTheme.colors.purple500.toArgb(),
            tokenName = "purple_500",
            padding = "16, 16, 16, 16"
        )
) {
    // ...
}
```

## 파일 구조

```
design-inspector/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/kurly/designinspector/
│   │   ├── DesignInspector.kt              ← 메인 매니저 (싱글톤)
│   │   ├── core/
│   │   │   ├── DesignInfo.kt               ← 데이터 모델
│   │   │   ├── ViewInspector.kt            ← View 속성 추출기
│   │   │   └── ComposeInspectorModifiers.kt ← Compose Modifier 확장
│   │   ├── token/
│   │   │   ├── ColorTokenRegistry.kt       ← 토큰 레지스트리
│   │   │   └── KurlyDesignTokens.kt        ← 컬러 토큰 정의 (커스터마이즈!)
│   │   ├── ui/
│   │   │   ├── InspectorOverlayView.kt     ← 오버레이 렌더링
│   │   │   ├── DebugSettingsCompose.kt     ← Compose 토글 UI
│   │   │   └── InspectorToggleFragment.kt  ← XML Fragment 토글 UI
│   │   └── util/
│   │       └── DimensionUtil.kt
│   └── res/layout/
│       └── view_inspector_toggle.xml
└── README.md
```

## Release 빌드에서 제외하기

`debugImplementation`으로 의존성을 추가했으므로 release APK에는 포함되지 않습니다.  
만약 코드 레벨에서도 분리하려면:

```kotlin
// app/src/debug/java/.../DebugInitializer.kt
object DebugInitializer {
    fun init(app: Application) {
        DesignInspector.init()
    }
}

// app/src/release/java/.../DebugInitializer.kt
object DebugInitializer {
    fun init(app: Application) { /* no-op */ }
}
```
