package io.github.hwanyu365.argos.child

import io.github.hwanyu365.argos.child.UsageEvent.Type.DESTROYED
import io.github.hwanyu365.argos.child.UsageEvent.Type.PAUSED
import io.github.hwanyu365.argos.child.UsageEvent.Type.RESUMED
import io.github.hwanyu365.argos.child.UsageEvent.Type.SCREEN_OFF
import io.github.hwanyu365.argos.child.UsageEvent.Type.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PipSessionTest {
    private val launcher = "launcher"
    private fun builder() = SessionBuilder(excluded = setOf(launcher))
    private fun ev(type: UsageEvent.Type, pkg: String?, ts: Long, cls: String? = null) = UsageEvent(type, pkg, ts, cls)

    @Test
    fun `TC#50 멈춘 뒤 다른 앱이 떠도 숨겨지지 않으면 PiP 로 이어서 센다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000, "Main")))
        assertEquals(emptyList<Session>(), closed)
        assertEquals(OpenSession("kakao", 10_000), b.current)
        assertEquals(OpenSession("yt", 0), b.pip?.open)
        assertEquals(listOf(Session("yt", 0, 70_000)), b.feed(listOf(ev(STOPPED, "yt", 70_000, "Watch"))))
        assertNull(b.pip)
    }

    @Test
    fun `TC#50 3초 안에 숨겨지면 일반 전환이라 다른 앱이 뜬 시각에 닫는다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "a", 0, "A"), ev(PAUSED, "a", 9_800, "A"), ev(RESUMED, "b", 10_000, "B"), ev(STOPPED, "a", 10_600, "A")))
        assertEquals(listOf(Session("a", 0, 10_000)), closed)
        assertNull(b.pip)
    }

    @Test
    fun `TC#50 숨김 없이 PiP 화면이 종료돼도 닫는다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000)))
        assertEquals(listOf(Session("yt", 0, 50_000)), b.feed(listOf(ev(DESTROYED, "yt", 50_000, "Watch"))))
        assertNull(b.pip)
    }

    @Test
    fun `TC#50 PiP 앱의 다른 화면이 숨겨지는 것은 PiP 종료가 아니다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000)))
        assertEquals(emptyList<Session>(), b.feed(listOf(ev(STOPPED, "yt", 20_000, "Splash"))))
        assertEquals("yt", b.pip?.open?.pkg)
    }

    @Test
    fun `TC#50 PiP 앱을 다시 크게 열면 같은 세션으로 이어진다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000)))
        val closed = b.feed(listOf(ev(PAUSED, "kakao", 59_000), ev(RESUMED, "yt", 60_000, "Watch")))
        assertEquals(listOf(Session("kakao", 10_000, 60_000)), closed)
        assertEquals(OpenSession("yt", 0), b.current)
        assertNull(b.pip)
    }

    @Test
    fun `TC#50 화면이 꺼지면 현재 앱과 PiP 앱을 함께 닫는다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000)))
        val closed = b.feed(listOf(ev(SCREEN_OFF, null, 30_000)))
        assertEquals(setOf(Session("kakao", 10_000, 30_000), Session("yt", 0, 30_000)), closed.toSet())
        assertNull(b.pip)
        assertNull(b.current)
    }

    @Test
    fun `TC#50 홈 화면 위에 PiP 를 띄워도 PiP 로 센다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, launcher, 10_000)))
        assertNull(b.current)
        assertEquals("yt", b.pip?.open?.pkg)
    }

    @Test
    fun `TC#50 PiP 가 있는 동안의 일반 앱 전환은 PiP 를 바꾸지 않는다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000, "K")))
        val closed = b.feed(listOf(ev(PAUSED, "kakao", 19_900, "K"), ev(RESUMED, "web", 20_000, "W"), ev(STOPPED, "kakao", 20_500, "K")))
        assertEquals(listOf(Session("kakao", 10_000, 20_000)), closed)
        assertEquals("yt", b.pip?.open?.pkg)
    }

    @Test
    fun `TC#50 실시간에는 3초가 지난 PiP 만 보인다 (일반 전환의 순간 표시 방지)`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0, "Watch"), ev(PAUSED, "yt", 9_000, "Watch"), ev(RESUMED, "kakao", 10_000)))
        assertNull(b.visiblePip(now = 12_000))
        assertEquals(OpenSession("yt", 0), b.visiblePip(now = 13_000))
    }

    @Test
    fun `TC#50 재시작 시 PiP 상태를 복원해 이어서 닫는다`() {
        val restored = SessionBuilder(emptySet(), lastEventTs = 10_000, current = OpenSession("kakao", 10_000), pip = Pip(OpenSession("yt", 0), "Watch", handoff = 10_000))
        assertEquals(listOf(Session("yt", 0, 40_000)), restored.feed(listOf(ev(STOPPED, "yt", 40_000, "Watch"))))
    }
}
