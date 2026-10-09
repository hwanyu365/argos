package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.Live
import io.github.hwanyu365.argos.data.PkgKey

/** spec §6.4. '상세 제한'은 접근성·알림 접근 기능이 생기는 UC3 에서 추가한다. */
enum class Liveness {
    OK,
    DELAYED,
    STOPPED
    ;

    companion object {
        private const val OK_MS = 2 * 60_000L
        private const val DELAYED_MS = 5 * 60_000L

        /** 기록이 한 번도 없으면 판정하지 않는다(null). 네트워크 끊김과 의도적 중단은 구분하지 않는다. */
        fun of(sinceUpdateMs: Long?, usageGranted: Boolean): Liveness? = when {
            sinceUpdateMs == null -> null
            !usageGranted || sinceUpdateMs > DELAYED_MS -> STOPPED
            sinceUpdateMs > OK_MS -> DELAYED
            else -> OK
        }
    }
}

/** FR#15 자녀 카드에 보여줄 값. 시각 계산은 서버 기준이다 (D#2). */
data class ChildCard(
    val name: String,
    val appLabel: String?,
    val elapsedMs: Long?,
    val sinceUpdateMs: Long?,
    val title: String?,
    val url: String?,
    val screenOn: Boolean,
    val liveness: Liveness?
) {
    companion object {
        fun serverNow(localNow: Long, offsetMs: Long) = localNow + offsetMs

        fun of(name: String, live: Live?, updatedAt: Long?, apps: Map<String, String>, serverNow: Long, usageGranted: Boolean = true): ChildCard {
            val pkg = live?.pkg
            val label = pkg?.let { live.label ?: apps[PkgKey.encode(it)] ?: it }
            val sinceUpdate = updatedAt?.let { (serverNow - it).coerceAtLeast(0) }
            val liveness = Liveness.of(sinceUpdate, usageGranted)
            return ChildCard(
                name = name,
                appLabel = label,
                // 기록이 끊겼으면 그 앱을 아직 쓰는지 알 수 없으므로 경과 시간을 늘려 보여주지 않는다 (FR#18).
                elapsedMs = live?.since?.takeIf { pkg != null && liveness == Liveness.OK }?.let { (serverNow - it).coerceAtLeast(0) },
                sinceUpdateMs = sinceUpdate,
                title = live?.title,
                url = live?.url,
                screenOn = live?.screenOn ?: false,
                liveness = liveness
            )
        }
    }
}

/** 카드에 맞게 줄여 쓴다. 단위 문자열은 화면에서 리소스로 넘긴다 (NFR#9). */
fun formatDuration(ms: Long, sec: String = "초", min: String = "분", hour: String = "시간"): String {
    val s = ms / 1000
    return when {
        s < 60 -> "$s$sec"
        s < 3600 -> "${s / 60}$min"
        else -> "${s / 3600}$hour ${s % 3600 / 60}$min"
    }
}
