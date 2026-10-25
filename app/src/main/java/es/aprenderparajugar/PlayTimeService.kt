package es.aprenderparajugar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.Locale

class PlayTimeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("aprender_para_jugar", MODE_PRIVATE) }
    private var expiryHandled = false
    private val tick = object : Runnable {
        override fun run() {
            val deadline = prefs.getLong("play_deadline_ms", 0L)
            val remaining = if (deadline > 0L) ((deadline - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0) else 0
            prefs.edit().putInt("balance_seconds", remaining).apply()
            if (remaining <= 0) {
                if (!expiryHandled) {
                    expiryHandled = true
                    val launch = Intent(this@PlayTimeService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("play_time_expired", true)
                    }
                    try { startActivity(launch) } catch (_: Exception) { }
                    showExpiredNotification()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            showCountdownNotification(remaining)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Tiempo de juego", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Cuenta atrás del tiempo de juego ganado"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacks(tick)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        expiryHandled = false
        if (prefs.getLong("play_deadline_ms", 0L) <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Calculando tiempo…", true))
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String, ongoing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Aprender para jugar")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCountdownNotification(seconds: Int) {
        val text = String.format(Locale("es", "ES"), "Tiempo de juego restante: %02d:%02d", seconds / 60, seconds % 60)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, true))
    }

    private fun showExpiredNotification() {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification("Se acabó el tiempo de juego. Vuelve a Aprender para jugar.", false)
        )
    }

    companion object {
        const val ACTION_START = "es.aprenderparajugar.action.START_PLAY_TIME"
        const val ACTION_STOP = "es.aprenderparajugar.action.STOP_PLAY_TIME"
        private const val CHANNEL_ID = "play_time_countdown"
        private const val NOTIFICATION_ID = 4102
    }
}