package com.kurly.loupe

import android.app.Activity
import android.app.Application
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import com.kurly.loupe.core.ComposeNodeInspector
import com.kurly.loupe.core.DesignInfo
import com.kurly.loupe.core.InspectorMode
import com.kurly.loupe.core.MeasurementResult
import com.kurly.loupe.core.ViewInspector
import com.kurly.loupe.token.ColorTokenRegistry
import com.kurly.loupe.token.KurlyDesignTokens
import com.kurly.loupe.token.TypographyTokenRegistry
import com.kurly.loupe.ui.FloatingToggleButton
import com.kurly.loupe.ui.InspectorOverlayView
import com.kurly.loupe.ui.ModeChipView
import com.kurly.loupe.ui.ViewTypeIndicatorBar
import java.lang.ref.WeakReference

/**
 * 디자인 인스펙터 메인 매니저.
 *
 * 2단계 제어:
 * 1. **플로팅 토글 버튼** — [showToggle]/[hideToggle]로 제어 (디버깅 설정에서 on/off)
 * 2. **인스펙터 오버레이** — 플로팅 버튼 탭으로 토글
 *
 * 모드:
 * - **INSPECT**: 싱글 탭 → 뷰 속성 인스펙션
 * - **MEASURE**: 첫 번째 탭 → 선택, 두 번째 탭 → 두 뷰 간 간격 측정
 *
 * 모든 Activity에서 자동으로 유지됩니다.
 */
object DesignInspector {

    private var appRef: WeakReference<Application>? = null
    private var activityRef: WeakReference<Activity>? = null
    private var isInitialized = false

    // ── 플로팅 토글 버튼 ──────────────────
    private var toggleButton: FloatingToggleButton? = null

    // ── 모드 전환 칩 ──────────────────────
    private var modeChip: ModeChipView? = null

    // ── 뷰 타입 인디케이터 바 ─────────────
    private var indicatorBar: ViewTypeIndicatorBar? = null

    /** 플로팅 토글 버튼이 표시 중인지 */
    private var isToggleVisible = false

    /** 플로팅 토글 버튼 표시 여부 (디버깅 설정 UI용) */
    val isToggleShown: Boolean get() = isToggleVisible

    // ── 인스펙터 오버레이 ──────────────────
    private var overlayView: InspectorOverlayView? = null

    /** 인스펙션 모드 활성 여부 */
    private var isInspecting = false

    /** 현재 모드 */
    private var currentMode = InspectorMode.INSPECT

    /** 인스펙션 모드 활성 여부 (외부 참조용) */
    val isActive: Boolean get() = isInspecting

    // ── 측정 모드 상태 ──────────────────────
    private var firstMeasureInfo: DesignInfo? = null

    // ── Lifecycle ─────────────────────────
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (!isToggleVisible) return
            val currentActivity = activityRef?.get()
            if (currentActivity === activity && toggleButton != null) return
            reattachToActivity(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activityRef?.get() !== activity) return
            detachOverlay()
            detachModeChip()
            detachIndicatorBar()
            detachToggleButton()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /**
     * 초기화.
     * Application.onCreate() 에서 호출하세요.
     *
     * @param application Activity 전환 추적에 필요
     * @param colorTokens 디자인 시스템 컬러 토큰 (미전달 시 토큰 매칭 없이 hex만 표시)
     * @param typographyTokens 디자인 시스템 타이포그래피 토큰 (미전달 시 토큰 매칭 없음)
     */
    fun init(
        application: Application? = null,
        colorTokens: List<com.kurly.loupe.token.ColorToken>? = null,
        typographyTokens: List<com.kurly.loupe.token.TypographyToken>? = null,
    ) {
        if (isInitialized) return
        colorTokens?.let { ColorTokenRegistry.registerTokens(it) }
        typographyTokens?.let { TypographyTokenRegistry.registerTokens(it) }
        application?.let { appRef = WeakReference(it) }
        isDebugInspectorInfoEnabled = true
        isInitialized = true
    }

    // ═══════════════════════════════════════
    // 플로팅 토글 버튼 제어
    // ═══════════════════════════════════════

    fun showToggle(activity: Activity) {
        if (!isInitialized) init()
        if (isToggleVisible) return

        isToggleVisible = true
        registerCallbacksIfNeeded(activity)
        attachIndicatorBar(activity)
        attachToggleButton(activity)
    }

    fun hideToggle() {
        isInspecting = false
        isToggleVisible = false
        currentMode = InspectorMode.INSPECT
        firstMeasureInfo = null
        detachOverlay()
        detachModeChip()
        detachIndicatorBar()
        detachToggleButton()
        unregisterCallbacks()
        activityRef = null
    }

    // ═══════════════════════════════════════
    // 인스펙터 오버레이 제어
    // ═══════════════════════════════════════

    private fun toggleInspector() {
        val activity = activityRef?.get() ?: return
        if (isInspecting) {
            isInspecting = false
            currentMode = InspectorMode.INSPECT
            firstMeasureInfo = null
            detachOverlay()
            detachModeChip()
        } else {
            isInspecting = true
            attachOverlay(activity)
            attachModeChip(activity)
        }
        syncButtonState()
    }

    /**
     * INSPECT ↔ MEASURE 모드 전환 (더블탭)
     */
    private fun switchMode() {
        if (!isInspecting) return
        currentMode = when (currentMode) {
            InspectorMode.INSPECT -> InspectorMode.MEASURE
            InspectorMode.MEASURE -> InspectorMode.INSPECT
        }
        firstMeasureInfo = null
        overlayView?.mode = currentMode
        overlayView?.clearInfo()
        overlayView?.clearMeasurement()
        syncButtonState()
    }

    private fun syncButtonState() {
        toggleButton?.isInspecting = isInspecting
        toggleButton?.mode = currentMode
        modeChip?.mode = currentMode
    }

    // ── 하위: 부착/제거 ─────

    private fun reattachToActivity(activity: Activity) {
        detachOverlay()
        detachModeChip()
        detachIndicatorBar()
        detachToggleButton()
        activityRef = WeakReference(activity)
        attachIndicatorBar(activity)
        if (isInspecting) {
            attachOverlay(activity)
            attachModeChip(activity)
        }
        attachToggleButton(activity)
    }

    private fun attachToggleButton(activity: Activity) {
        if (toggleButton != null) return
        activityRef = WeakReference(activity)

        val button = FloatingToggleButton(
            context = activity,
            onToggle = { toggleInspector() },
            onPositionChanged = { syncModeChipPosition() },
        ).apply {
            isInspecting = this@DesignInspector.isInspecting
            mode = this@DesignInspector.currentMode
        }

        try {
            activity.windowManager.addView(button, button.createLayoutParams())
            toggleButton = button
        } catch (_: Exception) {
        }
    }

    private fun detachToggleButton() {
        val activity = activityRef?.get() ?: return
        toggleButton?.let {
            try {
                activity.windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        toggleButton = null
    }

    private fun attachModeChip(activity: Activity) {
        if (modeChip != null) return
        val fabParams = toggleButton?.layoutParams as? WindowManager.LayoutParams ?: return

        val chip = ModeChipView(
            context = activity,
            onSwitch = { switchMode() },
        ).apply {
            mode = this@DesignInspector.currentMode
        }

        try {
            activity.windowManager.addView(chip, chip.createLayoutParams(fabParams))
            modeChip = chip
        } catch (_: Exception) {
        }
    }

    private fun syncModeChipPosition() {
        val fabParams = toggleButton?.layoutParams as? WindowManager.LayoutParams ?: return
        modeChip?.updatePosition(fabParams)
    }

    private fun detachModeChip() {
        val activity = activityRef?.get() ?: return
        modeChip?.let {
            try {
                activity.windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        modeChip = null
    }

    private fun attachIndicatorBar(activity: Activity) {
        if (indicatorBar != null) return

        val bar = ViewTypeIndicatorBar(context = activity)
        val statusBarHeight = ViewTypeIndicatorBar.getStatusBarHeight(activity)

        try {
            activity.windowManager.addView(bar, bar.createLayoutParams(statusBarHeight))
            indicatorBar = bar
            bar.post { bar.analyze(activity) }
        } catch (_: Exception) {
        }
    }

    private fun detachIndicatorBar() {
        val activity = activityRef?.get() ?: return
        indicatorBar?.let {
            it.stopObserving()
            try {
                activity.windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        indicatorBar = null
    }

    private fun attachOverlay(activity: Activity) {
        if (overlayView != null) return

        val overlay = InspectorOverlayView(
            context = activity,
            onViewTapped = { x, y -> onTap(x, y) },
            onDismiss = {
                isInspecting = false
                currentMode = InspectorMode.INSPECT
                firstMeasureInfo = null
                detachOverlay()
                detachModeChip()
                syncButtonState()
            },
        ).apply {
            mode = this@DesignInspector.currentMode
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )

        try {
            activity.windowManager.addView(overlay, params)
            overlayView = overlay
            bringToggleButtonToFront(activity)
        } catch (_: Exception) {
        }
    }

    private fun bringToggleButtonToFront(activity: Activity) {
        val button = toggleButton ?: return
        val savedParams = button.layoutParams as? WindowManager.LayoutParams ?: return
        try {
            activity.windowManager.removeView(button)
            activity.windowManager.addView(button, savedParams)
        } catch (_: Exception) {
        }
    }

    private fun detachOverlay() {
        val activity = activityRef?.get() ?: return
        overlayView?.let {
            try {
                activity.windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }

    // ── Lifecycle 콜백 등록/해제 ──────────

    private fun registerCallbacksIfNeeded(activity: Activity) {
        val app = appRef?.get() ?: activity.application
        appRef = WeakReference(app)
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    private fun unregisterCallbacks() {
        appRef?.get()?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    // ── 뷰 인스펙션 / 측정 ──────────────────

    private fun onTap(x: Int, y: Int) {
        when (currentMode) {
            InspectorMode.INSPECT -> onInspectTap(x, y)
            InspectorMode.MEASURE -> onMeasureTap(x, y)
        }
    }

    private fun onInspectTap(x: Int, y: Int) {
        val activity = activityRef?.get() ?: return
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val target = findViewExcludingOverlay(rootView, x, y)
        if (target != null) {
            val composeView = if (ComposeNodeInspector.isAndroidComposeView(target)) {
                target
            } else {
                ComposeNodeInspector.findComposeView(target)
            }

            val info = if (composeView != null) {
                ComposeNodeInspector.inspectAt(composeView, x, y)
                    ?: ViewInspector.extract(target)
            } else {
                ViewInspector.extract(target)
            }
            overlayView?.showInfo(info)
        } else {
            overlayView?.clearInfo()
        }
    }

    private fun onMeasureTap(x: Int, y: Int) {
        val activity = activityRef?.get() ?: return
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val target = findViewExcludingOverlay(rootView, x, y)
        if (target == null) {
            // 빈 곳 탭 → 측정 리셋
            firstMeasureInfo = null
            overlayView?.clearMeasurement()
            return
        }

        val composeView = if (ComposeNodeInspector.isAndroidComposeView(target)) {
            target
        } else {
            ComposeNodeInspector.findComposeView(target)
        }

        val info = if (composeView != null) {
            ComposeNodeInspector.inspectAt(composeView, x, y) ?: ViewInspector.extract(target)
        } else {
            ViewInspector.extract(target)
        }

        val first = firstMeasureInfo
        if (first == null) {
            // 첫 번째 선택
            firstMeasureInfo = info
            overlayView?.showFirstSelection(info.bounds)
        } else {
            // 두 번째 선택 → 측정
            val result = MeasurementResult.calculate(
                first = first.bounds,
                second = info.bounds,
                context = activity,
            )
            overlayView?.showMeasurement(result)
            firstMeasureInfo = null // 다음 탭은 새 측정 시작
        }
    }

    private fun isOwnOverlay(view: View): Boolean =
        view == overlayView || view == toggleButton || view == indicatorBar || view == modeChip

    private fun findViewExcludingOverlay(root: View, x: Int, y: Int): View? {
        if (isOwnOverlay(root)) return null
        if (!root.isShown) return null

        if (root is ViewGroup) {
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                if (isOwnOverlay(child)) continue
                val found = findViewExcludingOverlay(child, x, y)
                if (found != null) return found
            }
        }

        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val inBounds = x >= location[0] && x <= location[0] + root.width &&
            y >= location[1] && y <= location[1] + root.height

        return if (inBounds) root else null
    }

    // ═══════════════════════════════════════
    // 하위 호환 (기존 API)
    // ═══════════════════════════════════════

    fun show(activity: Activity) = showToggle(activity)
    fun hide() = hideToggle()
    fun toggle(activity: Activity) {
        if (isToggleVisible) hideToggle() else showToggle(activity)
    }
}
