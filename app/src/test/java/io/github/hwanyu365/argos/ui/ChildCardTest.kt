package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.Live
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChildCardTest {
    private val live = Live(pkg = "com.google.android.youtube", label = "YouTube", since = 1_000_000, screenOn = true)

    @Test
    fun `TC#30 경과 시간은 서버 기준 현재 시각에서 세션 시작을 뺀 값이다`() {
        val card = ChildCard.of("첫째", live, updatedAt = 1_100_000, apps = emptyMap(), serverNow = 1_125_000)
        assertEquals("YouTube", card.appLabel)
        assertEquals(125_000L, card.elapsedMs)
        assertEquals(25_000L, card.sinceUpdateMs)
    }

    @Test
    fun `TC#29 기기 시계가 서버보다 30초 빠르면 오프셋으로 보정한 값을 쓴다`() {
        val serverNow = ChildCard.serverNow(localNow = 1_155_000, offsetMs = -30_000)
        assertEquals(125_000L, ChildCard.of("첫째", live, 1_100_000, emptyMap(), serverNow).elapsedMs)
    }

    @Test
    fun `TC#30 live 에 이름이 없으면 공유된 앱 이름, 그것도 없으면 패키지명을 쓴다`() {
        val noLabel = live.copy(label = null)
        assertEquals("유튜브", ChildCard.of("첫째", noLabel, 0, mapOf("com,google,android,youtube" to "유튜브"), 0).appLabel)
        assertEquals("com.google.android.youtube", ChildCard.of("첫째", noLabel, 0, emptyMap(), 0).appLabel)
    }

    @Test
    fun `TC#30 화면이 꺼졌거나 앱이 없으면 현재 앱과 경과 시간이 없다`() {
        val off = ChildCard.of("첫째", Live(screenOn = false), 0, emptyMap(), 10_000)
        assertNull(off.appLabel)
        assertNull(off.elapsedMs)
    }

    @Test
    fun `TC#30 시계 차이로 음수가 되면 0 으로 본다`() {
        assertEquals(0L, ChildCard.of("첫째", live, updatedAt = 999_000, apps = emptyMap(), serverNow = 999_000).elapsedMs)
    }

    @Test
    fun `TC#30 경과 시간은 초·분·시간 단위로 줄여 쓴다`() {
        assertEquals("45초", formatDuration(45_000))
        assertEquals("3분", formatDuration(3 * 60_000 + 20_000))
        assertEquals("1시간 5분", formatDuration(65 * 60_000))
    }
}
