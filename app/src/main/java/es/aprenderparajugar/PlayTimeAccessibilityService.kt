package es.aprenderparajugar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PlayTimeAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("aprender_para_jugar", MODE_PRIVATE) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val deadline = prefs.getLong("play_deadline_ms", 0L)
        if (deadline <= 0L || System.currentTimeMillis() < deadline) return
        if (event.packageName?.toString() == packageName) return
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong("expiry_last_launch_ms", 0L)
        if (now - lastAttempt < 1500L) return

        prefs.edit().putInt("balance_seconds", 0).putLong("expiry_last_launch_ms", now).apply()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("play_time_expired", true)
        }
        try {
            startActivity(launch)
        } catch (_: Exception) {
            prefs.edit().putLong("expiry_last_launch_ms", 0L).apply()
        }
    }

    override fun onInterrupt() = Unit
}