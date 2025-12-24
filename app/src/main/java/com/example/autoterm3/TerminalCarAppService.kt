package com.example.autoterm3

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for the Android Auto app.
 * This service is declared in the manifest and instantiated by the car host.
 */
class TerminalCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // For development, allow any host. In production, you'd validate against known hosts.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return TerminalSession()
    }
}

/**
 * Represents a single connection to the car host.
 * A new session is created each time the app is launched on the car display.
 */
class TerminalSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return TerminalScreen(carContext)
    }
}
