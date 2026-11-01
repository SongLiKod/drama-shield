package com.dramashield.app.service.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import com.dramashield.app.util.log.AppLogger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用无障碍截图 + 本地中文 OCR 读取屏幕上的可见文字。
 *
 * 红果部分游戏广告把“上滑继续看短剧”画在 SurfaceView/WebView 里，
 * 无障碍节点树读不到，只能从屏幕像素识别。
 */
class ScreenTextOcr(private val service: AccessibilityService) {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var lastAttemptAt = 0L
    private var lastFailureLogAt = 0L
    private var lastTextLogAt = 0L

    /**
     * 异步截屏并识别。识别成功后在主线程回调完整文字。
     * 同一时间只允许一个 OCR 任务，避免连续截图拖慢服务。
     */
    fun scan(onRecognized: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAttemptAt < MIN_INTERVAL_MS) return
        if (!busy.compareAndSet(false, true)) return
        lastAttemptAt = now

        runCatching {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        var softwareBitmap: Bitmap? = null
                        var hardwareBitmap: Bitmap? = null
                        try {
                            hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (throwable: Throwable) {
                            AppLogger.e("OCR screenshot convert failed", throwable)
                        } finally {
                            // 硬件位图、硬件缓冲区都只用于本次识别，转换完立即释放。
                            runCatching { hardwareBitmap?.recycle() }
                            runCatching { screenshot.hardwareBuffer.close() }
                        }

                        val bitmap = softwareBitmap
                        if (bitmap == null) {
                            busy.set(false)
                            return
                        }

                        var scaled: Bitmap? = null
                        try {
                            val ocrBitmap = scaleDown(bitmap)
                            scaled = ocrBitmap
                            if (ocrBitmap !== bitmap) bitmap.recycle()

                            recognizer.process(InputImage.fromBitmap(ocrBitmap, 0))
                                .addOnSuccessListener { result ->
                                    try {
                                        val text = result.text
                                        val nowMs = System.currentTimeMillis()
                                        if (nowMs - lastTextLogAt > 10_000L) {
                                            lastTextLogAt = nowMs
                                            AppLogger.d("OCR captured chars=${text.length}")
                                        }
                                        if (text.isNotBlank()) onRecognized(text)
                                    } catch (throwable: Throwable) {
                                        AppLogger.e("OCR text callback failed", throwable)
                                    } finally {
                                        runCatching { ocrBitmap.recycle() }
                                        busy.set(false)
                                    }
                                }
                                .addOnFailureListener { throwable ->
                                    AppLogger.e("OCR recognize failed", throwable)
                                    runCatching { ocrBitmap.recycle() }
                                    busy.set(false)
                                }
                        } catch (throwable: Throwable) {
                            AppLogger.e("OCR process failed", throwable)
                            runCatching { scaled?.recycle() }
                            if (scaled == null) runCatching { bitmap.recycle() }
                            busy.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        busy.set(false)
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastFailureLogAt > 30_000L) {
                            lastFailureLogAt = nowMs
                            AppLogger.e("OCR screenshot failed code=$errorCode")
                        }
                    }
                }
            )
        }.onFailure { throwable ->
            busy.set(false)
            AppLogger.e("OCR screenshot request failed", throwable)
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        // 跳过文字通常在屏幕底部，先裁掉上半屏，保留更高分辨率给底部文字。
        val startY = (source.height * 0.45f).toInt().coerceIn(0, source.height - 1)
        val cropped = Bitmap.createBitmap(
            source,
            0,
            startY,
            source.width,
            source.height - startY
        )
        val maxWidth = 900
        if (cropped.width <= maxWidth) return cropped
        val height = (cropped.height.toLong() * maxWidth / cropped.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, maxWidth, height, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    fun close() {
        runCatching { recognizer.close() }
        screenshotExecutor.shutdownNow()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 900L
    }
}
