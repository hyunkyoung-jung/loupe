package com.kurly.loupe.core

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import com.kurly.loupe.util.DimensionUtil

/**
 * 두 뷰 간 간격 측정 결과.
 *
 * 두 가지 측정 모드:
 * 1. **GAP** — 겹치지 않을 때: 가장 가까운 변 사이 간격
 * 2. **EDGE** — 겹칠 때: 각 변 간 거리 (left↔left, top↔top, right↔right, bottom↔bottom)
 */
data class MeasurementResult(
    val firstBounds: Rect,
    val secondBounds: Rect,

    // ── GAP 측정 (비겹침) ──────────────────
    /** 가장 가까운 수평 변 사이 간격 (dp). 겹치면 0 */
    val horizontalGapDp: Int,
    /** 가장 가까운 수직 변 사이 간격 (dp). 겹치면 0 */
    val verticalGapDp: Int,
    val hLineStart: PointF,
    val hLineEnd: PointF,
    val vLineStart: PointF,
    val vLineEnd: PointF,

    // ── EDGE 측정 (겹침 포함) ──────────────
    /** 두 요소의 left 변 간 거리 (dp) */
    val edgeLeftDp: Int = 0,
    /** 두 요소의 top 변 간 거리 (dp) */
    val edgeTopDp: Int = 0,
    /** 두 요소의 right 변 간 거리 (dp) */
    val edgeRightDp: Int = 0,
    /** 두 요소의 bottom 변 간 거리 (dp) */
    val edgeBottomDp: Int = 0,

    /** 수평 겹침 영역 크기 (dp). 겹치지 않으면 0 */
    val hOverlapDp: Int = 0,
    /** 수직 겹침 영역 크기 (dp). 겹치지 않으면 0 */
    val vOverlapDp: Int = 0,
) {
    val isOverlapping: Boolean get() = horizontalGapDp == 0 && verticalGapDp == 0
    val hasGap: Boolean get() = horizontalGapDp > 0 || verticalGapDp > 0

    companion object {
        fun calculate(
            first: Rect,
            second: Rect,
            context: Context,
        ): MeasurementResult {
            // ── GAP 측정 ──────────────────────
            val hGapPx: Int
            val hStart: PointF
            val hEnd: PointF

            if (first.right <= second.left) {
                hGapPx = second.left - first.right
                val midY = verticalMidpoint(first, second)
                hStart = PointF(first.right.toFloat(), midY)
                hEnd = PointF(second.left.toFloat(), midY)
            } else if (second.right <= first.left) {
                hGapPx = first.left - second.right
                val midY = verticalMidpoint(first, second)
                hStart = PointF(second.right.toFloat(), midY)
                hEnd = PointF(first.left.toFloat(), midY)
            } else {
                hGapPx = 0
                hStart = PointF(0f, 0f)
                hEnd = PointF(0f, 0f)
            }

            val vGapPx: Int
            val vStart: PointF
            val vEnd: PointF

            if (first.bottom <= second.top) {
                vGapPx = second.top - first.bottom
                val midX = horizontalMidpoint(first, second)
                vStart = PointF(midX, first.bottom.toFloat())
                vEnd = PointF(midX, second.top.toFloat())
            } else if (second.bottom <= first.top) {
                vGapPx = first.top - second.bottom
                val midX = horizontalMidpoint(first, second)
                vStart = PointF(midX, second.bottom.toFloat())
                vEnd = PointF(midX, first.top.toFloat())
            } else {
                vGapPx = 0
                vStart = PointF(0f, 0f)
                vEnd = PointF(0f, 0f)
            }

            // ── EDGE 측정 (inner→outer 기준) ──
            // 면적이 작은 쪽이 inner
            val firstArea = first.width().toLong() * first.height().toLong()
            val secondArea = second.width().toLong() * second.height().toLong()
            val inner = if (firstArea <= secondArea) first else second
            val outer = if (firstArea <= secondArea) second else first

            val edgeLeftPx = (inner.left - outer.left).coerceAtLeast(0)
            val edgeTopPx = (inner.top - outer.top).coerceAtLeast(0)
            val edgeRightPx = (outer.right - inner.right).coerceAtLeast(0)
            val edgeBottomPx = (outer.bottom - inner.bottom).coerceAtLeast(0)

            // ── 겹침 영역 크기 ────────────────
            val hOverlapPx = maxOf(0,
                minOf(first.right, second.right) - maxOf(first.left, second.left))
            val vOverlapPx = maxOf(0,
                minOf(first.bottom, second.bottom) - maxOf(first.top, second.top))

            return MeasurementResult(
                firstBounds = first,
                secondBounds = second,
                horizontalGapDp = DimensionUtil.pxToDp(context, hGapPx),
                verticalGapDp = DimensionUtil.pxToDp(context, vGapPx),
                hLineStart = hStart,
                hLineEnd = hEnd,
                vLineStart = vStart,
                vLineEnd = vEnd,
                edgeLeftDp = DimensionUtil.pxToDp(context, edgeLeftPx),
                edgeTopDp = DimensionUtil.pxToDp(context, edgeTopPx),
                edgeRightDp = DimensionUtil.pxToDp(context, edgeRightPx),
                edgeBottomDp = DimensionUtil.pxToDp(context, edgeBottomPx),
                hOverlapDp = DimensionUtil.pxToDp(context, hOverlapPx),
                vOverlapDp = DimensionUtil.pxToDp(context, vOverlapPx),
            )
        }

        private fun verticalMidpoint(a: Rect, b: Rect): Float {
            val overlapTop = maxOf(a.top, b.top)
            val overlapBottom = minOf(a.bottom, b.bottom)
            return if (overlapTop < overlapBottom) {
                (overlapTop + overlapBottom) / 2f
            } else {
                (a.centerY() + b.centerY()) / 2f
            }
        }

        private fun horizontalMidpoint(a: Rect, b: Rect): Float {
            val overlapLeft = maxOf(a.left, b.left)
            val overlapRight = minOf(a.right, b.right)
            return if (overlapLeft < overlapRight) {
                (overlapLeft + overlapRight) / 2f
            } else {
                (a.centerX() + b.centerX()) / 2f
            }
        }
    }
}
