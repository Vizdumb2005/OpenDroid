package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class OpenCodeBackgroundService : Service() {

  companion object {
    const val CHANNEL_ID = "opencode_sandbox_service"
    const val NOTIFICATION_ID = 4040
    const val ACTION_START = "ACTION_START_OPENCODE_SERVICE"
    const val ACTION_STOP = "ACTION_STOP_OPENCODE_SERVICE"

    fun startService(context: Context) {
      val intent = Intent(context, OpenCodeBackgroundService::class.java).apply {
        action = ACTION_START
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, OpenCodeBackgroundService::class.java).apply {
        action = ACTION_STOP
      }
      context.stopService(intent)
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val notification = buildNotification()
    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "OpenCode Sandbox Daemon",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Keeps OpenCode agent and Ubuntu sandbox active in background"
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("OpenCode Sandbox Active")
      .setContentText("Ubuntu 24.04 LTS daemon running in background")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
