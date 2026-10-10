package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    @Test
    fun `TC#32 선택한 날짜의 세션을 시작 시각 오름차순으로 보여주고 앱 이름을 붙인다`() {
        val rows = TimelineRow.of(
            listOf(Session("com.kakao.talk", 2_000, 62_000), Session("com.google.android.youtube", 1_000, 1_800, title = "Shorts · 고양이")),
            labels = mapOf("com,google,android,youtube" to "YouTube")
        )
        assertEquals(listOf("YouTube", "com.kakao.talk"), rows.map { it.label })
        assertEquals("Shorts · 고양이", rows.first().detail)
        assertEquals(60_000L, rows.last().durationMs)
    }

    @Test
    fun `TC#32 상세는 제목을 우선하고 없으면 주소를 쓴다`() {
        val rows = TimelineRow.of(listOf(Session("c", 0, 1_000, url = "a.com"), Session("d", 2_000, 3_000, title = "t", url = "b.com")), emptyMap())
        assertEquals(listOf("a.com", "t"), rows.map { it.detail })
        assertEquals(listOf("https://a.com", "https://b.com"), rows.map { it.link })
    }

    @Test
    fun `TC#32 이름이 같은 다른 앱이 같은 시각에 시작해도 행 키가 겹치지 않는다`() {
        val rows = TimelineRow.of(listOf(Session("a.x", 0, 1_000), Session("b.x", 0, 1_000)), mapOf("a,x" to "같음", "b,x" to "같음"))
        assertEquals(2, rows.map { it.key }.toSet().size)
    }
}
