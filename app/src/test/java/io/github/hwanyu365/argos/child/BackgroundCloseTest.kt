package io.github.hwanyu365.argos.child

import io.github.hwanyu365.argos.child.UsageEvent.Type.PAUSED
import io.github.hwanyu365.argos.child.UsageEvent.Type.RESUMED
import io.github.hwanyu365.argos.child.UsageEvent.Type.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundCloseTest {
    private fun ev(type: UsageEvent.Type, pkg: String?, ts: Long, cls: String? = null) = UsageEvent(type, pkg, ts, cls)

    @Test
    fun `TC#53 다른 앱이 뜨지 않은 채 현재 화면이 숨겨지면 멈춘 시각에 닫는다`() {
        val b = SessionBuilder(emptySet())
        // 실측: Family Link 잠금 화면이 꺼진 화면에서 떴다가 바로 숨겨진 뒤 3시간 동안 다른 이벤트가 없었다.
        val closed = b.feed(listOf(ev(RESUMED, "gms", 0, "Lock"), ev(PAUSED, "gms", 0, "Lock"), ev(STOPPED, "gms", 1_000, "Lock")))
        assertEquals(emptyList<Session>(), closed)
        assertNull(b.current)
        assertEquals(emptyList<Session>(), b.feed(listOf(ev(RESUMED, "kakao", 3 * 3_600_000L, "K"))).filter { it.pkg == "gms" })
    }

    @Test
    fun `TC#53 사용한 뒤 숨겨진 앱은 멈춘 시각까지 센다`() {
        val b = SessionBuilder(emptySet())
        val closed = b.feed(listOf(ev(RESUMED, "a", 0, "A"), ev(PAUSED, "a", 60_000, "A"), ev(STOPPED, "a", 61_000, "A")))
        assertEquals(listOf(Session("a", 0, 60_000)), closed)
    }

    @Test
    fun `TC#53 같은 앱 안의 화면 전환에서 이전 화면이 숨겨지는 것은 종료가 아니다`() {
        val b = SessionBuilder(emptySet())
        val closed = b.feed(listOf(ev(RESUMED, "a", 0, "A1"), ev(PAUSED, "a", 5_000, "A1"), ev(RESUMED, "a", 5_100, "A2"), ev(STOPPED, "a", 5_600, "A1")))
        assertEquals(emptyList<Session>(), closed)
        assertEquals(OpenSession("a", 0), b.current)
    }
}
