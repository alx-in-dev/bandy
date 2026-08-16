package li.cactus.bandy.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import li.cactus.bandy.core.domain.model.RecordingPhase
import li.cactus.bandy.core.domain.repository.AudioRecorderController
import org.koin.android.ext.android.inject

private const val CHANNEL_ID = "sift_recording"
private const val NOTIFICATION_ID = 1001
private const val ACTION_PAUSE = "li.cactus.bandy.action.PAUSE"
private const val ACTION_RESUME = "li.cactus.bandy.action.RESUME"
private const val ACTION_STOP = "li.cactus.bandy.action.STOP"

class RecordingForegroundService : Service() {

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RecordingForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingForegroundService::class.java))
        }
    }

    private val controller: AudioRecorderController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(RecordingPhase.RECORDING, 0L))
        observeJob = scope.launch {
            controller.session.collect { session ->
                if (session.phase == RecordingPhase.IDLE) {
                    stopSelf()
                } else {
                    updateNotification(session.phase, session.elapsedMs)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> scope.launch { controller.stop() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sift recording",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(phase: RecordingPhase, elapsedMs: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(phase, elapsedMs))
    }

    private fun buildNotification(phase: RecordingPhase, elapsedMs: Long): Notification {
        val seconds = elapsedMs / 1000
        val timeText = "%02d:%02d".format(seconds / 60, seconds % 60)
        val statusText = if (phase == RecordingPhase.PAUSED) "Paused" else "Recording"

        val pauseResumeAction = if (phase == RecordingPhase.PAUSED) {
            NotificationCompat.Action(0, "Resume", actionIntent(ACTION_RESUME))
        } else {
            NotificationCompat.Action(0, "Pause", actionIntent(ACTION_PAUSE))
        }
        val stopAction = NotificationCompat.Action(0, "Stop", actionIntent(ACTION_STOP))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sift — $statusText")
            .setContentText(timeText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
