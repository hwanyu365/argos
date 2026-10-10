package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.Session
import io.github.hwanyu365.argos.data.PkgKey

/** FR#17 타임라인 한 줄. 상세는 제목을 우선한다 (영상·Shorts 제목이 주소보다 내용을 잘 보여줌). */
data class TimelineRow(val label: String, val detail: String?, val start: Long, val end: Long) {
    val durationMs get() = end - start

    companion object {
        fun of(sessions: List<Session>, labels: Map<String, String>): List<TimelineRow> = sessions.sortedBy { it.start }.map {
            TimelineRow(labels[PkgKey.encode(it.pkg)] ?: it.pkg, it.title ?: it.url, it.start, it.end)
        }
    }
}
