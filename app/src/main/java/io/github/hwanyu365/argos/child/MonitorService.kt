package io.github.hwanyu365.argos.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import io.github.hwanyu365.argos.MainActivity
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.data.PkgKey
import io.github.hwanyu365.argos.data.Prefs

/**
 * 자녀 기기 감시 루프 (FR#8, FR#12, NFR#6, NFR#7).
 * 판단은 SessionBuilder·LiveReporter·MonitorSchedule 이 하고, 여기서는 OS·저장소·Firebase 와 연결만 한다.
 */
class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var device: DeviceState
    private lateinit var store: SessionStore
    private lateinit var builder: SessionBuilder
    private var lastLive: Live? = null
    private var lastSentAt = 0L
    private val labeledApps = mutableSetOf<String>()

    // 화면이 꺼지면 tick 이 60초 간격이 되므로, 켜지는 즉시 3초 폴링으로 되돌린다 (NFR#2).
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching { step() }.onFailure { Log.e(TAG, "tick failed", it) }
            handler.postDelayed(this, MonitorSchedule.pollDelay(device.screenOn) ?: MonitorSchedule.HEARTBEAT_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        device = DeviceState(this)
        store = SessionStore(this)
        builder = SessionBuilder(device.excludedPackages(), prefs.lastEventTs, prefs.openSession)
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(notification())
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenOnReceiver)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun step() {
        val now = System.currentTimeMillis()
        // 처음이거나 오래 꺼져 있었으면 OS 보관 범위 안에서만 소급한다 (S#8).
        val from = maxOf(builder.lastEventTs, now - BACKFILL_MS)
        // insert 후 커서 저장 전에 죽으면 같은 세션이 다시 들어오지만, SessionStore 가 (pkg, start) 중복을 무시한다.
        store.insert(builder.feed(device.events(from, now)))
        prefs.lastEventTs = builder.lastEventTs
        prefs.openSession = builder.current

        val open = builder.current
        val live = Live(open?.pkg, open?.pkg?.let(device::label), open?.start, open?.detail?.title, open?.detail?.url, device.screenOn)
        if (LiveReporter.shouldUpload(live, lastLive, lastSentAt, now) && upload(live)) {
            lastLive = live
            lastSentAt = now
        }
    }

    /** 페어링 전이거나 Firebase 미설정이면 로컬 수집만 한다. */
    private fun upload(live: Live): Boolean {
        val fid = prefs.familyId ?: return false
        if (FirebaseApp.getApps(this).isEmpty()) return false
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val family = FirebaseDatabase.getInstance().getReference("families/$fid")
        val perms = device.permissions()
        family.child("children/$uid/live").setValue(
            mapOf(
                "pkg" to live.pkg,
                // 규칙의 길이 상한(R#3)을 넘으면 쓰기 전체가 거부되므로 미리 자른다.
                "label" to live.label?.take(100),
                "since" to live.since,
                "title" to live.title?.take(300),
                "url" to live.url?.take(2048),
                "screenOn" to live.screenOn,
                "updatedAt" to ServerValue.TIMESTAMP,
                "perms" to mapOf("usage" to perms.usage, "a11y" to perms.accessibility, "notif" to perms.notificationListener)
            )
        )
        // 부모 기기에 없는 앱도 이름을 보여주기 위해 처음 본 앱의 이름을 공유한다 (R#6).
        if (live.pkg != null && live.label != null && labeledApps.add(live.pkg)) {
            family.child("apps/${PkgKey.encode(live.pkg)}").setValue(mapOf("label" to live.label.take(100)))
        }
        return true
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.monitor_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.monitor_title))
            .setContentText(getString(R.string.monitor_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "ArgosMonitor"
        private const val CHANNEL = "monitor"
        private const val NOTIFICATION_ID = 1
        private const val BACKFILL_MS = 24 * 60 * 60_000L
        private const val WATCHDOG_JOB = 1
        private const val WATCHDOG_MS = 15 * 60_000L

        /** 감시를 켜고 서비스와 watchdog 을 띄운다. 재부팅·watchdog 도 이 경로로 되살린다 (FR#9). */
        fun start(context: Context) {
            Prefs(context).monitoring = true
            context.startForegroundService(Intent(context, MonitorService::class.java))
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(WATCHDOG_JOB, ComponentName(context, Watchdog::class.java)).setPeriodic(WATCHDOG_MS).setPersisted(true).build()
            )
        }

        fun stop(context: Context) {
            Prefs(context).monitoring = false
            context.getSystemService(JobScheduler::class.java).cancel(WATCHDOG_JOB)
            context.stopService(Intent(context, MonitorService::class.java))
        }

        fun resumeIfMonitoring(context: Context) {
            if (Prefs(context).monitoring) runCatching { context.startForegroundService(Intent(context, MonitorService::class.java)) }.onFailure { Log.w(TAG, "resume failed", it) }
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) resumeIfMonitoring(context)
        }
    }

    /**
     * 서비스가 OS 에 의해 죽었을 때 15분 안에 되살린다. 이미 떠 있으면 onStartCommand 만 다시 불린다.
     * Android 12+ 의 백그라운드 FGS 시작 제한은 배터리 최적화 제외 앱에는 적용되지 않는다 → 배터리 제외를 필수 권한으로 둔 이유 (FR#7).
     */
    class Watchdog : JobService() {
        override fun onStartJob(params: JobParameters?): Boolean {
            resumeIfMonitoring(this)
            return false
        }

        override fun onStopJob(params: JobParameters?) = false
    }
}
