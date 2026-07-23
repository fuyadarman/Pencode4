package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class VibeAgentService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VibeAgentService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "VibeAgentService action: $action")
        
        if (action == ACTION_START) {
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification)
        } else if (action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val intent = try {
            val mainActivityClass = Class.forName("com.example.MainActivity")
            Intent(this, mainActivityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } catch (e: Exception) {
            null
        }

        val pendingIntent = intent?.let {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            android.app.PendingIntent.getActivity(this, 0, it, flags)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Coding Agent Active")
            .setContentText("The AI agent is working fully in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Agent Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the AI Coder executing when the app is closed"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "VibeAgentService"
        private const val CHANNEL_ID = "vibe_agent_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_AGENT"
        const val ACTION_STOP = "com.example.action.STOP_AGENT"
    }
}
