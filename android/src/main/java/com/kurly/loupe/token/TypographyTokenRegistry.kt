package com.kurly.loupe.token

/**
 * 타이포그래피 토큰 레지스트리.
 *
 * fontSize(sp) + fontWeight → 토큰명 매칭.
 * 프로젝트의 디자인 시스템에 맞는 토큰을 외부에서 등록하세요.
 *
 * 사용법:
 * ```
 * // 앱 초기화 시 등록
 * TypographyTokenRegistry.register(sizeSp = 16, weight = 700, name = "bold16")
 * // 또는 일괄 등록
 * TypographyTokenRegistry.registerTokens(listOf(
 *     TypographyToken(sizeSp = 16, weight = 700, name = "bold16"),
 *     TypographyToken(sizeSp = 14, weight = 400, name = "regular14"),
 * ))
 *
 * // 조회
 * TypographyTokenRegistry.resolve(fontSize = 16f, fontWeight = 700)
 * // → "bold16"
 * ```
 */
object TypographyTokenRegistry {

    private data class TypoKey(val sizeSp: Int, val weight: Int)

    private val tokenMap = mutableMapOf<TypoKey, String>()

    fun registerTokens(tokens: List<TypographyToken>) {
        tokens.forEach { register(it) }
    }

    fun register(token: TypographyToken) {
        tokenMap[TypoKey(token.sizeSp, normalizeWeight(token.weight))] = token.name
    }

    fun register(sizeSp: Int, weight: Int, name: String) {
        tokenMap[TypoKey(sizeSp, normalizeWeight(weight))] = name
    }

    fun clear() {
        tokenMap.clear()
    }

    /**
     * fontSize(sp) + fontWeight → 토큰명.
     */
    fun resolve(fontSize: Float?, fontWeight: Int?): String? {
        if (fontSize == null) return null
        val size = fontSize.toInt()
        val weight = normalizeWeight(fontWeight ?: 400)
        return tokenMap[TypoKey(size, weight)]
    }

    /**
     * fontSize(sp) + fontWeight 문자열 → 토큰명.
     */
    fun resolve(fontSize: Float?, fontWeightStr: String?): String? {
        val weight = fontWeightStr?.toIntOrNull() ?: weightFromName(fontWeightStr)
        return resolve(fontSize, weight)
    }

    /**
     * fontWeight 정규화.
     * 500 이하 → 400, 501~650 → 600, 651+ → 700
     */
    private fun normalizeWeight(weight: Int): Int = when {
        weight <= 500 -> 400
        weight <= 650 -> 600
        else -> 700
    }

    private fun weightFromName(name: String?): Int = when (name?.lowercase()) {
        "bold" -> 700
        "semibold", "semi-bold", "semi_bold" -> 600
        "normal", "regular" -> 400
        else -> 400
    }
}

/**
 * 타이포그래피 토큰 정의.
 */
data class TypographyToken(
    val sizeSp: Int,
    val weight: Int,
    val name: String,
)
