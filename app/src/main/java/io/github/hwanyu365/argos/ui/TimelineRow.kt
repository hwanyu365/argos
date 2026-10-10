package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.Session
import io.github.hwanyu365.argos.data.PkgKey

/** FR#17 타임라인 한 줄. 상세는 제목을 우선한다 (영상·Shorts 제목이 주소보다 내용을 잘 보여줌). */
data class TimelineRow(val pkg: String, val label: String, val detail: String?, val start: Long, val end: Long, val link: String? = null) {
    // 원격 항목 키(D#6)와 같은 조합이라 하루 안에서 겹치지 않는다. 앱 이름은 자녀가 바꿀 수 있어 키로 쓰지 않는다.
    val key get() = "${start}_$pkg"

    val durationMs get() = end - start

    companion object {
        fun of(sessions: List<Session>, labels: Map<String, String>): List<TimelineRow> = sessions.sortedBy { it.start }.map {
            TimelineRow(it.pkg, labels[PkgKey.encode(it.pkg)] ?: it.pkg, it.title ?: it.url, it.start, it.end, DetailLink.of(it.pkg, it.title, it.url))
        }
    }
}
