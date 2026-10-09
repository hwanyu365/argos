package io.github.hwanyu365.argos.child

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** OS 상태를 순수 로직이 쓰는 형태로 옮긴다. 판단은 MonitorPolicy·SessionBuilder 가 한다. */
class DeviceState(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val pm = context.packageManager

    private companion object {
        // UsageEvents.Event.ACTIVITY_DESTROYED 는 API 29 에 공개됐다. 값은 그 이전 버전에서도 같아 상수로 둔다.
        const val ACTIVITY_DESTROYED = 24
    }

    fun events(from: Long, to: Long): List<UsageEvent> {
        val out = mutableListOf<UsageEvent>()
        val raw = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        while (raw.getNextEvent(e)) {
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Type.PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvent.Type.STOPPED
                ACTIVITY_DESTROYED -> UsageEvent.Type.DESTROYED
                UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEvent.Type.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Type.SCREEN_OFF
                else -> continue
            }
            // 화면(class) 이름은 PiP 앱의 다른 화면이 숨겨지는 것과 PiP 종료를 구분하는 데 쓴다 (S#9).
            out += UsageEvent(type, e.packageName, e.timeStamp, e.className)
        }
        return out
    }

    /** S#5: 기본 런처, 시스템 UI, argos 자신. */
    fun excludedPackages(): Set<String> {
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return setOfNotNull(home, "com.android.systemui", context.packageName)
    }

    /** X#1: 알림 접근이 없으면 조회할 수 없으므로 빈 목록이다. */
    fun mediaSessions(): List<MediaInfo> = runCatching {
        context.getSystemService(android.media.session.MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaListenerService::class.java))
            .map { MediaInfo(it.packageName, it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING, it.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)) }
    }.getOrDefault(emptyList())

    fun label(pkg: String): String = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    val screenOn: Boolean get() = context.getSystemService(PowerManager::class.java).isInteractive

    fun permissions(): Permissions {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        val usage = mode == AppOpsManager.MODE_ALLOWED
        val post = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        val a11y = Permissions.enabledIn(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), context.packageName)
        // API 26 은 확인 API 가 없어 설정 문자열로 본다.
        val listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, MediaListenerService::class.java))
        } else {
            Permissions.enabledIn(Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"), context.packageName)
        }
        return Permissions(usage, post, battery, a11y, listener)
    }
}
