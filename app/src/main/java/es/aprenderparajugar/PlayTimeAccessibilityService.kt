package es.aprenderparajugar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class PlayTimeAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val zeroBalanceCheck = Runnable { enforceLockAfterFocusChange() }
    private val prefs by lazy { getSharedPreferences("aprender_para_jugar", MODE_PRIVATE) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        // A parent explicitly confirmed exiting; do not relaunch until the app is opened manually again.
        if (prefs.getBoolean("device_setup_in_progress", false)) return
        if (prefs.getBoolean("manual_exit_allowed", false)) return
        if (prefs.getBoolean("play_paused_for_screen_lock", false)) return
        // Do not treat the keyboard or other focus changes inside the parental PIN/configuration flow as leaving play.
        if (prefs.getBoolean("parent_area_active", false)) return
        val now = System.currentTimeMillis()
        val deadline = prefs.getLong("play_deadline_ms", 0L)

        // Start the countdown as soon as the app loses focus with earned time.
        if (deadline <= 0L) {
            val balance = prefs.getInt("balance_seconds", 0).coerceAtLeast(0)
            val minimum = prefs.getInt("minimum_play_minutes", 5).coerceIn(0, 60) * 60
            val sessionAuthorized = prefs.getBoolean("play_session_authorized", false)
            if (sessionAuthorized || balance >= minimum && balance > 0) {
                prefs.edit()

                    .putLong("play_deadline_ms", now + balance.toLong() * 1000L)

                    .putInt("balance_seconds", balance)

                    .putLong("expiry_last_launch_ms", 0L)

                    .putBoolean("play_session_authorized", true)

                    .putBoolean("play_paused_for_screen_lock", false)

                    .commit()
                try {
                    androidx.core.content.ContextCompat.startForegroundService(
                        this,
                        Intent(this, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                    )
                } catch (_: Exception) { }
                return
            }
            // Recheck after the Activity lifecycle has persisted its latest balance,
            // then bring the app back if the minimum for leaving has not been met.
            handler.removeCallbacks(zeroBalanceCheck)
            handler.postDelayed(zeroBalanceCheck, 800L)
            return
        }
        if (now < deadline) return
        val lastAttempt = prefs.getLong("expiry_last_launch_ms", 0L)
        if (now - lastAttempt < 1500L) return

        prefs.edit()
            .putInt("balance_seconds", 0)
            .putLong("play_deadline_ms", 0L)
            .putBoolean("play_session_authorized", false)
            .putBoolean("play_paused_for_screen_lock", false)
            .putLong("expiry_last_launch_ms", now)
            .commit()
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

    private fun enforceLockAfterFocusChange() {
        if (prefs.getBoolean("device_setup_in_progress", false) ||
            prefs.getBoolean("manual_exit_allowed", false) ||
            prefs.getBoolean("parent_area_active", false) ||
            prefs.getBoolean("play_paused_for_screen_lock", false)) return

        val now = System.currentTimeMillis()
        val deadline = prefs.getLong("play_deadline_ms", 0L)
        if (deadline > now) return

        val balance = prefs.getInt("balance_seconds", 0).coerceAtLeast(0)
        val minimum = prefs.getInt("minimum_play_minutes", 5).coerceIn(0, 60) * 60
        val sessionAuthorized = prefs.getBoolean("play_session_authorized", false)
        if (sessionAuthorized || balance >= minimum && balance > 0) {
            // The reward arrived during the lifecycle transition. Start consuming it once.
            prefs.edit()

                .putLong("play_deadline_ms", now + balance.toLong() * 1000L)

                .putInt("balance_seconds", balance)

                .putLong("expiry_last_launch_ms", 0L)

                .putBoolean("play_session_authorized", true)

                .putBoolean("play_paused_for_screen_lock", false)

                .commit()
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, PlayTimeService::class.java).setAction(PlayTimeService.ACTION_START)
                )
            } catch (_: Exception) { }
            return
        }

        // No eligible session remains: revoke stale authorization before relaunching.
        prefs.edit().putBoolean("play_session_authorized", false).commit()
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
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(zeroBalanceCheck)
        super.onDestroy()
    }
}