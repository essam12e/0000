package com.downloader.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.downloader.app.MainActivity
import com.downloader.app.R
import com.downloader.app.data.DownloadTask
import com.downloader.app.data.Fmt
import com.downloader.app.data.TaskState
import com.downloader.app.engine.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** خدمة Foreground تُبقي التحميل يعمل في الخلفية وتعرض إشعاراً بالنسبة والسرعة مع إيقاف/إلغاء. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private var started = false
    // يُبقي المعالج يعمل والشاشة مطفأة حتى لا يتوقف التحميل (يُحرَّر فور انتهاء التحميلات).
    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "downloader:downloads").apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        scope.launch {
            DownloadManager.tasks.collect { tasks ->
                val active = tasks.filter { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }
                if (active.isEmpty()) {
                    if (started) stopNow()
                } else if (started && canNotify()) {
                    nm.notify(PROGRESS_ID, progressNotification(active))
                }
                delay(600) // لا نحدّث الإشعار أكثر من مرتين في الثانية تقريباً
            }
        }
        scope.launch {
            DownloadManager.completed.collect { (title, count) -> notifyDone(title, count) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> DownloadManager.pauseAll()
            ACTION_CANCEL -> DownloadManager.cancelAll()
        }
        val active = DownloadManager.tasks.value.filter { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }
        try {
            ServiceCompat.startForeground(
                this, PROGRESS_ID, progressNotification(active),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            started = true
            runCatching { wakeLock.acquire(6 * 60 * 60 * 1000L) }
        } catch (t: Throwable) {
            // النظام قد يمنع بدء الخدمة من الخلفية؛ التحميل يستمر ما دام التطبيق حياً.
        }
        if (active.isEmpty()) stopNow()
        return START_NOT_STICKY
    }

    /** أندرويد 15+: مهلة خدمات dataSync. نوقف التحميلات مؤقتاً ليستكملها المستخدم لاحقاً. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        DownloadManager.pauseAll()
        stopNow()
    }

    private fun stopNow() {
        started = false
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progressNotification(active: List<DownloadTask>): Notification {
        val running = active.filter { it.state == TaskState.RUNNING }
        val single = active.singleOrNull()
        val known = running.filter { it.progress >= 0 }
        val avg = if (known.isEmpty()) -1f else known.map { it.progress }.average().toFloat()
        val title = single?.spec?.title ?: "جاري تحميل ${Fmt.files(active.size)}"
        val text = buildList {
            if (avg >= 0) add(Fmt.percent(avg))
            (single ?: running.firstOrNull())?.let { t ->
                Fmt.speed(t.speed)?.let(::add) ?: t.speed?.let(::add)
                if (single != null) t.stage?.let(::add)
            }
            if (isEmpty()) add("في الانتظار")
        }.joinToString("  ·  ") { Fmt.iso(it) }
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFFD9B36A.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, (avg * 100).toInt().coerceAtLeast(0), avg < 0)
            .setContentIntent(openApp())
            .addAction(0, if (active.size > 1) "إيقاف الكل مؤقتاً" else "إيقاف مؤقت", action(ACTION_PAUSE, 1))
            .addAction(0, if (active.size > 1) "إلغاء الكل" else "إلغاء", action(ACTION_CANCEL, 2))
            .build()
    }

    private fun notifyDone(title: String, count: Int) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("اكتمل التحميل")
            .setContentText(if (count > 1) "$title  ·  ${Fmt.files(count)}" else title)
            .setColor(0xFFD9B36A.toInt())
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        nm.notify(title.hashCode(), n)
    }

    companion object {
        private const val CHANNEL_PROGRESS = "downloads_progress"
        private const val CHANNEL_DONE = "downloads_done"
        private const val PROGRESS_ID = 41
        private const val ACTION_PAUSE = "com.downloader.app.PAUSE_ALL"
        private const val ACTION_CANCEL = "com.downloader.app.CANCEL_ALL"

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
