package com.dramashield.app.service.action

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.dramashield.app.util.log.AppLogger
import kotlin.math.roundToInt

/**
 * 可拖动的半透明悬浮计数条，显示检测/命中/上滑次数。
 * 使用 TYPE_ACCESSIBILITY_OVERLAY，不需要额外悬浮窗权限。
 */
class FloatingCounterOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var detectedCount: Int = prefs.getInt(KEY_DETECTED, 0).coerceAtLeast(0) % COUNT_CYCLE
    private var hitCount: Int = prefs.getInt(KEY_HIT, 0).coerceAtLeast(0) % COUNT_CYCLE
    private var swipeCount: Int =
        prefs.getInt(KEY_SWIPE, prefs.getInt(KEY_LEGACY_COUNT, 0)).coerceAtLeast(0) % COUNT_CYCLE

    fun show() {
        handler.post {
            if (view != null) {
                updateText()
                return@post
            }
            val manager = service.getSystemService(WindowManager::class.java) ?: return@post
            windowManager = manager
            val counterView = TextView(service).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.parseColor("#991E3A8A"))
                    setStroke(dp(1), Color.parseColor("#55FFFFFF"))
                }
                elevation = dp(6).toFloat()
                alpha = 0.88f
                isClickable = true
                text = label()
            }

            val screen = screenSize(manager)
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefs.getInt(KEY_X, screen.first - dp(240)).coerceIn(0, screen.first)
                y = prefs.getInt(KEY_Y, screen.second / 3).coerceIn(0, screen.second)
            }

            attachDrag(counterView, layoutParams, manager, screen)

            runCatching { manager.addView(counterView, layoutParams) }
                .onSuccess {
                    view = counterView
                    params = layoutParams
                    counterView.post {
                        val maxX = (screen.first - counterView.width).coerceAtLeast(0)
                        val maxY = (screen.second - counterView.height).coerceAtLeast(0)
                        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
                        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
                        runCatching { manager.updateViewLayout(counterView, layoutParams) }
                        prefs.edit()
                            .putInt(KEY_X, layoutParams.x)
                            .putInt(KEY_Y, layoutParams.y)
                            .apply()
                    }
                    AppLogger.d("Floating counter shown at (${layoutParams.x},${layoutParams.y})")
                }
                .onFailure { AppLogger.e("Show floating counter failed", it) }
        }
    }

    fun hide() {
        handler.post {
            view?.let { runCatching { windowManager?.removeView(it) } }
            view = null
            params = null
            windowManager = null
        }
    }

    fun recordDetection() {
        handler.post {
            detectedCount = (detectedCount + 1) % COUNT_CYCLE
            prefs.edit().putInt(KEY_DETECTED, detectedCount).apply()
            updateText()
        }
    }

    fun recordHit() {
        handler.post {
            hitCount = (hitCount + 1) % COUNT_CYCLE
            prefs.edit().putInt(KEY_HIT, hitCount).apply()
            updateText()
        }
    }

    fun recordSwipe() {
        handler.post {
            swipeCount = (swipeCount + 1) % COUNT_CYCLE
            prefs.edit().putInt(KEY_SWIPE, swipeCount).apply()
            updateText()
        }
    }

    fun resetCounts() {
        handler.post {
            detectedCount = 0
            hitCount = 0
            swipeCount = 0
            prefs.edit()
                .putInt(KEY_DETECTED, 0)
                .putInt(KEY_HIT, 0)
                .putInt(KEY_SWIPE, 0)
                .apply()
            updateText()
            AppLogger.d("Floating counter reset")
        }
    }

    private fun updateText() {
        view?.text = label()
    }

    private fun label(): String = "测 $detectedCount · 中 $hitCount · 滑 $swipeCount"

    private fun attachDrag(
        counterView: TextView,
        layoutParams: WindowManager.LayoutParams,
        manager: WindowManager,
        screen: Pair<Int, Int>
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressed = false
        var longPressRunnable: Runnable? = null
        val touchSlop = dp(6)

        counterView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                    longPressed = false
                    longPressRunnable = Runnable {
                        if (!dragging) {
                            longPressed = true
                            resetCounts()
                        }
                    }.also { handler.postDelayed(it, LONG_PRESS_MS) }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!longPressed && !dragging &&
                        (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)
                    ) {
                        dragging = true
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }
                    if (dragging) {
                        val maxX = (screen.first - counterView.width).coerceAtLeast(0)
                        val maxY = (screen.second - counterView.height).coerceAtLeast(0)
                        layoutParams.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        layoutParams.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { manager.updateViewLayout(counterView, layoutParams) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (dragging) {
                        prefs.edit()
                            .putInt(KEY_X, layoutParams.x)
                            .putInt(KEY_Y, layoutParams.y)
                            .apply()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun screenSize(manager: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = manager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PREFS_NAME = "drama_shield_floating_counter"
        const val KEY_DETECTED = "detected_count"
        const val KEY_HIT = "hit_count"
        const val KEY_SWIPE = "swipe_count"
        const val KEY_LEGACY_COUNT = "swipe_count_legacy"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val COUNT_CYCLE = 1000
        const val LONG_PRESS_MS = 650L
    }
}
