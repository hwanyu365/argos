package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.Live
import io.github.hwanyu365.argos.data.PkgKey

/** FR#15 자녀 카드에 보여줄 값. 시각 계산은 서버 기준이다 (D#2). */
data class ChildCard(
    val name: String,
    val appLabel: String?,
    val elapsedMs: Long?,
    val sinceUpdateMs: Long?,
    val title: String?,
    val url: String?,
    val screenOn: Boolean
) {
    companion object {
        fun serverNow(localNow: Long, offsetMs: Long) = localNow + offsetMs

        fun of(name: String, live: Live?, updatedAt: Long?, apps: Map<String, String>, serverNow: Long): ChildCard {
            val pkg = live?.pkg
            val label = pkg?.let { live.label ?: apps[PkgKey.encode(it)] ?: it }
            return ChildCard(
                name = name,
                appLabel = label,
                elapsedMs = live?.since?.takeIf { pkg != null }?.let { (serverNow - it).coerceAtLeast(0) },
                sinceUpdateMs = updatedAt?.let { (serverNow - it).coerceAtLeast(0) },
                title = live?.title,
                url = live?.url,
                screenOn = live?.screenOn ?: false
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
