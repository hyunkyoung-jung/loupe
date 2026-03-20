package com.kurly.loupe.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.kurly.loupe.core.ComposeNodeInspector
import com.kurly.loupe.util.DimensionUtil

/**
 * 화면 최상단에 노출되는 얇은 인디케이터 바.
 * 현재 화면이 XML / Compose / Mixed 인지 표시하고,
 * Mixed인 경우 Compose 비율을 시각적으로 보여줍니다.
 *
 * 레이아웃 변경(탭 전환 등)을 감지하여 자동으로 비율을 업데이트합니다.
 */
@SuppressLint("ViewConstructor")
class ViewTypeIndicatorBar(
    context: Context,
) : View(context) {

    private val dp = { v: Float -> DimensionUtil.dpToPx(context, v) }
    private val barHeight = dp(20f)

    private var viewTypeInfo: ViewTypeInfo = ViewTypeInfo.Unknown

    // ── 화면 변경 감지 (주기적 폴링) ─────────
    private val handler = Handler(Looper.getMainLooper())
    private var observingActivity: Activity? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            val activity = observingActivity ?: return
            val root = activity.window?.decorView
                ?.findViewById<ViewGroup>(android.R.id.content) ?: return
            val newInfo = analyzeViewHierarchy(root)
            if (newInfo != viewTypeInfo) {
                viewTypeInfo = newInfo
                invalidate()
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // ── Paint ──────────────────────────────
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val composeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_COMPOSE
    }
    private val xmlFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_XML
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(dp(1f), 0f, 0.5f, 0x80000000.toInt())
    }
    private val borderPaint = Paint().apply {
        color = 0x40FFFFFF
        strokeWidth = dp(0.5f)
        style = Paint.Style.STROKE
    }

    fun analyze(activity: Activity) {
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        viewTypeInfo = analyzeViewHierarchy(rootView)
        invalidate()
        startPolling(activity)
    }

    private fun startPolling(activity: Activity) {
        stopObserving()
        observingActivity = activity
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    fun stopObserving() {
        handler.removeCallbacks(pollRunnable)
        observingActivity = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val info = viewTypeInfo

        when (info) {
            is ViewTypeInfo.PureXml -> {
                barPaint.color = COLOR_XML
                canvas.drawRect(0f, 0f, w, h, barPaint)
                drawLabel(canvas, "XML", w, h)
            }
            is ViewTypeInfo.PureCompose -> {
                barPaint.color = COLOR_COMPOSE
                canvas.drawRect(0f, 0f, w, h, barPaint)
                drawLabel(canvas, "Compose", w, h)
            }
            is ViewTypeInfo.Mixed -> {
                val composeWidth = w * info.composeRatio
                canvas.drawRect(0f, 0f, w, h, xmlFillPaint)
                canvas.drawRect(0f, 0f, composeWidth, h, composeFillPaint)
                canvas.drawLine(composeWidth, 0f, composeWidth, h, borderPaint)

                val pct = (info.composeRatio * 100).toInt()
                drawLabel(canvas, "Compose $pct%  ·  XML ${100 - pct}%", w, h)
            }
            is ViewTypeInfo.Unknown -> {
                barPaint.color = COLOR_UNKNOWN
                canvas.drawRect(0f, 0f, w, h, barPaint)
                drawLabel(canvas, "Analyzing…", w, h)
            }
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, w: Float, h: Float) {
        val textY = h / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, w / 2, textY, textPaint)
    }

    fun createLayoutParams(statusBarHeight: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeight.toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = statusBarHeight
        }
    }

    // ═══════════════════════════════════════
    // View 계층 분석
    // ═══════════════════════════════════════

    private fun analyzeViewHierarchy(root: ViewGroup): ViewTypeInfo {
        val counter = AreaCounter()
        countAreas(root, counter)

        val total = counter.xmlArea + counter.composeArea
        if (total <= 0) return ViewTypeInfo.Unknown

        val composeRatio = counter.composeArea.toFloat() / total

        return when {
            composeRatio >= 0.99f -> ViewTypeInfo.PureCompose
            composeRatio <= 0.01f -> ViewTypeInfo.PureXml
            else -> ViewTypeInfo.Mixed(composeRatio = composeRatio)
        }
    }

    private data class AreaCounter(
        var xmlArea: Long = 0,
        var composeArea: Long = 0,
    )

    private fun countAreas(view: View, counter: AreaCounter) {
        if (!view.isShown || view.width <= 0 || view.height <= 0) return

        val area = view.width.toLong() * view.height.toLong()

        if (ComposeNodeInspector.isAndroidComposeView(view)) {
            counter.composeArea += area
            return
        }

        if (view is ViewGroup) {
            if (view.childCount == 0) {
                counter.xmlArea += area
            } else {
                var childrenArea = 0L
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (child.isShown && child.width > 0 && child.height > 0) {
                        childrenArea += child.width.toLong() * child.height.toLong()
                        countAreas(child, counter)
                    }
                }
                val ownArea = (area - childrenArea).coerceAtLeast(0)
                if (ownArea > 0) counter.xmlArea += ownArea
            }
        } else {
            counter.xmlArea += area
        }
    }

    // ── 모델 ────────────────────────────────

    sealed interface ViewTypeInfo {
        data object PureXml : ViewTypeInfo
        data object PureCompose : ViewTypeInfo
        data class Mixed(val composeRatio: Float) : ViewTypeInfo
        data object Unknown : ViewTypeInfo
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L

        private const val COLOR_COMPOSE = 0xCC00897B.toInt()   // teal
        private const val COLOR_XML = 0xCC5C6BC0.toInt()       // indigo
        private const val COLOR_UNKNOWN = 0xCC757575.toInt()   // grey

        fun getStatusBarHeight(context: Context): Int {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }
    }
}