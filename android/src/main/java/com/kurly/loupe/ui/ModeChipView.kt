package com.kurly.loupe.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.kurly.loupe.core.InspectorMode
import com.kurly.loupe.util.DimensionUtil

/**
 * FAB 위에 표시되는 모드 전환 칩 버튼.
 *
 * 현재 모드(Inspect / Measure)를 텍스트로 표시하고,
 * 탭하면 모드를 전환합니다.
 */
@SuppressLint("ViewConstructor")
class ModeChipView(
    context: Context,
    private val onSwitch: () -> Unit,
) : View(context) {

    private val dp = { v: Float -> DimensionUtil.dpToPx(context, v) }

    private val chipHeight = dp(26f)
    private val chipWidth = dp(72f)
    private val cornerRadius = dp(13f)

    var mode: InspectorMode = InspectorMode.INSPECT
        set(value) {
            field = value
            invalidate()
        }

    // ── Paint ──────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(dp(4f), 0f, dp(1f), 0x40000000)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
            return true
        }
        if (event.action == MotionEvent.ACTION_UP) {
            animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            onSwitch()
            return true
        }
        if (event.action == MotionEvent.ACTION_CANCEL) {
            animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = chipWidth
        val h = chipHeight

        // 배경
        bgPaint.color = when (mode) {
            InspectorMode.INSPECT -> COLOR_INSPECT
            InspectorMode.MEASURE -> COLOR_MEASURE
        }
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bgPaint)

        // 아이콘 + 텍스트
        val iconAreaW = dp(14f)
        val totalContentW = iconAreaW + dp(4f) + textPaint.measureText(label())
        val startX = (w - totalContentW) / 2
        val cy = h / 2

        when (mode) {
            InspectorMode.INSPECT -> drawSmallMagnifier(canvas, startX + iconAreaW / 2, cy)
            InspectorMode.MEASURE -> drawSmallRuler(canvas, startX + iconAreaW / 2, cy)
        }

        val textX = startX + iconAreaW + dp(4f) + textPaint.measureText(label()) / 2
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label(), textX, textY, textPaint)
    }

    private fun label(): String = when (mode) {
        InspectorMode.INSPECT -> "Inspect"
        InspectorMode.MEASURE -> "Measure"
    }

    private fun drawSmallMagnifier(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(4f)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawCircle(cx - dp(1f), cy - dp(1f), r, iconPaint)
        val hx = cx - dp(1f) + r * 0.7f
        val hy = cy - dp(1f) + r * 0.7f
        canvas.drawLine(hx, hy, hx + dp(3f), hy + dp(3f), iconPaint)
    }

    private fun drawSmallRuler(canvas: Canvas, cx: Float, cy: Float) {
        val halfW = dp(5f)
        val capH = dp(3f)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawLine(cx - halfW, cy, cx + halfW, cy, iconPaint)
        canvas.drawLine(cx - halfW, cy - capH, cx - halfW, cy + capH, iconPaint)
        canvas.drawLine(cx + halfW, cy - capH, cx + halfW, cy + capH, iconPaint)
    }

    /**
     * FAB 위치 변경 시 칩 위치를 동기화한다.
     */
    fun updatePosition(fabParams: WindowManager.LayoutParams) {
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val gap = DimensionUtil.dpToPx(context, 6f).toInt()
        val chipW = chipWidth.toInt()
        val chipH = chipHeight.toInt()
        val fabSize = fabParams.width

        val idealX = fabParams.x + (fabSize - chipW) / 2
        lp.x = idealX.coerceIn(0, screenWidth - chipW)

        val aboveY = fabParams.y - chipH - gap
        val belowY = fabParams.y + fabSize + gap
        lp.y = if (aboveY >= 0) aboveY else belowY.coerceAtMost(screenHeight - chipH)

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(this, lp)
        } catch (_: Exception) {
        }
    }

    fun createLayoutParams(fabParams: WindowManager.LayoutParams): WindowManager.LayoutParams {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val gap = DimensionUtil.dpToPx(context, 6f).toInt()
        val chipW = chipWidth.toInt()
        val chipH = chipHeight.toInt()
        val fabSize = fabParams.width

        // 좌우: FAB 중심 정렬 + 화면 안으로 clamp
        val idealX = fabParams.x + (fabSize - chipW) / 2
        val clampedX = idealX.coerceIn(0, screenWidth - chipW)

        // 상하: FAB 위에 배치, 공간 없으면 FAB 아래에
        val aboveY = fabParams.y - chipH - gap
        val belowY = fabParams.y + fabSize + gap
        val clampedY = if (aboveY >= 0) aboveY else belowY.coerceAtMost(screenHeight - chipH)

        return WindowManager.LayoutParams(
            chipW,
            chipH,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampedX
            y = clampedY
        }
    }

    companion object {
        private const val COLOR_INSPECT = 0xDD5F0080.toInt()   // purple
        private const val COLOR_MEASURE = 0xDD1565C0.toInt()   // blue
    }
}
