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

    // ── 화면 변경 감지 (구조 변경 시에만 업데이트) ─
    private val handler = Handler(Looper.getMainLooper())
    private var observingActivity: Activity? = null
    private var lastStructureHash = 0

    private val pollRunnable = object : Runnable {
        override fun run() {
            val activity = observingActivity ?: return
            val root = activity.window?.decorView
                ?.findViewById<ViewGroup>(android.R.id.content) ?: return

            // 뷰 트리 구조 해시가 변경된 경우에만 재계산
            val hash = computeStructureHash(root)
            if (hash != lastStructureHash) {
                lastStructureHash = hash
                val newInfo = analyzeViewHierarchy(root)
                if (newInfo != viewTypeInfo) {
                    viewTypeInfo = newInfo
                    invalidate()
                }
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /**
     * 뷰 트리 상위 [HASH_MAX_DEPTH] 레벨의 구조 시그니처 해시.
     * 클래스명 + 자식 수만으로 계산하므로:
     * - 스크롤 (RecyclerView 아이템 재활용) → 해시 불변 → 재계산 안 함
     * - 탭 전환 / Fragment 교체 → 해시 변경 → 재계산
     */
    private fun computeStructureHash(view: View, depth: Int = 0): Int {
        var hash = view.javaClass.name.hashCode()
        if (depth < HASH_MAX_DEPTH && view is ViewGroup) {
            hash = hash * 31 + view.childCount
            for (i in 0 until view.childCount) {
                hash = hash * 31 + computeStructureHash(view.getChildAt(i), depth + 1)
            }
        }
        return hash
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
    // View 계층 분석 (깊이 제한 면적 기반 — 스크롤 무관)
    // ═══════════════════════════════════════

    /**
     * 뷰 트리 전체를 탐색하여 Compose / XML 면적 비율을 계산.
     *
     * 재계산 시점은 [computeStructureHash] (상위 구조만 감지)로 제어되므로
     * 스크롤로 인한 RecyclerView 아이템 재활용은 재계산을 트리거하지 않는다.
     */
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
        if (view.width <= 0 || view.height <= 0) return

        val area = view.width.toLong() * view.height.toLong()

        // ComposeView → 전체 면적을 compose로 (하위 탐색 중단)
        if (ComposeNodeInspector.isAndroidComposeView(view)) {
            counter.composeArea += area
            return
        }

        if (view is ViewGroup && view.childCount > 0) {
            for (i in 0 until view.childCount) {
                countAreas(view.getChildAt(i), counter)
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
        /** 구조 변경 감지용 해시의 최대 깊이. 상위만 보고 스크롤 영향 차단 */
        private const val HASH_MAX_DEPTH = 3

        private const val COLOR_COMPOSE = 0xCC00897B.toInt()   // teal
        private const val COLOR_XML = 0xCC5C6BC0.toInt()       // indigo
        private const val COLOR_UNKNOWN = 0xCC757575.toInt()   // grey

        fun getStatusBarHeight(context: Context): Int {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }
    }
}