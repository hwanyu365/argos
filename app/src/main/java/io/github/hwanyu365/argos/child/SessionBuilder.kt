package io.github.hwanyu365.argos.child

data class UsageEvent(val type: Type, val pkg: String?, val ts: Long) {
    enum class Type { RESUMED, PAUSED, SCREEN_ON, SCREEN_OFF }
}

data class Detail(val title: String? = null, val url: String? = null) {
    val isEmpty get() = title == null && url == null
}

data class OpenSession(val pkg: String, val start: Long, val detail: Detail = Detail())

data class Session(val pkg: String, val start: Long, val end: Long, val title: String? = null, val url: String? = null)

/**
 * UsageEvents 를 세션으로 바꾼다 (spec §6.1 S#1~S#8).
 * OS 가 이벤트를 보관하므로 lastEventTs 와 current 만 저장해 두면 프로세스가 죽어도 이어서 만들 수 있다.
 */
class SessionBuilder(
    private val excluded: Set<String>,
    lastEventTs: Long = 0,
    current: OpenSession? = null
) {
    var lastEventTs = lastEventTs
        private set
    var current = current
        private set
    private var pausedAt: Long? = null

    fun feed(events: List<UsageEvent>): List<Session> {
        val closed = mutableListOf<Session>()
        // 폴링 구간이 겹쳐 이미 처리한 이벤트가 다시 와도 무시한다 (S#8).
        for (e in events.sortedBy { it.ts }.filter { it.ts >= lastEventTs }) {
            when (e.type) {
                UsageEvent.Type.RESUMED -> onResumed(e.pkg ?: continue, e.ts, closed)
                UsageEvent.Type.PAUSED -> if (e.pkg == current?.pkg) pausedAt = e.ts
                UsageEvent.Type.SCREEN_OFF -> close(pausedAt ?: e.ts, closed)
                UsageEvent.Type.SCREEN_ON -> Unit
            }
            lastEventTs = e.ts
        }
        return closed
    }

    /**
     * S#8: [from] 이전 이벤트는 읽을 수 없다(OS 보관 범위 밖). 그 사이에 진행 중 세션이 언제 끝났는지 알 수 없으므로
     * 이어 붙이면 사용 시간이 부풀려진다 → 버리고 [from] 부터 다시 시작한다.
     */
    fun skipGap(from: Long) {
        if (lastEventTs >= from) return
        current = null
        pausedAt = null
        lastEventTs = from
    }

    /** S#7: 상세가 다른 값으로 바뀌면 나눈다. 상세가 없던 세션에 처음 붙는 값은 늦게 도착한 메타데이터라 그대로 붙인다. */
    fun onDetail(detail: Detail, ts: Long): List<Session> {
        val open = current ?: return emptyList()
        if (open.detail == detail) return emptyList()
        if (open.detail.isEmpty) {
            current = open.copy(detail = detail)
            return emptyList()
        }
        val closed = mutableListOf<Session>()
        close(ts, closed)
        current = OpenSession(open.pkg, ts, detail)
        return closed
    }

    private fun onResumed(pkg: String, ts: Long, closed: MutableList<Session>) {
        if (pkg == current?.pkg) {
            pausedAt = null
            return
        }
        close(ts, closed)
        if (pkg !in excluded) current = OpenSession(pkg, ts)
    }

    private fun close(end: Long, closed: MutableList<Session>) {
        val open = current ?: return
        if (end - open.start >= MIN_SESSION_MS) closed += Session(open.pkg, open.start, end, open.detail.title, open.detail.url)
        current = null
        pausedAt = null
    }

    private companion object {
        // S#6: 앱 전환 중 순간 노출은 사용으로 보지 않는다.
        const val MIN_SESSION_MS = 1_000L
    }
}
