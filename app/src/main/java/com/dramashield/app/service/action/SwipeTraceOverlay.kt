package com.dramashield.app.service.action

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.dramashield.app.util.log.AppLogger

/**
 * 在无障碍服务上叠加一个不可触摸的全屏指示层，用来显示本次上滑的
 * 起点、终点、移动轨迹和实际时长。它只负责显示，不参与手势执行。
 */
class SwipeTraceOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private var view: TraceView? = null
    private var animator: ValueAnimator? = null
    private var windowManager: WindowManager? = null

    fun show(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        handler.post {
            hide()
            val manager = service.getSystemService(WindowManager::class.java) ?: return@post
            windowManager = manager
            val traceView = TraceView(service, startX, startY, endX, endY, durationMs)
            AppLogger.d(
                "Swipe trace show start=($startX,$startY) end=($endX,$endY) duration=${durationMs}ms"
            )
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            runCatching { manager.addView(traceView, params) }
                .onSuccess {
                    AppLogger.d("Swipe trace overlay added")
                }
                .onFailure {
                    AppLogger.e("Show swipe trace failed", it)
                    return@post
                }
            view = traceView

            // 实际滑动可能只有几十毫秒；视觉上延长到 600ms，方便看清轨迹。
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = VIRTUAL_TRACE_DURATION_MS
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    traceView.progress = animation.animatedValue as Float
                    traceView.invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        handler.postDelayed({ hide() }, TRACE_HOLD_MS)
                    }
                })
                start()
            }
        }
    }

    fun hide() {
        handler.post {
            animator?.cancel()
            animator = null
            view?.let { traceView ->
                runCatching { windowManager?.removeView(traceView) }
            }
            view = null
            windowManager = null
        }
    }

    private class TraceView(
        context: Context,
        private val startX: Float,
        private val startY: Float,
        private val endX: Float,
        private val endY: Float,
        private val durationMs: Long
    ) : View(context) {

        var progress: Float = 0f

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            alpha = 220
        }
        private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 12f), 0f)
            alpha = 100
        }
        private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0EA5E9")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = 200
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            style = Paint.Style.FILL
            setShadowLayer(14f, 0f, 0f, Color.parseColor("#802563EB"))
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        private val textBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6FFFFFF")
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val currentX = startX + (endX - startX) * progress
            val currentY = startY + (endY - startY) * progress

            canvas.drawCircle(startX, startY, 18f, ghostPaint)
            canvas.drawCircle(endX, endY, 24f, ghostPaint)

            canvas.drawLine(startX, startY, currentX, currentY, linePaint)
            canvas.drawCircle(currentX, currentY, 20f, dotPaint)

            val label = "上滑 ${durationMs}ms"
            val textY = (startY - 28f).coerceAtLeast(80f)
            val textWidth = textPaint.measureText(label)
            canvas.drawRoundRect(
                (startX - textWidth / 2f - 16f),
                (textY - 38f),
                (startX + textWidth / 2f + 16f),
                (textY + 12f),
                18f,
                18f,
                textBackgroundPaint
            )
            canvas.drawText(label, startX - textWidth / 2f, textY, textPaint)
        }
    }

    private companion object {
        const val VIRTUAL_TRACE_DURATION_MS = 600L
        const val TRACE_HOLD_MS = 700L
    }
}
