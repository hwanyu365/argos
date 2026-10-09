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
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import io.github.hwanyu365.argos.MainActivity
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.data.PkgKey
import io.github.hwanyu365.argos.data.Prefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 자녀 기기 감시 루프 (FR#8, FR#12, NFR#6, NFR#7).
 * 판단은 SessionBuilder·LiveReporter·MonitorSchedule 이 하고, 여기서는 OS·저장소·Firebase 와 연결만 한다.
 */
class MonitorService : Service() {
    // 24시간 소급 조회와 SQLite 쓰기가 메인 스레드를 막지 않도록 전용 스레드에서 돈다.
    private val thread = HandlerThread("argos-monitor").apply { start() }
    private val handler = Handler(thread.looper)
    private lateinit var prefs: Prefs
    private lateinit var device: DeviceState
    private lateinit var store: SessionStore
    private lateinit var builder: SessionBuilder
    private var lastLive: Live? = null
    private var lastSentAt = 0L

    // 감시 스레드와 Firebase 콜백(메인 스레드)이 함께 쓴다.
    private val labeledApps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var lastDailyAt = 0L
    private var lastDailyDate: LocalDate? = null
    private var lastPruneCutoff: LocalDate? = null

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
        builder = restoredBuilder()
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
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoredBuilder() = SessionBuilder(device.excludedPackages(), prefs.lastEventTs, prefs.openSession, prefs.pip)

    private fun step() {
        val now = System.currentTimeMillis()
        // 처음이거나 오래 꺼져 있었으면 OS 보관 범위 안에서만 소급한다 (S#8).
        builder.skipGap(now - BACKFILL_MS)
        val from = builder.lastEventTs
        // insert 후 커서 저장 전에 죽으면 같은 세션이 다시 들어오지만, SessionStore 가 (pkg, start) 중복을 무시한다.
        val closed = builder.feed(device.events(from, now))
        try {
            store.insert(closed)
        } catch (e: Exception) {
            // 메모리 커서는 이미 전진했으므로, 저장된 커서로 되돌려 다음 tick 에 같은 구간을 다시 처리한다.
            builder = restoredBuilder()
            throw e
        }
        prefs.lastEventTs = builder.lastEventTs
        prefs.openSession = builder.current
        prefs.pip = builder.pip

        val open = builder.current
        val pip = builder.visiblePip(now)?.let { LivePip(it.pkg, device.label(it.pkg), it.start) }
        val live = Live(open?.pkg, open?.pkg?.let(device::label), open?.start, open?.detail?.title, open?.detail?.url, device.screenOn, pip)
        if (LiveReporter.shouldUpload(live, lastLive, lastSentAt, now) && upload(live)) {
            lastLive = live
            lastSentAt = now
        }
        val today = LocalDate.now()
        if (now - lastDailyAt >= DAILY_MS || today != lastDailyDate) {
            lastDailyAt = now
            lastDailyDate = today
            uploadDaily(today, now)
        }
    }

    /**
     * FR#13: 마지막으로 올린 날부터 오늘까지 일별 합계를 다시 계산해 덮어쓴다 (A#3).
     * 진행 중 세션과 PiP 는 지금 시각까지 더한다 (A#2). 보관 기간이 지난 기록을 지운다: 로컬은 매번, 원격은 기준일이 바뀔 때 (FR#14).
     */
    private fun uploadDaily(today: LocalDate, now: Long) {
        val zone = ZoneId.systemDefault()
        val cutoff = DailyAggregator.retentionCutoff(today)
        store.deleteEndedBefore(cutoff.atStartOfDay(zone).toInstant().toEpochMilli())
        val fid = prefs.familyId ?: return
        if (FirebaseApp.getApps(this).isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val earliest = store.earliestStart()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val days = DailyAggregator.uploadDays(prefs.dailyUploaded, earliest, today)
        val from = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val open = listOfNotNull(builder.current, builder.pip?.open).map { Session(it.pkg, it.start, now) }
        val agg = DailyAggregator.aggregate(store.endingAfter(from) + open, zone)
        val update = mutableMapOf<String, Any?>()
        days.forEach { d ->
            val day = agg[d]
            // A#3: 날짜 노드 전체를 덮어써 재시도·중복 업로드에도 값이 일정하다.
            update["children/$uid/daily/$d"] = day?.apps?.mapKeys { PkgKey.encode(it.key) }?.takeIf { it.isNotEmpty() }
            update["children/$uid/dailyTotal/$d"] = day?.totalSec?.takeIf { it > 0 }
        }
        // 부모 기기에 없는 앱도 이름을 보여주기 위해 집계에 나온 앱의 이름을 공유한다 (R#6).
        val newLabels = agg.values.flatMap { it.apps.keys }.distinct().filter { labeledApps.add(it) }
        newLabels.forEach { pkg -> update["apps/${PkgKey.encode(pkg)}"] = mapOf("label" to device.label(pkg).take(100)) }
        val family = FirebaseDatabase.getInstance().getReference("families/$fid")
        family.updateChildren(update)
            .addOnSuccessListener { prefs.dailyUploaded = today }
            .addOnFailureListener {
                // 실패하면 다음 재전송 때 앱 이름도 다시 올라가도록 공유 표시를 되돌린다.
                labeledApps.removeAll(newLabels.toSet())
                Log.w(TAG, "daily upload failed", it)
            }
        // 기준일은 하루에 한 번 바뀌므로 그때만 원격을 조회해 정리한다 (다운로드 한도 절약).
        if (cutoff != lastPruneCutoff) {
            lastPruneCutoff = cutoff
            pruneRemote(family.child("children/$uid"), cutoff)
        }
    }

    /** FR#14: 원격의 보관 기간이 지난 날짜를 지운다. 날짜 키는 사전순이 곧 날짜순이다. */
    private fun pruneRemote(child: DatabaseReference, cutoff: LocalDate) {
        listOf("daily", "dailyTotal").forEach { node ->
            child.child(node).orderByKey().endBefore(cutoff.toString()).get().addOnSuccessListener { snap ->
                val old = snap.children.mapNotNull { it.key }.associate { "$node/$it" to null }
                if (old.isNotEmpty()) child.updateChildren(old)
            }
        }
    }

    /** 페어링 전이거나 Firebase 미설정이면 로컬 수집만 한다. */
    private fun upload(live: Live): Boolean {
        val fid = prefs.familyId ?: return false
        if (FirebaseApp.getApps(this).isEmpty()) return false
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val family = FirebaseDatabase.getInstance().getReference("families/$fid")
        val perms = device.permissions()
        // 실패해도 다음 변경이나 60초 heartbeat 에 다시 올라가므로 재시도 큐 없이 기록만 남긴다.
        family.child("children/$uid/live").setValue(
            mapOf(
                "pkg" to live.pkg,
                // 규칙의 길이 상한(R#3)을 넘으면 쓰기 전체가 거부되므로 미리 자른다.
                "label" to live.label?.take(100),
                "since" to live.since,
                "title" to live.title?.take(300),
                "url" to live.url?.take(2048),
                "screenOn" to live.screenOn,
                "pip" to live.pip?.let { mapOf("pkg" to it.pkg, "label" to it.label?.take(100), "since" to it.since) },
                "updatedAt" to ServerValue.TIMESTAMP,
                "perms" to mapOf("usage" to perms.usage, "a11y" to perms.accessibility, "notif" to perms.notificationListener)
            )
        ).addOnFailureListener { Log.w(TAG, "live upload failed", it) }
        // 부모 기기에 없는 앱도 이름을 보여주기 위해 처음 본 앱의 이름을 공유한다 (R#6).
        if (live.pkg != null && live.label != null && labeledApps.add(live.pkg)) {
            family.child("apps/${PkgKey.encode(live.pkg)}").setValue(mapOf("label" to live.label.take(100)))
                .addOnFailureListener { labeledApps.remove(live.pkg) }
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
        private const val DAILY_MS = 15 * 60_000L
        private const val WATCHDOG_JOB = 1
        private const val WATCHDOG_MS = 15 * 60_000L

        /** 감시를 켜고 서비스와 watchdog 을 띄운다. 재부팅·watchdog 도 이 경로로 되살린다 (FR#9). */
        fun start(context: Context) {
            Prefs(context).monitoring = true
            context.startForegroundService(Intent(context, MonitorService::class.java))
            scheduleWatchdog(context)
        }

        /**
         * 강제 종료(설정의 '강제 중지')는 서비스와 함께 예약된 watchdog 도 지운다.
         * 자녀가 앱을 다시 열면 둘 다 되살린다. 화면이 떠 있을 때라 백그라운드 시작 제한도 받지 않는다.
         */
        fun resumeFromUi(context: Context) {
            if (!Prefs(context).monitoring) return
            resumeIfMonitoring(context)
            scheduleWatchdog(context)
        }

        private fun scheduleWatchdog(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            // 다시 예약하면 주기가 처음부터 다시 시작되므로, 이미 예약돼 있으면 그대로 둔다.
            if (js.getPendingJob(WATCHDOG_JOB) != null) return
            js.schedule(
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
