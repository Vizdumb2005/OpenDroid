package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.settings.AppSettings

class OpenDroidService : Service() {

  companion object {
    const val CHANNEL_ID = "opendroid_runtime_service"
    const val NOTIFICATION_ID = 5050
    const val ACTION_START = "ACTION_START_OPENDROID_SERVICE"
    const val ACTION_STOP = "ACTION_STOP_OPENDROID_SERVICE"
    const val ACTION_UPDATE_STATUS = "ACTION_UPDATE_STATUS_OPENDROID_SERVICE"
    const val EXTRA_STATUS = "EXTRA_STATUS"

    fun startService(context: Context) {
      val intent = Intent(context, OpenDroidService::class.java).apply {
        action = ACTION_START
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, OpenDroidService::class.java).apply {
        action = ACTION_STOP
      }
      context.stopService(intent)
    }

    fun start(context: Context) = startService(context)
    fun stop(context: Context) = stopService(context)

    @Volatile private var instance: OpenDroidService? = null

    fun updateStatus(context: Context, statusText: String) {
      val active = instance
      if (active != null) {
        active.updateStatus(statusText)
        return
      }
      val intent = Intent(context, OpenDroidService::class.java).apply {
        action = ACTION_UPDATE_STATUS
        putExtra(EXTRA_STATUS, statusText)
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {}
    }
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  override fun onCreate() {
    super.onCreate()
    instance = this
    createNotificationChannel()

    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenDroid::RuntimeLock").apply {
      setReferenceCounted(false)
    }

    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OpenDroid::WifiLock")?.apply {
      setReferenceCounted(false)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      releaseLocks()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    acquireLocks()
    val statusText = if (intent?.action == ACTION_UPDATE_STATUS) {
      intent.getStringExtra(EXTRA_STATUS) ?: "Linux sandbox active"
    } else {
      "Linux sandbox runtime active in background"
    }
    val notification = buildNotification(statusText)
    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
  }

  fun updateStatus(statusText: String) {
    val notification = buildNotification(statusText)
    val manager = getSystemService(NotificationManager::class.java)
    manager?.notify(NOTIFICATION_ID, notification)
  }

  private fun acquireLocks() {
    try {
      if (wakeLock?.isHeld != true) wakeLock?.acquire(24 * 60 * 60 * 1000L)
      if (wifiLock?.isHeld != true) wifiLock?.acquire()
    } catch (_: Exception) {}
  }

  private fun releaseLocks() {
    try {
      if (wakeLock?.isHeld == true) wakeLock?.release()
      if (wifiLock?.isHeld == true) wifiLock?.release()
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    if (instance == this) instance = null
    releaseLocks()
    super.onDestroy()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "OpenDroid Background Runtime",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Keeps OpenDroid Ubuntu sandbox, terminal and agent server running in the background"
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(contentText: String): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val stopIntent = Intent(this, OpenDroidService::class.java).apply {
      action = ACTION_STOP
    }
    val stopPendingIntent = PendingIntent.getService(
      this,
      1,
      stopIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("OpenDroid Runtime")
      .setContentText(contentText)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
