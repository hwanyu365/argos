package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.ui.Liveness.DELAYED
import io.github.hwanyu365.argos.ui.Liveness.LIMITED
import io.github.hwanyu365.argos.ui.Liveness.OK
import io.github.hwanyu365.argos.ui.Liveness.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LivenessTest {
    private val min = 60_000L

    @Test
    fun `TC#28 마지막 확인 2분 이내는 정상, 5분 이내는 지연, 그 뒤는 중단이다`() {
        assertEquals(OK, Liveness.of(sinceUpdateMs = 1 * min, usageGranted = true))
        assertEquals(OK, Liveness.of(sinceUpdateMs = 2 * min, usageGranted = true))
        assertEquals(DELAYED, Liveness.of(sinceUpdateMs = 3 * min, usageGranted = true))
        assertEquals(DELAYED, Liveness.of(sinceUpdateMs = 5 * min, usageGranted = true))
        assertEquals(STOPPED, Liveness.of(sinceUpdateMs = 6 * min, usageGranted = true))
    }

    @Test
    fun `TC#28 정상이지만 접근성이나 알림 접근이 꺼져 있으면 상세 제한이다`() {
        assertEquals(LIMITED, Liveness.of(sinceUpdateMs = min, usageGranted = true, detailsGranted = false))
        assertEquals(OK, Liveness.of(sinceUpdateMs = min, usageGranted = true, detailsGranted = true))
        assertEquals(DELAYED, Liveness.of(sinceUpdateMs = 3 * min, usageGranted = true, detailsGranted = false))
    }

    @Test
    fun `TC#28 상세 제한이어도 현재 앱과 경과 시간은 보여준다`() {
        val live = io.github.hwanyu365.argos.child.Live(pkg = "a.b", label = "앱", since = 0, screenOn = true)
        val card = ChildCard.of("첫째", live, updatedAt = 0, apps = emptyMap(), serverNow = 30_000, usageGranted = true, detailsGranted = false)
        assertEquals(LIMITED, card.liveness)
        assertEquals(30_000L, card.elapsedMs)
    }

    @Test
    fun `TC#28 사용 정보 접근이 꺼지면 최근에 확인됐어도 중단이다`() {
        assertEquals(STOPPED, Liveness.of(sinceUpdateMs = 10_000, usageGranted = false))
    }

    @Test
    fun `TC#28 기록이 한 번도 없으면 상태를 판정하지 않는다`() {
        assertNull(Liveness.of(sinceUpdateMs = null, usageGranted = true))
    }

    @Test
    fun `TC#28 정상이 아니면 카드의 경과 시간은 늘어나지 않고 마지막 앱으로만 보인다`() {
        val live = io.github.hwanyu365.argos.child.Live(pkg = "a.b", label = "앱", since = 0, title = "영상", url = "u", screenOn = true)
        val stale = ChildCard.of("첫째", live, updatedAt = 0, apps = emptyMap(), serverNow = 10 * min, usageGranted = true)
        assertEquals(STOPPED, stale.liveness)
        assertNull(stale.elapsedMs)
        assertEquals("앱", stale.appLabel)
        // 끊긴 뒤의 상세는 '지금 보는 것'처럼 보이면 안 된다.
        assertNull(stale.title)
        assertNull(stale.url)
    }
}
