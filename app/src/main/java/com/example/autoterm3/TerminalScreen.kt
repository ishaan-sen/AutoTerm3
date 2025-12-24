package com.example.autoterm3

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The main screen that displays terminal output on the car's display.
 * Using PaneTemplate for simplicity - will upgrade to NavigationTemplate for Surface later.
 */
class TerminalScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "TerminalScreen"
        private const val REFRESH_INTERVAL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var refreshCounter = 0

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshCounter++
            invalidate() // Triggers onGetTemplate() to be called again
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        Log.d(TAG, "Screen created")
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "Screen destroyed")
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate called, counter=$refreshCounter")

        val terminalOutput = buildString {
            appendLine("AUTOTERM3 DEBUG TERMINAL")
            appendLine("========================")
            appendLine("")
            appendLine("Refresh: $refreshCounter")
            appendLine("Time: ${System.currentTimeMillis()}")
            appendLine("")
            appendLine("$ ls -la /system")
            appendLine("drwxr-xr-x  root  4096  .")
            appendLine("drwxr-xr-x  root  4096  ..")
            appendLine("-rwxr-xr-x  shell  123  sh")
        }

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Terminal Output")
                    .addText(terminalOutput)
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("AutoTerm3")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
