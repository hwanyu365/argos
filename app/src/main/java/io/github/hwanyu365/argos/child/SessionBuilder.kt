package io.github.hwanyu365.argos.child

data class UsageEvent(val type: Type, val pkg: String?, val ts: Long, val cls: String? = null) {
    enum class Type { RESUMED, PAUSED, STOPPED, DESTROYED, SCREEN_ON, SCREEN_OFF }
}

data class Detail(val title: String? = null, val url: String? = null) {
    val isEmpty get() = title == null && url == null
}

data class OpenSession(val pkg: String, val start: Long, val detail: Detail = Detail())

data class Session(val pkg: String, val start: Long, val end: Long, val title: String? = null, val url: String? = null)

/** 멈췄지만 숨겨지지 않은 앱(PiP). [cls] 는 멈춘 화면, [handoff] 는 다른 앱이 앞으로 온 시각이다. */
data class Pip(val open: OpenSession, val cls: String?, val handoff: Long)

/**
 * UsageEvents 를 세션으로 바꾼다 (spec §6.1 S#1~S#8).
 * OS 가 이벤트를 보관하므로 lastEventTs 와 current 만 저장해 두면 프로세스가 죽어도 이어서 만들 수 있다.
 */
class SessionBuilder(
    private val excluded: Set<String>,
    lastEventTs: Long = 0,
    current: OpenSession? = null,
    pip: Pip? = null
) {
    var lastEventTs = lastEventTs
        private set
    var current = current
        private set
    var pip = pip
        private set

    // 현재 앱이 멈춘 시각과 화면. 다른 앱이 뜰 때 PiP 후보인지 판단하는 데 쓴다.
    private var paused: Pair<Long, String?>? = null

    fun feed(events: List<UsageEvent>): List<Session> {
        val closed = mutableListOf<Session>()
        // 폴링 구간이 겹쳐 이미 처리한 이벤트가 다시 와도 무시한다 (S#8).
        for (e in events.sortedBy { it.ts }.filter { it.ts >= lastEventTs }) {
            when (e.type) {
                UsageEvent.Type.RESUMED -> onResumed(e.pkg ?: continue, e.ts, closed)
                UsageEvent.Type.PAUSED -> if (e.pkg == current?.pkg) paused = e.ts to e.cls
                // 숨김 없이 종료되는 경우도 있어 종료 이벤트도 PiP 종료로 본다.
                UsageEvent.Type.STOPPED, UsageEvent.Type.DESTROYED -> onStopped(e.pkg, e.cls, e.ts, closed)
                UsageEvent.Type.SCREEN_OFF -> {
                    close(paused?.first ?: e.ts, closed)
                    closePip(e.ts, closed)
                }
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
        pip = null
        paused = null
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

    /** 실시간 표시용. 일반 전환에서도 숨김까지 1초 남짓 걸리므로 그보다 오래 남은 경우만 PiP 로 보여준다. */
    fun visiblePip(now: Long): OpenSession? = pip?.takeIf { now - it.handoff >= PIP_MIN_MS }?.open

    private fun onResumed(pkg: String, ts: Long, closed: MutableList<Session>) {
        val p = pip
        // PiP 로 보던 앱을 다시 크게 열면 같은 세션을 이어 간다.
        if (p != null && pkg == p.open.pkg) {
            close(ts, closed)
            current = p.open
            pip = null
            return
        }
        if (pkg == current?.pkg) {
            paused = null
            return
        }
        val open = current
        val pz = paused
        // S#9: 멈춘 채 다른 앱이 떴다. 숨겨지는지(STOPPED) 볼 때까지 PiP 후보로 남긴다. PiP 는 한 번에 하나뿐이다.
        if (open != null && pz != null && p == null) {
            pip = Pip(open, pz.second, ts)
            current = null
            paused = null
        } else {
            close(ts, closed)
        }
        if (pkg !in excluded) current = OpenSession(pkg, ts)
    }

    private fun onStopped(pkg: String?, cls: String?, ts: Long, closed: MutableList<Session>) {
        val p = pip ?: return
        // PiP 앱의 다른 화면이 숨겨지는 것은 PiP 종료가 아니다.
        if (pkg != p.open.pkg || (p.cls != null && cls != null && cls != p.cls)) return
        closePip(ts, closed)
    }

    private fun closePip(ts: Long, closed: MutableList<Session>) {
        val p = pip ?: return
        // 앞 앱이 뜬 뒤 곧바로 숨겨졌다면 PiP 가 아니라 일반 전환이었다 → 앞 앱이 뜬 시각에 닫는다.
        val end = if (ts - p.handoff < PIP_MIN_MS) p.handoff else ts
        if (end - p.open.start >= MIN_SESSION_MS) closed += Session(p.open.pkg, p.open.start, end, p.open.detail.title, p.open.detail.url)
        pip = null
    }

    private fun close(end: Long, closed: MutableList<Session>) {
        val open = current ?: return
        if (end - open.start >= MIN_SESSION_MS) closed += Session(open.pkg, open.start, end, open.detail.title, open.detail.url)
        current = null
        paused = null
    }

    private companion object {
        // S#6: 앱 전환 중 순간 노출은 사용으로 보지 않는다.
        const val MIN_SESSION_MS = 1_000L

        // S#9: 일반 전환에서 이전 앱이 숨겨지기까지의 여유. 실측 약 0.5~1초.
        const val PIP_MIN_MS = 3_000L
    }
}
