package com.kurly.loupe.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.kurly.loupe.core.ColorInfo
import com.kurly.loupe.core.DesignInfo
import com.kurly.loupe.core.InspectorMode
import com.kurly.loupe.core.MeasurementResult
import com.kurly.loupe.util.DimensionUtil

/**
 * 선택된 뷰의 바운딩 박스, 패딩/마진 영역, 속성 팝업을 그리는 오버레이 뷰
 */
@SuppressLint("ViewConstructor")
class InspectorOverlayView(
    context: Context,
    private val onViewTapped: (x: Int, y: Int) -> Unit,
    private val onDismiss: () -> Unit,
) : View(context) {

    // ── 현재 인스펙션 대상 ────────────────────
    private var designInfo: DesignInfo? = null
    private var animProgress = 0f

    // ── 측정 모드 상태 ──────────────────────
    var mode: InspectorMode = InspectorMode.INSPECT
    private var firstSelectionBounds: Rect? = null
    private var measurementResult: MeasurementResult? = null

    // ── 페인트 ──────────────────────────────
    private val boundsStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = DimensionUtil.dpToPx(context, 1.5f)
        color = COLOR_BOUNDS
    }
    private val paddingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_PADDING
    }
    private val marginFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_MARGIN
    }
    private val dimPaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0x44000000
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xF0202020.toInt()
        setShadowLayer(DimensionUtil.dpToPx(context, 8f), 0f, 2f, 0x40000000)
    }
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = DimensionUtil.dpToPx(context, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val popupLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt()
        textSize = DimensionUtil.dpToPx(context, 10f)
    }
    private val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TOKEN_BADGE
        textSize = DimensionUtil.dpToPx(context, 11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val colorSwatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── 측정 모드 페인트 ──────────────────
    private val measureLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_MEASURE_LINE
        strokeWidth = DimensionUtil.dpToPx(context, 1.5f)
    }
    private val measureCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_MEASURE_LINE
        strokeWidth = DimensionUtil.dpToPx(context, 1.5f)
    }
    private val measureLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_MEASURE_LABEL_BG
    }
    private val measureLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = DimensionUtil.dpToPx(context, 11f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
    }
    private val firstSelectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_MEASURE_LINE
        strokeWidth = DimensionUtil.dpToPx(context, 2f)
        pathEffect = DashPathEffect(floatArrayOf(
            DimensionUtil.dpToPx(context, 6f),
            DimensionUtil.dpToPx(context, 4f),
        ), 0f)
    }
    private val waitingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_MEASURE_LINE
        textSize = DimensionUtil.dpToPx(context, 12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val sizeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BOUNDS
        textSize = DimensionUtil.dpToPx(context, 10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    // ── Anim ────────────────────────────────
    private val showAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 200
        addUpdateListener {
            animProgress = it.animatedValue as Float
            invalidate()
        }
    }

    fun showInfo(info: DesignInfo) {
        designInfo = info
        animProgress = 0f
        showAnimator.start()
    }

    fun clearInfo() {
        designInfo = null
        invalidate()
    }

    // ── 측정 모드 공개 메서드 ────────────────

    fun showFirstSelection(bounds: Rect) {
        firstSelectionBounds = bounds
        measurementResult = null
        designInfo = null
        invalidate()
    }

    fun showMeasurement(result: MeasurementResult) {
        measurementResult = result
        firstSelectionBounds = null
        designInfo = null
        animProgress = 0f
        showAnimator.start()
    }

    fun clearMeasurement() {
        firstSelectionBounds = null
        measurementResult = null
        invalidate()
    }

    // ── Touch ───────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // 팝업 외부 터치 시 새로운 뷰 탐색
            onViewTapped(event.rawX.toInt(), event.rawY.toInt())
            return true
        }
        return super.onTouchEvent(event)
    }

    // ── Draw ────────────────────────────────

    /**
     * 오버레이 뷰의 화면 좌표 오프셋.
     * bounds(getLocationOnScreen 기준)를 캔버스 로컬 좌표로 변환할 때 사용.
     * Fold 디바이스 등에서 오버레이가 screen (0,0)에서 시작하지 않을 수 있다.
     */
    private val overlayOffset = IntArray(2)

    private fun screenToLocal(rect: Rect): Rect {
        getLocationOnScreen(overlayOffset)
        return Rect(
            rect.left - overlayOffset[0],
            rect.top - overlayOffset[1],
            rect.right - overlayOffset[0],
            rect.bottom - overlayOffset[1],
        )
    }

    private fun screenToLocalX(x: Float): Float {
        getLocationOnScreen(overlayOffset)
        return x - overlayOffset[0]
    }

    private fun screenToLocalY(y: Float): Float {
        getLocationOnScreen(overlayOffset)
        return y - overlayOffset[1]
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 측정 모드 그리기
        if (mode == InspectorMode.MEASURE) {
            drawMeasureMode(canvas)
            return
        }

        val info = designInfo ?: return
        val density = resources.displayMetrics.density
        val alpha = (animProgress * 255).toInt()

        // screen 좌표 → 캔버스 로컬 좌표
        val bounds = screenToLocal(info.bounds)

        // 1) 화면 전체 살짝 어둡게
        dimPaint.alpha = (0x44 * animProgress).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // 바운딩 영역은 어둡지 않게 (클리어)
        canvas.save()
        canvas.clipRect(bounds, Region.Op.DIFFERENCE)
        canvas.restore()

        // 2) 마진 영역 (바운딩 박스 바깥)
        val ml = info.marginLeft * density
        val mt = info.marginTop * density
        val mr = info.marginRight * density
        val mb = info.marginBottom * density
        if (ml > 0 || mt > 0 || mr > 0 || mb > 0) {
            marginFillPaint.alpha = (0x44 * animProgress).toInt()
            val marginRect = RectF(
                bounds.left - ml, bounds.top - mt,
                bounds.right + mr, bounds.bottom + mb,
            )
            canvas.drawRect(marginRect, marginFillPaint)
        }

        // 3) 패딩 영역 (바운딩 박스 안쪽)
        val pl = info.paddingLeft * density
        val pt = info.paddingTop * density
        val pr = info.paddingRight * density
        val pb = info.paddingBottom * density
        if (pl > 0 || pt > 0 || pr > 0 || pb > 0) {
            paddingFillPaint.alpha = (0x55 * animProgress).toInt()
            // Top padding
            canvas.drawRect(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.top + pt, paddingFillPaint
            )
            // Bottom padding
            canvas.drawRect(
                bounds.left.toFloat(), bounds.bottom - pb,
                bounds.right.toFloat(), bounds.bottom.toFloat(), paddingFillPaint
            )
            // Left padding
            canvas.drawRect(
                bounds.left.toFloat(), bounds.top + pt,
                bounds.left + pl, bounds.bottom - pb, paddingFillPaint
            )
            // Right padding
            canvas.drawRect(
                bounds.right - pr, bounds.top + pt,
                bounds.right.toFloat(), bounds.bottom - pb, paddingFillPaint
            )
        }

        // 4) 바운딩 박스 테두리
        boundsStrokePaint.alpha = alpha
        canvas.drawRect(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), boundsStrokePaint
        )

        // 5) 사이즈 라벨 (가로 x 세로)
        sizeTextPaint.alpha = alpha
        val sizeLabel = "${info.widthDp} × ${info.heightDp} dp"
        canvas.drawText(
            sizeLabel,
            bounds.exactCenterX(),
            bounds.bottom + DimensionUtil.dpToPx(context, 14f),
            sizeTextPaint
        )

        // 6) 패딩 / 마진 수치 라벨 (축약)
        drawSpacingLabels(canvas, info, bounds, alpha)

        // 7) 속성 정보 팝업
        drawInfoPopup(canvas, info, bounds)
    }

    /**
     * 패딩/마진 수치를 뷰 가장자리에 표시
     */
    private fun drawSpacingLabels(canvas: Canvas, info: DesignInfo, bounds: Rect, alpha: Int) {
        val density = resources.displayMetrics.density
        val labelPaint = Paint(popupLabelPaint).apply { this.alpha = alpha; textAlign = Paint.Align.CENTER }
        val labelSize = DimensionUtil.dpToPx(context, 9f)
        labelPaint.textSize = labelSize

        // Padding labels (초록)
        labelPaint.color = COLOR_PADDING_TEXT
        labelPaint.alpha = alpha
        if (info.paddingTop > 0) {
            canvas.drawText("P:${info.paddingTop}", bounds.exactCenterX(),
                bounds.top + info.paddingTop * density / 2 + labelSize / 2, labelPaint)
        }
        if (info.paddingBottom > 0) {
            canvas.drawText("P:${info.paddingBottom}", bounds.exactCenterX(),
                bounds.bottom - info.paddingBottom * density / 2 + labelSize / 2, labelPaint)
        }

        // Margin labels (주황)
        labelPaint.color = COLOR_MARGIN_TEXT
        labelPaint.alpha = alpha
        if (info.marginTop > 0) {
            canvas.drawText("M:${info.marginTop}", bounds.exactCenterX(),
                bounds.top - info.marginTop * density / 2 + labelSize / 2, labelPaint)
        }
        if (info.marginBottom > 0) {
            canvas.drawText("M:${info.marginBottom}", bounds.exactCenterX(),
                bounds.bottom + info.marginBottom * density + DimensionUtil.dpToPx(context, 20f), labelPaint)
        }
    }

    /**
     * 팝업 정보 패널 그리기
     */
    private fun drawInfoPopup(canvas: Canvas, info: DesignInfo, bounds: Rect) {
        val dp = { v: Float -> DimensionUtil.dpToPx(context, v) }
        val popupWidth = dp(240f)
        val lineHeight = dp(18f)
        val sectionGap = dp(10f)
        val swatchSize = dp(14f)
        val padding = dp(14f)
        val cornerRadius = dp(10f)

        // 팝업에 들어갈 줄 수 계산
        val lines = buildPopupLines(info)
        val popupHeight = padding * 2 + lineHeight * lines.size + sectionGap * countSections(lines)

        // 팝업 위치: 뷰 위쪽, 화면 밖이면 아래쪽
        var popupX = (bounds.exactCenterX() - popupWidth / 2).coerceIn(dp(8f), width - popupWidth - dp(8f))
        var popupY = bounds.top - popupHeight - dp(12f)
        if (popupY < dp(48f)) {
            popupY = bounds.bottom + dp(12f)
        }

        // 애니메이션 적용
        val scale = 0.9f + 0.1f * animProgress
        val popupAlpha = (animProgress * 255).toInt()

        canvas.save()
        canvas.translate(popupX + popupWidth / 2, popupY + popupHeight / 2)
        canvas.scale(scale, scale)
        canvas.translate(-popupWidth / 2, -popupHeight / 2)

        // 배경
        popupBgPaint.alpha = (0xF0 * animProgress).toInt()
        val popupRect = RectF(0f, 0f, popupWidth, popupHeight)
        canvas.drawRoundRect(popupRect, cornerRadius, cornerRadius, popupBgPaint)

        // 텍스트 렌더링
        var y = padding + lineHeight * 0.8f
        for (line in lines) {
            when (line) {
                is PopupLine.Header -> {
                    popupTextPaint.alpha = popupAlpha
                    popupTextPaint.textSize = dp(13f)
                    popupTextPaint.typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                    canvas.drawText(line.text, padding, y, popupTextPaint)
                    popupTextPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                is PopupLine.LabelValue -> {
                    popupLabelPaint.alpha = popupAlpha
                    popupTextPaint.alpha = popupAlpha
                    popupTextPaint.textSize = dp(12f)
                    canvas.drawText(line.label, padding, y, popupLabelPaint)
                    canvas.drawText(line.value, padding + dp(72f), y, popupTextPaint)
                }
                is PopupLine.ColorRow -> {
                    popupLabelPaint.alpha = popupAlpha
                    popupTextPaint.alpha = popupAlpha
                    popupTextPaint.textSize = dp(12f)

                    canvas.drawText(line.label, padding, y, popupLabelPaint)

                    // 컬러 스와치
                    val swatchX = padding + dp(72f)
                    colorSwatchPaint.color = line.colorInfo.colorInt
                    colorSwatchPaint.alpha = popupAlpha
                    canvas.drawRoundRect(
                        swatchX, y - swatchSize + dp(2f),
                        swatchX + swatchSize, y + dp(2f),
                        dp(3f), dp(3f), colorSwatchPaint
                    )

                    // Hex
                    canvas.drawText(
                        line.colorInfo.hexString,
                        swatchX + swatchSize + dp(6f), y, popupTextPaint
                    )

                    // 토큰 이름이 있으면 다음 줄에
                    if (line.colorInfo.tokenName != null) {
                        y += lineHeight
                        tokenPaint.alpha = popupAlpha
                        val tokenText = "→ ${line.colorInfo.tokenName}"
                        val groupText = line.colorInfo.tokenGroup?.let { " ($it)" } ?: ""
                        canvas.drawText(
                            tokenText + groupText,
                            padding + dp(72f), y, tokenPaint
                        )
                    }
                }
                is PopupLine.Separator -> {
                    y += sectionGap * 0.5f
                }
            }
            y += lineHeight
        }

        canvas.restore()
    }

    /**
     * 팝업에 표시할 정보 줄 생성
     */
    private fun buildPopupLines(info: DesignInfo): List<PopupLine> {
        val lines = mutableListOf<PopupLine>()

        // 뷰 타입 & ID
        val title = buildString {
            append(info.viewClassName)
            info.viewId?.let { append("  #$it") }
        }
        lines.add(PopupLine.Header(title))
        lines.add(PopupLine.Separator)

        // 컬러 섹션
        info.backgroundColor?.let {
            lines.add(PopupLine.ColorRow("Background", it))
        }
        info.textColor?.let {
            lines.add(PopupLine.ColorRow("Text Color", it))
        }
        info.tintColor?.let {
            lines.add(PopupLine.ColorRow("Tint", it))
        }

        if (info.backgroundColor != null || info.textColor != null) {
            lines.add(PopupLine.Separator)
        }

        // 패딩 & 마진
        val padStr = "${info.paddingLeft}, ${info.paddingTop}, ${info.paddingRight}, ${info.paddingBottom}"
        lines.add(PopupLine.LabelValue("Padding", "$padStr dp"))

        val marStr = "${info.marginLeft}, ${info.marginTop}, ${info.marginRight}, ${info.marginBottom}"
        lines.add(PopupLine.LabelValue("Margin", "$marStr dp"))

        lines.add(PopupLine.LabelValue("Size", "${info.widthDp} × ${info.heightDp} dp"))

        // 텍스트 정보
        info.textSizeSp?.let {
            lines.add(PopupLine.Separator)
            lines.add(PopupLine.LabelValue("Font Size", "${String.format("%.1f", it)} sp"))
            info.fontWeight?.let { w -> lines.add(PopupLine.LabelValue("Weight", w)) }
            info.lineHeightSp?.let { lh ->
                lines.add(PopupLine.LabelValue("Line H", "${String.format("%.1f", lh)} sp"))
            }
            info.typographyToken?.let { token ->
                lines.add(PopupLine.LabelValue("TextStyle", "→ $token"))
            }
        }

        // 코너 라디우스
        info.cornerRadiusDp?.let {
            if (it > 0) lines.add(PopupLine.LabelValue("Radius", "${String.format("%.1f", it)} dp"))
        }

        return lines
    }

    private fun countSections(lines: List<PopupLine>): Int =
        lines.count { it is PopupLine.Separator }

    // ── 팝업 줄 타입 ────────────────────────
    private sealed class PopupLine {
        data class Header(val text: String) : PopupLine()
        data class LabelValue(val label: String, val value: String) : PopupLine()
        data class ColorRow(val label: String, val colorInfo: ColorInfo) : PopupLine()
        data object Separator : PopupLine()
    }

    // ═══════════════════════════════════════
    // 측정 모드 그리기
    // ═══════════════════════════════════════

    private fun drawMeasureMode(canvas: Canvas) {
        val firstBounds = firstSelectionBounds
        val result = measurementResult

        if (result != null) {
            drawMeasurementResult(canvas, result)
        } else if (firstBounds != null) {
            drawFirstSelection(canvas, firstBounds)
        }
    }

    /**
     * 첫 번째 요소 선택 상태: 대시 박스 + "두 번째 요소를 탭하세요" 안내
     */
    private fun drawFirstSelection(canvas: Canvas, bounds: Rect) {
        val local = screenToLocal(bounds)

        dimPaint.alpha = 0x33
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        canvas.drawRect(
            local.left.toFloat(), local.top.toFloat(),
            local.right.toFloat(), local.bottom.toFloat(),
            firstSelectionPaint,
        )

        val label = "두 번째 요소를 탭하세요"
        val textY = local.top - DimensionUtil.dpToPx(context, 12f)
        canvas.drawText(label, local.exactCenterX(), textY, waitingTextPaint)
    }

    /**
     * 두 요소 간 측정 결과: 양쪽 바운딩 박스 + 측정선 + dp 라벨
     */
    private fun drawMeasurementResult(canvas: Canvas, result: MeasurementResult) {
        val alpha = (animProgress * 255).toInt()
        getLocationOnScreen(overlayOffset)
        val ox = overlayOffset[0].toFloat()
        val oy = overlayOffset[1].toFloat()

        dimPaint.alpha = (0x44 * animProgress).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        boundsStrokePaint.alpha = alpha
        val first = screenToLocal(result.firstBounds)
        val second = screenToLocal(result.secondBounds)
        canvas.drawRect(RectF(
            first.left.toFloat(), first.top.toFloat(),
            first.right.toFloat(), first.bottom.toFloat(),
        ), boundsStrokePaint)
        canvas.drawRect(RectF(
            second.left.toFloat(), second.top.toFloat(),
            second.right.toFloat(), second.bottom.toFloat(),
        ), boundsStrokePaint)

        measureLinePaint.alpha = alpha
        measureCapPaint.alpha = alpha
        measureLabelTextPaint.alpha = alpha
        measureLabelBgPaint.alpha = (0xE0 * animProgress).toInt()

        val capH = DimensionUtil.dpToPx(context, 5f)

        if (result.hasGap) {
            // ── 비겹침: 가장 가까운 변 사이 간격 ──
            if (result.horizontalGapDp > 0) {
                val sx = result.hLineStart.x - ox; val sy = result.hLineStart.y - oy
                val ex = result.hLineEnd.x - ox; val ey = result.hLineEnd.y - oy
                canvas.drawLine(sx, sy, ex, ey, measureLinePaint)
                canvas.drawLine(sx, sy - capH, sx, sy + capH, measureCapPaint)
                canvas.drawLine(ex, ey - capH, ex, ey + capH, measureCapPaint)
                drawMeasureLabel(canvas, "${result.horizontalGapDp} dp", (sx + ex) / 2, sy)
            }
            if (result.verticalGapDp > 0) {
                val sx = result.vLineStart.x - ox; val sy = result.vLineStart.y - oy
                val ex = result.vLineEnd.x - ox; val ey = result.vLineEnd.y - oy
                canvas.drawLine(sx, sy, ex, ey, measureLinePaint)
                canvas.drawLine(sx - capH, sy, sx + capH, sy, measureCapPaint)
                canvas.drawLine(ex - capH, ey, ex + capH, ey, measureCapPaint)
                drawMeasureLabel(canvas, "${result.verticalGapDp} dp", sx, (sy + ey) / 2)
            }
        } else {
            // ── 겹침: 각 변 간 거리를 측정선으로 표시 ──
            drawEdgeMeasurements(canvas, first, second, result, capH)
        }
    }

    /**
     * 겹침 시 하위(작은) 요소 기준으로 상위(큰) 요소까지의 상하좌우 거리를 표시.
     *
     * ┌── outer ──────────────┐
     * │         ↑ top          │
     * │  ←left  ┌─inner─┐ right→│
     * │         └───────┘      │
     * │         ↓ bottom       │
     * └────────────────────────┘
     */
    private fun drawEdgeMeasurements(
        canvas: Canvas,
        first: Rect,
        second: Rect,
        result: MeasurementResult,
        capH: Float,
    ) {
        // 면적이 작은 쪽이 inner (하위), 큰 쪽이 outer (상위)
        val firstArea = first.width().toLong() * first.height().toLong()
        val secondArea = second.width().toLong() * second.height().toLong()
        val inner = if (firstArea <= secondArea) first else second
        val outer = if (firstArea <= secondArea) second else first

        val innerCx = inner.exactCenterX()
        val innerCy = inner.exactCenterY()

        // left: inner 좌측 → outer 좌측
        val leftPx = inner.left - outer.left
        if (leftPx > 0) {
            val y = innerCy
            canvas.drawLine(outer.left.toFloat(), y, inner.left.toFloat(), y, measureLinePaint)
            canvas.drawLine(outer.left.toFloat(), y - capH, outer.left.toFloat(), y + capH, measureCapPaint)
            canvas.drawLine(inner.left.toFloat(), y - capH, inner.left.toFloat(), y + capH, measureCapPaint)
            drawMeasureLabel(canvas, "${result.edgeLeftDp} dp", (outer.left + inner.left) / 2f, y)
        }

        // right: inner 우측 → outer 우측
        val rightPx = outer.right - inner.right
        if (rightPx > 0) {
            val y = innerCy
            canvas.drawLine(inner.right.toFloat(), y, outer.right.toFloat(), y, measureLinePaint)
            canvas.drawLine(inner.right.toFloat(), y - capH, inner.right.toFloat(), y + capH, measureCapPaint)
            canvas.drawLine(outer.right.toFloat(), y - capH, outer.right.toFloat(), y + capH, measureCapPaint)
            drawMeasureLabel(canvas, "${result.edgeRightDp} dp", (inner.right + outer.right) / 2f, y)
        }

        // top: inner 상단 → outer 상단
        val topPx = inner.top - outer.top
        if (topPx > 0) {
            val x = innerCx
            canvas.drawLine(x, outer.top.toFloat(), x, inner.top.toFloat(), measureLinePaint)
            canvas.drawLine(x - capH, outer.top.toFloat(), x + capH, outer.top.toFloat(), measureCapPaint)
            canvas.drawLine(x - capH, inner.top.toFloat(), x + capH, inner.top.toFloat(), measureCapPaint)
            drawMeasureLabel(canvas, "${result.edgeTopDp} dp", x, (outer.top + inner.top) / 2f)
        }

        // bottom: inner 하단 → outer 하단
        val bottomPx = outer.bottom - inner.bottom
        if (bottomPx > 0) {
            val x = innerCx
            canvas.drawLine(x, inner.bottom.toFloat(), x, outer.bottom.toFloat(), measureLinePaint)
            canvas.drawLine(x - capH, inner.bottom.toFloat(), x + capH, inner.bottom.toFloat(), measureCapPaint)
            canvas.drawLine(x - capH, outer.bottom.toFloat(), x + capH, outer.bottom.toFloat(), measureCapPaint)
            drawMeasureLabel(canvas, "${result.edgeBottomDp} dp", x, (inner.bottom + outer.bottom) / 2f)
        }
    }

    /**
     * 측정값 라벨: 둥근 배경 + 흰색 텍스트
     */
    private fun drawMeasureLabel(canvas: Canvas, text: String, cx: Float, cy: Float) {
        val padH = DimensionUtil.dpToPx(context, 6f)
        val padV = DimensionUtil.dpToPx(context, 3f)
        val textWidth = measureLabelTextPaint.measureText(text)
        val textHeight = measureLabelTextPaint.textSize
        val margin = DimensionUtil.dpToPx(context, 4f)

        // 화면 안에 들어오도록 위치 보정
        val halfW = textWidth / 2 + padH
        val halfH = textHeight / 2 + padV
        val clampedCx = cx.coerceIn(halfW + margin, width - halfW - margin)
        val clampedCy = cy.coerceIn(halfH + margin, height - halfH - margin)

        val bgRect = RectF(
            clampedCx - textWidth / 2 - padH,
            clampedCy - textHeight / 2 - padV,
            clampedCx + textWidth / 2 + padH,
            clampedCy + textHeight / 2 + padV,
        )
        val radius = DimensionUtil.dpToPx(context, 4f)
        canvas.drawRoundRect(bgRect, radius, radius, measureLabelBgPaint)

        val textY = clampedCy - (measureLabelTextPaint.descent() + measureLabelTextPaint.ascent()) / 2
        canvas.drawText(text, clampedCx, textY, measureLabelTextPaint)
    }

    companion object {
        // 바운딩 박스
        private const val COLOR_BOUNDS = 0xFF5F0080.toInt()          // Kurly purple

        // 패딩 영역 (초록 계열)
        private const val COLOR_PADDING = 0x5500C853.toInt()
        private const val COLOR_PADDING_TEXT = 0xFF00C853.toInt()

        // 마진 영역 (주황 계열)
        private const val COLOR_MARGIN = 0x55FF6D00.toInt()
        private const val COLOR_MARGIN_TEXT = 0xFFFF6D00.toInt()

        // 토큰 배지
        private const val COLOR_TOKEN_BADGE = 0xFFCE93D8.toInt()

        // 측정 모드
        private const val COLOR_MEASURE_LINE = 0xFF448AFF.toInt()      // blue
        private const val COLOR_MEASURE_LABEL_BG = 0xE01565C0.toInt()  // dark blue
    }
}
