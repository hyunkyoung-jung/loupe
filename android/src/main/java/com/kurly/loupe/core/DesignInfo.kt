package com.kurly.loupe.core

import android.graphics.Rect

/**
 * 뷰에서 추출한 디자인 속성 정보
 */
data class DesignInfo(
    val viewClassName: String,
    val viewId: String?,
    val bounds: Rect,

    // 컬러
    val backgroundColor: ColorInfo?,
    val textColor: ColorInfo?,
    val tintColor: ColorInfo?,

    // 패딩 & 마진 (dp)
    val paddingLeft: Int,
    val paddingTop: Int,
    val paddingRight: Int,
    val paddingBottom: Int,
    val marginLeft: Int,
    val marginTop: Int,
    val marginRight: Int,
    val marginBottom: Int,

    // 텍스트
    val textSizeSp: Float?,
    val fontWeight: String?,
    val letterSpacingSp: Float?,
    val lineHeightSp: Float?,
    /** 매칭된 KPDS 타이포그래피 토큰명 (예: "bold16", "regular14") */
    val typographyToken: String? = null,

    // 추가 정보
    val widthDp: Int,
    val heightDp: Int,
    val alpha: Float,
    val elevation: Float,
    val cornerRadiusDp: Float?,

    // Compose 전용
    val isComposeNode: Boolean = false,
    val composeModifiers: List<String> = emptyList(),
)

/**
 * 컬러 정보 + 토큰 매핑
 */
data class ColorInfo(
    val colorInt: Int,
    val hexString: String,
    val tokenName: String?,    // 매칭된 디자인 토큰 이름 (예: "primary_500")
    val tokenGroup: String?,   // 토큰 그룹 (예: "Primary")
) {
    companion object {
        fun from(colorInt: Int, tokenName: String? = null, tokenGroup: String? = null): ColorInfo {
            val hex = String.format("#%08X", colorInt)
            return ColorInfo(
                colorInt = colorInt,
                hexString = hex,
                tokenName = tokenName,
                tokenGroup = tokenGroup,
            )
        }
    }
}
