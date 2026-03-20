package com.kurly.loupe.token

/**
 * 컬리 디자인 시스템 컬러 토큰 예시.
 * 실제 디자인 시스템의 토큰 값으로 교체하세요.
 *
 * Figma / 디자인 시스템 문서에서 export한 값을 여기에 등록하면
 * 인스펙터에서 "#FF5F0080" 대신 "purple_500 (Primary)" 로 표시됩니다.
 */
object KurlyDesignTokens {

    fun allTokens(): List<ColorToken> = listOf(
        // ── Primary (Purple) ──────────────────────
        ColorToken("purple_50",  "Primary", 0xFFF5EAFA.toInt(), "가장 밝은 퍼플"),
        ColorToken("purple_100", "Primary", 0xFFE0B8F5.toInt()),
        ColorToken("purple_200", "Primary", 0xFFC880EB.toInt()),
        ColorToken("purple_300", "Primary", 0xFFAF48E0.toInt()),
        ColorToken("purple_400", "Primary", 0xFF9720D6.toInt()),
        ColorToken("purple_500", "Primary", 0xFF5F0080.toInt(), "메인 브랜드 컬러"),
        ColorToken("purple_600", "Primary", 0xFF4D0068.toInt()),
        ColorToken("purple_700", "Primary", 0xFF3B0050.toInt()),
        ColorToken("purple_800", "Primary", 0xFF290038.toInt()),
        ColorToken("purple_900", "Primary", 0xFF170020.toInt()),

        // ── Grayscale ─────────────────────────────
        ColorToken("white",      "Gray", 0xFFFFFFFF.toInt()),
        ColorToken("gray_50",    "Gray", 0xFFFAFAFA.toInt()),
        ColorToken("gray_100",   "Gray", 0xFFF5F5F5.toInt()),
        ColorToken("gray_200",   "Gray", 0xFFEEEEEE.toInt()),
        ColorToken("gray_300",   "Gray", 0xFFE0E0E0.toInt()),
        ColorToken("gray_400",   "Gray", 0xFFBDBDBD.toInt()),
        ColorToken("gray_500",   "Gray", 0xFF9E9E9E.toInt()),
        ColorToken("gray_600",   "Gray", 0xFF757575.toInt()),
        ColorToken("gray_700",   "Gray", 0xFF616161.toInt()),
        ColorToken("gray_800",   "Gray", 0xFF424242.toInt()),
        ColorToken("gray_900",   "Gray", 0xFF212121.toInt()),
        ColorToken("black",      "Gray", 0xFF000000.toInt()),

        // ── Semantic ──────────────────────────────
        ColorToken("red_500",    "Semantic", 0xFFF44336.toInt(), "Error / 경고"),
        ColorToken("green_500",  "Semantic", 0xFF4CAF50.toInt(), "Success / 완료"),
        ColorToken("blue_500",   "Semantic", 0xFF2196F3.toInt(), "Info / 링크"),
        ColorToken("orange_500", "Semantic", 0xFFFF9800.toInt(), "Warning / 주의"),

        // ── Background ────────────────────────────
        ColorToken("bg_primary",   "Background", 0xFFFFFFFF.toInt(), "메인 배경"),
        ColorToken("bg_secondary", "Background", 0xFFF5F5F5.toInt(), "보조 배경"),
        ColorToken("bg_tertiary",  "Background", 0xFFEEEEEE.toInt(), "카드 배경"),

        // ── Text ──────────────────────────────────
        ColorToken("text_primary",   "Text", 0xFF1C1C1C.toInt(), "본문 텍스트"),
        ColorToken("text_secondary", "Text", 0xFF6B6B6B.toInt(), "보조 텍스트"),
        ColorToken("text_tertiary",  "Text", 0xFFB5B5B5.toInt(), "비활성 텍스트"),
        ColorToken("text_on_primary","Text", 0xFFFFFFFF.toInt(), "퍼플 위 텍스트"),
    )
}
