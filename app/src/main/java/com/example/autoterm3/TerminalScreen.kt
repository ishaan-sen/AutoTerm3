package com.example.autoterm3

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Displays terminal output as monospaced white text on black background.
 * Uses NavigationTemplate for Surface access.
 */
class TerminalScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "TerminalScreen"
        private const val REFRESH_INTERVAL_MS = 1000L
    }

    private var surface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private var refreshCounter = 0

    private val textPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val terminalContent: String
        get() = buildString {
            val width = 60
            appendLine("╔${"═".repeat(width)}╗")
            appendLine("║${"  AUTOTERM3 - DEBUG TERMINAL".padEnd(width)}║")
            appendLine("╠${"═".repeat(width)}╣")
            appendLine("║${" ".repeat(width)}║")
            appendLine("║${"  Refresh Count: ${"%06d".format(refreshCounter)}".padEnd(width)}║")
            appendLine("║${"  Timestamp: ${System.currentTimeMillis()}".padEnd(width)}║")
            appendLine("║${" ".repeat(width)}║")
            appendLine("╠${"═".repeat(width)}╣")
            appendLine("║${"  SAMPLE OUTPUT:".padEnd(width)}║")
            appendLine("║${"  $ ls -la /system/bin".padEnd(width)}║")
            appendLine("║${"  drwxr-xr-x  2 root root  4096 Jan  1 00:00 .".padEnd(width)}║")
            appendLine("║${"  drwxr-xr-x 17 root root  4096 Jan  1 00:00 ..".padEnd(width)}║")
            appendLine("║${"  -rwxr-xr-x  1 root shell  123 Jan  1 00:00 sh".padEnd(width)}║")
            appendLine("║${"  -rwxr-xr-x  1 root shell  456 Jan  1 00:00 ls".padEnd(width)}║")
            appendLine("║${"  Memory: 2048 MB / 4096 MB (50%)".padEnd(width)}║")
            appendLine("║${"  CPU: 23% | GPU: 12% | Temp: 42°C".padEnd(width)}║")
            appendLine("╚${"═".repeat(width)}╝")
        }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshCounter++
            renderToSurface()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            Log.d(TAG, "Surface available: ${container.width}x${container.height} (dpi: ${container.dpi})")
            surface = container.surface
            surfaceWidth = container.width
            surfaceHeight = container.height
            // Fixed 15px text size per user request
            textPaint.textSize = 15f
            Log.d(TAG, "Text size set to: ${textPaint.textSize}")
            handler.post(refreshRunnable)
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            Log.d(TAG, "Surface destroyed")
            surface = null
            handler.removeCallbacks(refreshRunnable)
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            renderToSurface()
        }

        override fun onStableAreaChanged(stableArea: Rect) {}
        override fun onScroll(distanceX: Float, distanceY: Float) {}
        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {}
        override fun onFling(velocityX: Float, velocityY: Float) {}
    }

    init {
        lifecycle.addObserver(this)
    }
    
    private var surfaceCallbackRegistered = false

    override fun onCreate(owner: LifecycleOwner) {
        Log.d(TAG, "Screen onCreate lifecycle")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "Screen destroyed")
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate called")
        
        // Register surface callback here (host is definitely ready now)
        if (!surfaceCallbackRegistered) {
            try {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
                surfaceCallbackRegistered = true
                Log.d(TAG, "Surface callback registered in onGetTemplate")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set surface callback", e)
            }
        }
        
        // NavigationTemplate requires an action strip, but custom titles aren't allowed
        // Using PAN action (a built-in icon-only action)
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .build()
            
        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun renderToSurface() {
        val s = surface ?: return
        if (!s.isValid) return

        try {
            val canvas = s.lockCanvas(null) ?: return
            try {
                // Black background
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), bgPaint)

                // Draw text
                val fm = textPaint.fontMetrics
                val lineHeight = (fm.descent - fm.ascent) * 1.2f
                var y = 20f - fm.ascent

                for (line in terminalContent.lines()) {
                    if (y > canvas.height) break
                    canvas.drawText(line, 20f, y, textPaint)
                    y += lineHeight
                }
            } finally {
                s.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Render error", e)
        }
    }
}
