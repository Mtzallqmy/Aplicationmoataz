package ai.alaser.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import ai.alaser.app.MainActivity
import ai.alaser.app.R

class AgentForegroundService : Service() {
    private val activeTasks = linkedMapOf<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val task = intent?.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
        if (intent.action == ACTION_STOP) {
            activeTasks.remove(task)
            if (activeTasks.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            activeTasks[task] = intent.getStringExtra(EXTRA_LABEL) ?: "An Alaser task is running"
        }
        val notification = notification(activeTasks.values.last())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun notification(label: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent and Telegram tasks", NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alaser_logo)
            .setContentTitle("Alaser AI is working")
            .setContentText(label.take(120))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "alaser_active_tasks"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_START = "ai.alaser.app.START_TASK"
        private const val ACTION_STOP = "ai.alaser.app.STOP_TASK"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_LABEL = "label"

        fun start(context: Context, taskId: String, label: String) {
            context.startForegroundService(
                Intent(context, AgentForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .putExtra(EXTRA_LABEL, label),
            )
        }

        fun stop(context: Context, taskId: String) {
            context.startService(
                Intent(context, AgentForegroundService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_TASK_ID, taskId),
            )
        }
    }
}
