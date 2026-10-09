package io.github.hwanyu365.argos.child

import io.github.hwanyu365.argos.child.UsageEvent.Type.PAUSED
import io.github.hwanyu365.argos.child.UsageEvent.Type.RESUMED
import io.github.hwanyu365.argos.child.UsageEvent.Type.SCREEN_OFF
import io.github.hwanyu365.argos.child.UsageEvent.Type.SCREEN_ON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionBuilderTest {
    private val launcher = "com.sec.android.app.launcher"
    private fun builder() = SessionBuilder(excluded = setOf(launcher, "com.android.systemui"))
    private fun ev(type: UsageEvent.Type, pkg: String?, ts: Long) = UsageEvent(type, pkg, ts)

    @Test
    fun `TC#12 다른 앱 RESUMED 는 이전 세션을 닫고 새 세션을 연다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "A", 0), ev(RESUMED, "B", 10_000)))
        assertEquals(listOf(Session("A", 0, 10_000)), closed)
        assertEquals(OpenSession("B", 10_000), b.current)
    }

    @Test
    fun `TC#12 같은 앱 RESUMED 연속은 세션을 유지한다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "A", 0), ev(PAUSED, "A", 5_000), ev(RESUMED, "A", 5_100), ev(RESUMED, "B", 20_000)))
        assertEquals(listOf(Session("A", 0, 20_000)), closed)
    }

    @Test
    fun `TC#13 PAUSED 뒤 화면이 꺼지면 PAUSED 시각에 닫는다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "A", 0), ev(PAUSED, "A", 8_000), ev(SCREEN_OFF, null, 9_000)))
        assertEquals(listOf(Session("A", 0, 8_000)), closed)
        assertNull(b.current)
    }

    @Test
    fun `TC#13 PAUSED 없이 화면이 꺼지면 꺼진 시각에 닫는다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "A", 0), ev(SCREEN_OFF, null, 9_000)))
        assertEquals(listOf(Session("A", 0, 9_000)), closed)
    }

    @Test
    fun `TC#13 화면이 다시 켜져도 RESUMED 전까지는 세션이 없다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "A", 0), ev(SCREEN_OFF, null, 9_000), ev(SCREEN_ON, null, 20_000)))
        assertNull(b.current)
    }

    @Test
    fun `TC#14 제외 패키지는 세션을 만들지 않지만 이전 세션은 닫는다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "A", 0), ev(RESUMED, launcher, 7_000)))
        assertEquals(listOf(Session("A", 0, 7_000)), closed)
        assertNull(b.current)
    }

    @Test
    fun `TC#15 1초 미만 세션은 버린다`() {
        val b = builder()
        val closed = b.feed(listOf(ev(RESUMED, "A", 0), ev(RESUMED, "B", 500), ev(RESUMED, "C", 5_000)))
        assertEquals(listOf(Session("B", 500, 5_000)), closed)
    }

    @Test
    fun `TC#16 상세가 없던 세션에 처음 붙은 상세는 나누지 않고 붙인다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0)))
        assertEquals(emptyList<Session>(), b.onDetail(Detail(title = "영상1"), 2_000))
        assertEquals(OpenSession("yt", 0, Detail(title = "영상1")), b.current)
    }

    @Test
    fun `TC#16 진행 중 세션의 상세가 바뀌면 그 시각에 나눈다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0)))
        b.onDetail(Detail(title = "영상1"), 2_000)
        assertEquals(listOf(Session("yt", 0, 10_000, title = "영상1")), b.onDetail(Detail(title = "영상2"), 10_000))
        assertEquals(OpenSession("yt", 10_000, Detail(title = "영상2")), b.current)
    }

    @Test
    fun `TC#16 상세가 사라져도 나눈다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0)))
        b.onDetail(Detail(title = "영상1"), 0)
        assertEquals(listOf(Session("yt", 0, 9_000, title = "영상1")), b.onDetail(Detail(), 9_000))
    }

    @Test
    fun `TC#16 진행 중 세션이 없으면 상세를 무시한다`() {
        assertEquals(emptyList<Session>(), builder().onDetail(Detail(title = "x"), 0))
    }

    @Test
    fun `TC#16 같은 상세가 다시 들어오면 나누지 않는다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "yt", 0)))
        b.onDetail(Detail(title = "영상1"), 0)
        assertEquals(emptyList<Session>(), b.onDetail(Detail(title = "영상1"), 5_000))
    }

    @Test
    fun `TC#17 이어서 받은 이벤트는 마지막 처리 시각 이후만 반영한다`() {
        val b = builder()
        b.feed(listOf(ev(RESUMED, "A", 0), ev(RESUMED, "B", 10_000)))
        assertEquals(10_000, b.lastEventTs)
        // 폴링 구간이 겹쳐 같은 이벤트가 다시 와도 중복 세션을 만들지 않는다.
        val closed = b.feed(listOf(ev(RESUMED, "B", 10_000), ev(RESUMED, "C", 30_000)))
        assertEquals(listOf(Session("B", 10_000, 30_000)), closed)
    }

    @Test
    fun `TC#17 소급 범위보다 오래 끊겼으면 끝을 알 수 없는 진행 중 세션은 버린다`() {
        val restored = SessionBuilder(excluded = emptySet(), lastEventTs = 10_000, current = OpenSession("B", 10_000))
        restored.skipGap(from = 50_000)
        assertNull(restored.current)
        assertEquals(50_000, restored.lastEventTs)
        assertEquals(emptyList<Session>(), restored.feed(listOf(ev(RESUMED, "C", 60_000))))
    }

    @Test
    fun `TC#17 소급 범위 안이면 진행 중 세션을 유지한다`() {
        val restored = SessionBuilder(excluded = emptySet(), lastEventTs = 10_000, current = OpenSession("B", 10_000))
        restored.skipGap(from = 5_000)
        assertEquals(OpenSession("B", 10_000), restored.current)
    }

    @Test
    fun `TC#17 재시작 시 진행 중 세션과 마지막 시각을 복원해 이어서 만든다`() {
        val restored = SessionBuilder(excluded = emptySet(), lastEventTs = 10_000, current = OpenSession("B", 10_000))
        val closed = restored.feed(listOf(ev(RESUMED, "C", 30_000)))
        assertEquals(listOf(Session("B", 10_000, 30_000)), closed)
    }
}
