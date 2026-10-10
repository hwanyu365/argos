package io.github.hwanyu365.argos.child

/** spec FR#7. 선언 순서가 체크리스트 표시 순서다 (필수 먼저). */
enum class Permission(val required: Boolean) {
    USAGE(true),
    BATTERY(true),

    // 알림이 꺼져도 Foreground Service 는 유지된다 (GH-53 실측). 자녀가 알림을 끄는 것은 허용한다 (NFR#6).
    POST_NOTIFICATIONS(false),
    ACCESSIBILITY(false),
    NOTIFICATION_LISTENER(false)
}

data class Permissions(
    val usage: Boolean,
    val postNotifications: Boolean,
    val batteryExempt: Boolean,
    val accessibility: Boolean,
    val notificationListener: Boolean
) {
    fun granted(p: Permission) = when (p) {
        Permission.USAGE -> usage
        Permission.POST_NOTIFICATIONS -> postNotifications
        Permission.BATTERY -> batteryExempt
        Permission.ACCESSIBILITY -> accessibility
        Permission.NOTIFICATION_LISTENER -> notificationListener
    }

    val missing get() = Permission.entries.filterNot(::granted)
    val canStart get() = missing.none { it.required }

    companion object {
        /**
         * Settings.Secure 의 활성 서비스 목록(`pkg/class:pkg/class`)에 이 패키지가 있는지.
         * 부분 문자열로 비교하면 debug(`….debug`) 와 release 가 같이 설치됐을 때 서로의 권한을 자기 것으로 오인한다.
         */
        fun enabledIn(setting: String?, pkg: String): Boolean = setting.orEmpty().split(':').any { it.substringBefore('/') == pkg }
    }
}

/** spec §4.2 live 노드. updatedAt 은 업로드 시 서버 시각으로 채운다 (D#2). */
data class Live(
    val pkg: String? = null,
    val label: String? = null,
    val since: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val screenOn: Boolean,
    val pip: LivePip? = null
)

/** S#9: 현재 앱과 함께 보이는 PiP 앱. */
data class LivePip(val pkg: String, val label: String?, val since: Long)

object LiveReporter {
    /** FR#12: 바뀌면 즉시, 아니면 heartbeat 주기마다. */
    fun shouldUpload(next: Live, last: Live?, lastSentAt: Long, now: Long): Boolean = next != last || now - lastSentAt >= MonitorSchedule.HEARTBEAT_MS
}

object MonitorSchedule {
    const val HEARTBEAT_MS = 60_000L
    private const val POLL_MS = 3_000L

    /** NFR#7: 화면이 꺼지면 앱 전환이 없으므로 폴링을 멈춘다. */
    fun pollDelay(screenOn: Boolean): Long? = if (screenOn) POLL_MS else null
}
