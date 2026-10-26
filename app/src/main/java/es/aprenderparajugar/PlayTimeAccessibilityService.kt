package es.aprenderparajugar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PlayTimeAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("aprender_para_jugar", MODE_PRIVATE) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        // A parent explicitly confirmed exiting; do not relaunch until the app is opened manually again.
        if (prefs.getBoolean("manual_exit_allowed", false)) return
        // Do not treat the keyboard or other focus changes inside the parental PIN/configuration flow as leaving play.
        if (prefs.getBoolean("parent_area_active", false)) return
        val now = System.currentTimeMillis()
        val deadline = prefs.getLong("play_deadline_ms", 0L)

        // Start the countdown as soon as the app loses focus with earned time.
        if (deadline <= 0L) {
            val balance = prefs.getInt("balance_seconds", 0).coerceAtLeast(0)
            if (balance > 0) {
                prefs.edit()
                    .putLong("play_deadline_ms", now + balance.toLong() * 1000L)
                    .putLong("expiry_last_launch_ms", 0L)
                    .apply()
                try {
                    androidx.core.content.ContextCompat.startForegroundService(
                        this,
                        Intent(this, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                    )
                } catch (_: Exception) { }
                return
            }
            // Zero balance is also a locked state: return to the learning app immediately.
            val lastAttempt = prefs.getLong("expiry_last_launch_ms", 0L)
            if (now - lastAttempt < 2000L) return
            prefs.edit().putLong("expiry_last_launch_ms", now).apply()
            try {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("play_time_expired", true)
                })
            } catch (_: Exception) {
                prefs.edit().putLong("expiry_last_launch_ms", 0L).apply()
            }
            return
        }
        if (now < deadline) return
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