package ru.gpstrips.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Состояние передачи координат для интерфейса. Живёт в процессе приложения,
 * сервис и экраны читают его напрямую.
 */
object LiveState {

    data class Status(
        val running: Boolean = false,
        val lastFix: LiveFix? = null,
        val lastSentAt: Long = 0L,
        val lastAttemptAt: Long = 0L,
        val sentCount: Int = 0,
        val lastError: String? = null
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun update(block: (Status) -> Status) {
        _status.update(block)
    }
}

/**
 * Сервис следит за свежим GPS-логом и отправляет новые координаты на сайт.
 * Работает как foreground-сервис, чтобы магнитола не убивала его в фоне.
 */
class LiveTrackingService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private lateinit var prefs: Prefs
    private lateinit var tail: LogTail
    private var lastNotifiedAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        tail = LogTail(this)
        LiveState.update { it.copy(running = true) }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForegroundNotification()
        if (loop == null) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? = null

    private suspend fun runLoop() {
        while (scope.isActive) {
            if (!prefs.liveEnabled) {
                stopSelf()
                return
            }
            val target = prefs.target
            if (target.isNullOrBlank()) {
                LiveState.update { it.copy(lastError = "Не выбрана папка с логами") }
            } else {
                // Если новых точек нет (стоянка), раз в минуту повторяем последнюю:
                // страница на сайте должна оставаться «на связи».
                val fix = runCatching { tail.poll(target) }.getOrNull()
                    ?: LiveState.status.value.lastFix
                if (fix != null) {
                    handleFix(fix)
                }
            }
            updateNotification()
            delay(prefs.liveIntervalSec * 1000L)
        }
    }

    private fun handleFix(fix: LiveFix) {
        val status = LiveState.status.value
        val changed = status.lastFix == null ||
            status.lastFix.lat != fix.lat ||
            status.lastFix.lon != fix.lon
        val heartbeat = System.currentTimeMillis() - status.lastAttemptAt > HEARTBEAT_MS

        LiveState.update { it.copy(lastFix = fix) }
        if (!changed && !heartbeat) return

        val endpoint = prefs.liveUrl
        if (!LiveSender.isValidEndpoint(endpoint)) {
            LiveState.update { it.copy(lastError = "Не задан адрес сервера (нужен http:// или https://)") }
            return
        }

        val now = System.currentTimeMillis()
        val error = LiveSender.send(endpoint, prefs.liveToken, prefs.liveDevice, fix)
        LiveState.update {
            it.copy(
                lastAttemptAt = now,
                lastSentAt = if (error == null) now else it.lastSentAt,
                sentCount = if (error == null) it.sentCount + 1 else it.sentCount,
                lastError = error
            )
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifiedAt < NOTIFY_EVERY_MS) return
        lastNotifiedAt = now
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val status = LiveState.status.value
        val fix = status.lastFix
        val text = when {
            status.lastError != null -> status.lastError
            fix == null -> "Жду координаты из лога GPS"
            else -> buildString {
                append(String.format(java.util.Locale.US, "%.5f, %.5f", fix.lat, fix.lon))
                fix.speedKmh?.let { append(" · ").append(Fmt.speed(it)) }
                append(" · ").append(Fmt.hhmm(status.lastSentAt.takeIf { it > 0 } ?: fix.timeMs))
            }
        }
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            android.content.Intent(this, ru.gpstrips.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GPS Поездки · передача координат")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Передача координат",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        LiveState.update { it.copy(running = false) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "live_tracking"
        private const val NOTIF_ID = 41
        private const val HEARTBEAT_MS = 60_000L
        private const val NOTIFY_EVERY_MS = 10_000L

        fun start(ctx: Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    ctx,
                    android.content.Intent(ctx, LiveTrackingService::class.java)
                )
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(android.content.Intent(ctx, LiveTrackingService::class.java))
        }
    }
}
