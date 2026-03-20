package com.kurly.loupe.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kurly.loupe.token.ColorTokenRegistry

// ── Semantics Keys ─────────────────────────
// Compose 뷰의 디자인 속성을 semantics에 태깅해서 인스펙터가 읽을 수 있게 합니다.

val DesignTokenKey = SemanticsPropertyKey<String>("DesignToken")
var SemanticsPropertyReceiver.designToken by DesignTokenKey

val DesignBackgroundColorKey = SemanticsPropertyKey<Int>("DesignBgColor")
var SemanticsPropertyReceiver.designBackgroundColor by DesignBackgroundColorKey

val DesignTextColorKey = SemanticsPropertyKey<Int>("DesignTextColor")
var SemanticsPropertyReceiver.designTextColor by DesignTextColorKey

val DesignPaddingKey = SemanticsPropertyKey<String>("DesignPadding")
var SemanticsPropertyReceiver.designPadding by DesignPaddingKey

/**
 * Compose에서 디자인 속성을 인스펙터에 노출하는 Modifier.
 *
 * 사용 예:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .background(KurlyTheme.colors.purple500)
 *         .padding(16.dp)
 *         .inspectable(
 *             backgroundColor = KurlyTheme.colors.purple500.toArgb(),
 *             tokenName = "purple_500",
 *             padding = "16, 16, 16, 16"
 *         )
 * )
 * ```
 *
 * 인스펙터가 비활성일 때는 no-op이므로 성능에 영향 없습니다.
 */
fun Modifier.inspectable(
    backgroundColor: Int? = null,
    textColor: Int? = null,
    tokenName: String? = null,
    padding: String? = null,
): Modifier {
    return this.semantics {
        tokenName?.let { designToken = it }
        backgroundColor?.let { designBackgroundColor = it }
        textColor?.let { designTextColor = it }
        padding?.let { designPadding = it }
    }
}

/**
 * Compose 뷰의 레이아웃 좌표를 인스펙터에 보고하는 Modifier.
 * ComposeView가 XML 레이아웃 안에 embedded 되어 있을 때 유용합니다.
 */
fun Modifier.reportToInspector(
    tag: String,
    onPositioned: (LayoutCoordinates) -> Unit = {},
): Modifier {
    return this.onGloballyPositioned { coordinates ->
        onPositioned(coordinates)
    }
}
