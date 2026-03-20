package com.kurly.loupe.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import com.kurly.loupe.core.InspectorMode
import com.kurly.loupe.util.DimensionUtil

/**
 * 드래그 가능한 플로팅 토글 버튼.
 * 인스펙터 on/off를 모든 화면에서 제어할 수 있습니다.
 *
 * - 싱글 탭: 인스펙터 토글 (off ↔ on)
 * - 더블 탭: 모드 전환 (INSPECT ↔ MEASURE)
 * - 드래그: 버튼 위치 이동
 * - 드래그 종료 시 좌/우 가장자리로 스냅
 */
@SuppressLint("ViewConstructor")
class FloatingToggleButton(
    context: Context,
    private val onToggle: () -> Unit,
    private val onPositionChanged: () -> Unit = {},
) : View(context) {

    private val dp = { v: Float -> DimensionUtil.dpToPx(context, v) }

    private val buttonSize = dp(44f)
    private val iconPadding = dp(11f)

    var isInspecting = false
        set(value) {
            field = value
            invalidate()
        }

    var mode: InspectorMode = InspectorMode.INSPECT
        set(value) {
            field = value
            invalidate()
        }

    // ── Paint ──────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_BG
        setShadowLayer(dp(6f), 0f, dp(2f), 0x40000000)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Touch / Drag ──────────────────────
    private var downX = 0f
    private var downY = 0f
    private var downParamX = 0
    private var downParamY = 0
    private var isDragging = false
    private val touchSlop = dp(8f)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                val lp = layoutParams as? WindowManager.LayoutParams
                downParamX = lp?.x ?: 0
                downParamY = lp?.y ?: 0
                isDragging = false
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!isDragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    val lp = layoutParams as? WindowManager.LayoutParams ?: return true
                    lp.x = (downParamX + dx).toInt()
                    lp.y = (downParamY + dy).toInt()
                    try {
                        wm.updateViewLayout(this, lp)
                        onPositionChanged()
                    } catch (_: Exception) {
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                if (!isDragging) {
                    onToggle()
                } else {
                    snapToEdge(wm)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToEdge(wm: WindowManager) {
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (lp.x + buttonSize / 2 < screenWidth / 2) {
            0
        } else {
            screenWidth - buttonSize.toInt()
        }

        val animator = ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 200
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener {
                lp.x = it.animatedValue as Int
                try {
                    wm.updateViewLayout(this@FloatingToggleButton, lp)
                    onPositionChanged()
                } catch (_: Exception) {
                    cancel()
                }
            }
        }
        animator.start()
    }

    /**
     * 화면 크기 변경 시 (fold/unfold 등) FAB가 화면 밖으로 벗어나지 않도록 보정.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        post { clampToScreen() }
    }

    private fun clampToScreen() {
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val size = buttonSize.toInt()

        var changed = false
        if (lp.x + size > screenWidth) {
            lp.x = screenWidth - size
            changed = true
        }
        if (lp.y + size > screenHeight) {
            lp.y = screenHeight - size
            changed = true
        }
        if (lp.x < 0) { lp.x = 0; changed = true }
        if (lp.y < 0) { lp.y = 0; changed = true }

        if (changed) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.updateViewLayout(this, lp)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = buttonSize / 2
        val cy = buttonSize / 2
        val radius = buttonSize / 2 - dp(2f)

        // 배경 원
        bgPaint.color = when {
            !isInspecting -> COLOR_BG
            mode == InspectorMode.MEASURE -> COLOR_BG_MEASURE
            else -> COLOR_BG_ACTIVE
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 아이콘
        if (isInspecting && mode == InspectorMode.MEASURE) {
            drawRulerIcon(canvas, cx, cy)
        } else {
            drawMagnifierIcon(canvas, cx, cy)
        }

        // 활성 상태 인디케이터 점
        if (isInspecting) {
            activeDotPaint.color = if (mode == InspectorMode.MEASURE) {
                COLOR_MEASURE_DOT
            } else {
                COLOR_ACTIVE_DOT
            }
            canvas.drawCircle(
                cx + radius * 0.55f,
                cy - radius * 0.55f,
                dp(4f),
                activeDotPaint,
            )
        }
    }

    private fun drawMagnifierIcon(canvas: Canvas, cx: Float, cy: Float) {
        val iconSize = buttonSize - iconPadding * 2
        val lensRadius = iconSize * 0.32f
        val lensCx = cx - iconSize * 0.08f
        val lensCy = cy - iconSize * 0.08f

        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(2f)
        canvas.drawCircle(lensCx, lensCy, lensRadius, iconPaint)

        val handleStartX = lensCx + lensRadius * 0.7f
        val handleStartY = lensCy + lensRadius * 0.7f
        val handleEndX = handleStartX + iconSize * 0.25f
        val handleEndY = handleStartY + iconSize * 0.25f
        iconPaint.strokeWidth = dp(2.5f)
        canvas.drawLine(handleStartX, handleStartY, handleEndX, handleEndY, iconPaint)
        iconPaint.strokeWidth = dp(2f)
    }

    /**
     * 룰러(간격 측정) 아이콘: ↔ 양방향 화살표 + 양쪽 세로 끝선
     */
    private fun drawRulerIcon(canvas: Canvas, cx: Float, cy: Float) {
        val iconSize = buttonSize - iconPadding * 2
        val halfW = iconSize * 0.38f
        val capH = dp(5f)

        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(2f)

        // 가로 중심선
        canvas.drawLine(cx - halfW, cy, cx + halfW, cy, iconPaint)

        // 왼쪽 세로 끝선
        canvas.drawLine(cx - halfW, cy - capH, cx - halfW, cy + capH, iconPaint)
        // 오른쪽 세로 끝선
        canvas.drawLine(cx + halfW, cy - capH, cx + halfW, cy + capH, iconPaint)

        // 왼쪽 화살촉
        val arrowSize = dp(3.5f)
        canvas.drawLine(cx - halfW, cy, cx - halfW + arrowSize, cy - arrowSize, iconPaint)
        canvas.drawLine(cx - halfW, cy, cx - halfW + arrowSize, cy + arrowSize, iconPaint)

        // 오른쪽 화살촉
        canvas.drawLine(cx + halfW, cy, cx + halfW - arrowSize, cy - arrowSize, iconPaint)
        canvas.drawLine(cx + halfW, cy, cx + halfW - arrowSize, cy + arrowSize, iconPaint)
    }

    fun createLayoutParams(): WindowManager.LayoutParams {
        val screenWidth = resources.displayMetrics.widthPixels
        val size = buttonSize.toInt()
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - size
            y = (resources.displayMetrics.heightPixels * 0.7f).toInt()
        }
    }

    companion object {
        private const val COLOR_BG = 0xFF5F0080.toInt()
        private const val COLOR_BG_ACTIVE = 0xFF3D0054.toInt()
        private const val COLOR_BG_MEASURE = 0xFF1565C0.toInt()    // blue
        private const val COLOR_ACTIVE_DOT = 0xFF00E676.toInt()
        private const val COLOR_MEASURE_DOT = 0xFF82B1FF.toInt()   // light blue

    }
}
