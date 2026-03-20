package com.kurly.loupe.core

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import com.kurly.loupe.util.DimensionUtil

/**
 * 두 뷰 간 간격 측정 결과
 */
data class MeasurementResult(
    val firstBounds: Rect,
    val secondBounds: Rect,
    val horizontalGapDp: Int,
    val verticalGapDp: Int,
    /** 수평 측정선 시작/끝 (px, 화면 좌표) */
    val hLineStart: PointF,
    val hLineEnd: PointF,
    /** 수직 측정선 시작/끝 (px, 화면 좌표) */
    val vLineStart: PointF,
    val vLineEnd: PointF,
) {
    val isOverlapping: Boolean get() = horizontalGapDp == 0 && verticalGapDp == 0

    companion object {
        fun calculate(
            first: Rect,
            second: Rect,
            context: Context,
        ): MeasurementResult {
            // ── 수평 간격 ──────────────────────
            val hGapPx: Int
            val hStart: PointF
            val hEnd: PointF

            if (first.right <= second.left) {
                // first가 왼쪽
                hGapPx = second.left - first.right
                val midY = verticalMidpoint(first, second)
                hStart = PointF(first.right.toFloat(), midY)
                hEnd = PointF(second.left.toFloat(), midY)
            } else if (second.right <= first.left) {
                // second가 왼쪽
                hGapPx = first.left - second.right
                val midY = verticalMidpoint(first, second)
                hStart = PointF(second.right.toFloat(), midY)
                hEnd = PointF(first.left.toFloat(), midY)
            } else {
                // 수평 겹침
                hGapPx = 0
                hStart = PointF(0f, 0f)
                hEnd = PointF(0f, 0f)
            }

            // ── 수직 간격 ──────────────────────
            val vGapPx: Int
            val vStart: PointF
            val vEnd: PointF

            if (first.bottom <= second.top) {
                // first가 위
                vGapPx = second.top - first.bottom
                val midX = horizontalMidpoint(first, second)
                vStart = PointF(midX, first.bottom.toFloat())
                vEnd = PointF(midX, second.top.toFloat())
            } else if (second.bottom <= first.top) {
                // second가 위
                vGapPx = first.top - second.bottom
                val midX = horizontalMidpoint(first, second)
                vStart = PointF(midX, second.bottom.toFloat())
                vEnd = PointF(midX, first.top.toFloat())
            } else {
                // 수직 겹침
                vGapPx = 0
                vStart = PointF(0f, 0f)
                vEnd = PointF(0f, 0f)
            }

            return MeasurementResult(
                firstBounds = first,
                secondBounds = second,
                horizontalGapDp = DimensionUtil.pxToDp(context, hGapPx),
                verticalGapDp = DimensionUtil.pxToDp(context, vGapPx),
                hLineStart = hStart,
                hLineEnd = hEnd,
                vLineStart = vStart,
                vLineEnd = vEnd,
            )
        }

        /** 두 Rect의 수직 겹침 영역의 중점 Y, 겹치지 않으면 두 중심의 평균 */
        private fun verticalMidpoint(a: Rect, b: Rect): Float {
            val overlapTop = maxOf(a.top, b.top)
            val overlapBottom = minOf(a.bottom, b.bottom)
            return if (overlapTop < overlapBottom) {
                (overlapTop + overlapBottom) / 2f
            } else {
                (a.centerY() + b.centerY()) / 2f
            }
        }

        /** 두 Rect의 수평 겹침 영역의 중점 X, 겹치지 않으면 두 중심의 평균 */
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
