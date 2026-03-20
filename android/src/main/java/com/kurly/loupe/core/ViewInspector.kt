package com.kurly.loupe.core

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.kurly.loupe.token.ColorTokenRegistry
import com.kurly.loupe.util.DimensionUtil

/**
 * View에서 디자인 속성을 추출하는 인스펙터
 */
object ViewInspector {

    /**
     * 터치 좌표에 해당하는 최하위 뷰를 찾는다
     */
    fun findViewAt(root: View, x: Int, y: Int): View? {
        if (!root.isShown) return null

        if (root is ViewGroup) {
            // 자식 뷰를 역순으로 탐색 (최상위에 그려진 뷰 우선)
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                val found = findViewAt(child, x, y)
                if (found != null) return found
            }
        }

        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + root.width,
            location[1] + root.height,
        )

        return if (rect.contains(x, y)) root else null
    }

    /**
     * View에서 DesignInfo를 추출한다
     */
    fun extract(view: View): DesignInfo {
        val context = view.context
        val density = context.resources.displayMetrics.density

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        // 뷰 ID 이름
        val viewId = try {
            if (view.id != View.NO_ID) {
                view.resources.getResourceEntryName(view.id)
            } else null
        } catch (_: Exception) {
            null
        }

        // 배경색 추출
        val bgColor = extractBackgroundColor(view)

        // 텍스트 컬러 추출
        val textColor = (view as? TextView)?.let {
            ColorTokenRegistry.resolve(it.currentTextColor)
        }

        // 틴트 컬러
        val tintColor = (view as? ImageView)?.let {
            it.imageTintList?.defaultColor?.let { c -> ColorTokenRegistry.resolve(c) }
        }

        // 마진
        val lp = view.layoutParams
        val marginLeft: Int
        val marginTop: Int
        val marginRight: Int
        val marginBottom: Int
        if (lp is ViewGroup.MarginLayoutParams) {
            marginLeft = DimensionUtil.pxToDp(context, lp.leftMargin)
            marginTop = DimensionUtil.pxToDp(context, lp.topMargin)
            marginRight = DimensionUtil.pxToDp(context, lp.rightMargin)
            marginBottom = DimensionUtil.pxToDp(context, lp.bottomMargin)
        } else {
            marginLeft = 0; marginTop = 0; marginRight = 0; marginBottom = 0
        }

        // 텍스트 속성
        val textView = view as? TextView
        val textSizeSp = textView?.let { DimensionUtil.pxToSp(context, it.textSize) }
        val fontWeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            textView?.typeface?.weight?.toString()
        } else {
            textView?.typeface?.let { if (it.isBold) "Bold" else "Normal" }
        }
        val letterSpacing = textView?.letterSpacing?.let { it * (textView.textSize / density) }
        val lineHeight = textView?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DimensionUtil.pxToSp(context, it.lineHeight.toFloat())
            } else null
        }

        // 코너 라디우스
        val cornerRadius = extractCornerRadius(view)

        return DesignInfo(
            viewClassName = view.javaClass.simpleName,
            viewId = viewId,
            bounds = Rect(
                location[0], location[1],
                location[0] + view.width, location[1] + view.height,
            ),
            backgroundColor = bgColor,
            textColor = textColor,
            tintColor = tintColor,
            paddingLeft = DimensionUtil.pxToDp(context, view.paddingLeft),
            paddingTop = DimensionUtil.pxToDp(context, view.paddingTop),
            paddingRight = DimensionUtil.pxToDp(context, view.paddingRight),
            paddingBottom = DimensionUtil.pxToDp(context, view.paddingBottom),
            marginLeft = marginLeft,
            marginTop = marginTop,
            marginRight = marginRight,
            marginBottom = marginBottom,
            textSizeSp = textSizeSp,
            fontWeight = fontWeight,
            letterSpacingSp = letterSpacing,
            lineHeightSp = lineHeight,
            widthDp = DimensionUtil.pxToDp(context, view.width),
            heightDp = DimensionUtil.pxToDp(context, view.height),
            alpha = view.alpha,
            elevation = DimensionUtil.pxToDp(context, view.elevation),
            cornerRadiusDp = cornerRadius,
        )
    }

    /**
     * 배경색 추출 (다양한 Drawable 타입 처리)
     */
    private fun extractBackgroundColor(view: View): ColorInfo? {
        val bg = view.background ?: return null

        return when (bg) {
            is ColorDrawable -> ColorTokenRegistry.resolve(bg.color)

            is GradientDrawable -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    bg.color?.defaultColor?.let { ColorTokenRegistry.resolve(it) }
                } else null
            }

            is RippleDrawable -> {
                // RippleDrawable 내부의 실제 배경색을 추출
                if (bg.numberOfLayers > 0) {
                    val inner = bg.getDrawable(0)
                    when (inner) {
                        is ColorDrawable -> ColorTokenRegistry.resolve(inner.color)
                        is GradientDrawable -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                inner.color?.defaultColor?.let { ColorTokenRegistry.resolve(it) }
                            } else null
                        }
                        else -> null
                    }
                } else null
            }

            else -> null
        }
    }

    /**
     * 코너 라디우스 추출
     */
    private fun extractCornerRadius(view: View): Float? {
        val bg = view.background
        val drawable = when (bg) {
            is GradientDrawable -> bg
            is RippleDrawable -> {
                if (bg.numberOfLayers > 0) bg.getDrawable(0) as? GradientDrawable else null
            }
            else -> null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && drawable != null) {
            val radii = drawable.cornerRadii
            if (radii != null) {
                DimensionUtil.pxToDp(view.context, radii[0])
            } else {
                DimensionUtil.pxToDp(view.context, drawable.cornerRadius)
            }
        } else null
    }
}
