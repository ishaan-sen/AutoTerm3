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

    // Buffer options
    private val bufferSize = 200
    private val buffer = java.util.ArrayDeque<String>(bufferSize)
    
    // TCP Server
    private var serverSocket: java.net.ServerSocket? = null
    private var serverThread: Thread? = null
    @Volatile private var isRunning = false

    private val textPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
        // Fixed 15px text size per user request
        textSize = 15f
    }

    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
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
            // Fixed 15px text size per user request, already set in paint
            Log.d(TAG, "Text size: ${textPaint.textSize}")
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
    
    // We don't need a default string anymore, but we can seed the buffer
    init {
        lifecycle.addObserver(this)
        synchronized(buffer) {
            buffer.add("AUTOTERM3 - LISTENING ON PORT 9000")
            buffer.add("Type 'nc localhost 9000' (adb forward tcp:9000 tcp:9000)")
            buffer.add("-------------------------------------------------------")
        }
    }
    
    private var surfaceCallbackRegistered = false

    override fun onCreate(owner: LifecycleOwner) {
        Log.d(TAG, "Screen onCreate lifecycle")
        startServer()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "Screen destroyed")
        stopServer()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun startServer() {
        if (isRunning) return
        isRunning = true
        serverThread = Thread {
            try {
                serverSocket = java.net.ServerSocket(9000)
                Log.d(TAG, "Server started on port 9000")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Error accepting client", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }.apply { start() }
    }

    private fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server", e)
        }
        serverThread?.interrupt()
    }

    private fun handleClient(client: java.net.Socket?) {
        client ?: return
        Log.d(TAG, "Client connected: ${client.inetAddress}")
        Thread {
            try {
                client.getInputStream().bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        addToBuffer(line!!)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client connection error", e)
            } finally {
                try { client.close() } catch (e: Exception) {}
                Log.d(TAG, "Client disconnected")
            }
        }.start()
    }

    private fun addToBuffer(line: String) {
        synchronized(buffer) {
            if (buffer.size >= bufferSize) {
                buffer.pollFirst()
            }
            buffer.add(line)
        }
        // Force refresh immediately when data comes in
        handler.post { renderToSurface() }
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

                // Render buffer from bottom up
                val fm = textPaint.fontMetrics
                val lineHeight = (fm.descent - fm.ascent) * 1.2f
                
                // Start drawing from bottom of screen (minus some padding)
                var y = canvas.height - 10f - fm.descent
                
                synchronized(buffer) {
                    val iterator = buffer.descendingIterator()
                    while (iterator.hasNext()) {
                        val line = iterator.next()
                        canvas.drawText(line, 20f, y, textPaint)
                        y -= lineHeight
                        if (y < 0 - lineHeight) break // Stop if we've gone off top
                    }
                }
            } finally {
                s.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Render error", e)
        }
    }
}
