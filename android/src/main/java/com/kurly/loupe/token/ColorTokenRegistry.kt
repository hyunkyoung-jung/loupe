package com.kurly.loupe.token

import com.kurly.loupe.core.ColorInfo

/**
 * 디자인 토큰 정의.
 * 프로젝트의 디자인 시스템에 맞게 수정하세요.
 */
data class ColorToken(
    val name: String,       // 예: "purple_500"
    val group: String,      // 예: "Primary"
    val colorInt: Int,      // 0xFFxxxxxx (ARGB)
    val description: String = "",
)

/**
 * 컬러 토큰 레지스트리.
 *
 * 사용법:
 * ```
 * // 1) 앱 시작 시 토큰 등록
 * ColorTokenRegistry.registerTokens(KurlyDesignTokens.allTokens())
 *
 * // 2) 색상값으로 토큰 조회
 * val info = ColorTokenRegistry.resolve(0xFF5F0080.toInt())
 * // → ColorInfo(hex="#FF5F0080", tokenName="purple_500", tokenGroup="Primary")
 * ```
 */
object ColorTokenRegistry {

    private val tokenMap = mutableMapOf<Int, ColorToken>()

    // alpha 무시하고 RGB만 비교하기 위한 보조 맵
    private val rgbMap = mutableMapOf<Int, ColorToken>()

    /**
     * 토큰 목록을 일괄 등록
     */
    fun registerTokens(tokens: List<ColorToken>) {
        tokens.forEach { token ->
            tokenMap[token.colorInt] = token
            rgbMap[token.colorInt and 0x00FFFFFF] = token
        }
    }

    /**
     * 단일 토큰 등록
     */
    fun register(token: ColorToken) {
        tokenMap[token.colorInt] = token
        rgbMap[token.colorInt and 0x00FFFFFF] = token
    }

    /**
     * 등록된 토큰 전체 초기화
     */
    fun clear() {
        tokenMap.clear()
        rgbMap.clear()
    }

    /**
     * colorInt → ColorInfo 변환 (토큰 매칭 포함)
     */
    fun resolve(colorInt: Int): ColorInfo {
        // 1) 정확한 ARGB 매치
        val exact = tokenMap[colorInt]
        if (exact != null) {
            return ColorInfo.from(colorInt, exact.name, exact.group)
        }

        // 2) RGB만 매치 (alpha 무시)
        val rgbOnly = rgbMap[colorInt and 0x00FFFFFF]
        if (rgbOnly != null) {
            return ColorInfo.from(colorInt, rgbOnly.name, rgbOnly.group)
        }

        // 3) 매칭 실패 → 토큰 없이 반환
        return ColorInfo.from(colorInt)
    }

    /**
     * 등록된 전체 토큰 목록 (디버그용)
     */
    fun allTokens(): List<ColorToken> = tokenMap.values.toList()
}
