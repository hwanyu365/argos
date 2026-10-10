package io.github.hwanyu365.argos.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodSummaryTest {
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private val daily = mapOf(
        d(8) to mapOf("com,kakao,talk" to 600L),
        d(9) to mapOf("com,google,android,youtube" to 3_600L, "com,kakao,talk" to 300L),
        d(10) to mapOf("com,google,android,youtube" to 1_200L)
    )
    private val totals = mapOf(d(8) to 600L, d(9) to 3_700L, d(10) to 1_200L)

    @Test
    fun `TC#57 날짜를 고르면 막대는 기간 전체로 두고 총합·앱별·Shorts 는 그날 값만 센다`() {
        val s = PeriodSummary.of(daily, totals, today = d(10), days = 7, shorts = mapOf(d(9) to 100L, d(10) to 50L), selected = d(9))
        assertEquals(7, s.bars.size)
        assertEquals(listOf("com.google.android.youtube" to 3_600L, "com.kakao.talk" to 300L), s.apps)
        assertEquals(3_700L, s.totalSec)
        assertEquals(100L, s.shortsSec)
        assertEquals(d(9), s.selected)
    }

    @Test
    fun `TC#57 같은 날을 다시 고르면 선택을 풀고, 다른 날이면 그날로 바꾼다`() {
        assertEquals(d(9), PeriodSummary.toggle(null, d(9)))
        assertEquals(null, PeriodSummary.toggle(d(9), d(9)))
        assertEquals(d(8), PeriodSummary.toggle(d(9), d(8)))
    }

    @Test
    fun `TC#57 기간 밖 날짜(화면을 켠 채 자정을 넘김)는 고르지 않은 것으로 본다`() {
        assertEquals(null, PeriodSummary.of(daily, totals, today = d(10), days = 7, selected = d(1)).selected)
        assertEquals(PeriodSummary.of(daily, totals, today = d(10), days = 7), PeriodSummary.of(daily, totals, today = d(10), days = 7, selected = d(1)))
    }

    @Test
    fun `TC#31 기간의 앱별 합계를 많은 순으로 보여주고 앱 키를 패키지명으로 되돌린다`() {
        val s = PeriodSummary.of(daily, totals, today = d(10), days = 7)
        assertEquals(listOf("com.google.android.youtube" to 4_800L, "com.kakao.talk" to 900L), s.apps)
    }

    @Test
    fun `TC#31 일별 막대는 기간의 모든 날을 순서대로, 기록 없는 날은 0 으로 채운다`() {
        val s = PeriodSummary.of(daily, totals, today = d(10), days = 7)
        assertEquals(7, s.bars.size)
        assertEquals(d(4), s.bars.first().first)
        assertEquals(d(10) to 1_200L, s.bars.last())
        assertEquals(0L, s.bars.first().second)
    }

    @Test
    fun `TC#31 기간 총합은 하루 총합(겹침 한 번만)을 더한 값이다`() {
        assertEquals(5_500L, PeriodSummary.of(daily, totals, today = d(10), days = 7).totalSec)
    }

    @Test
    fun `TC#31 하루 총합이 없는 날도 앱별 기록이 있으면 비어 있지 않다`() {
        val s = PeriodSummary.of(daily, totals = emptyMap(), today = d(10), days = 1)
        assertEquals(listOf("com.google.android.youtube" to 1_200L), s.apps)
        assertEquals(false, s.isEmpty)
        assertEquals(true, PeriodSummary.of(emptyMap(), emptyMap(), d(10), 7).isEmpty)
    }

    @Test
    fun `TC#55 기간의 Shorts 시간을 더한다`() {
        val s = PeriodSummary.of(daily, totals, today = d(10), days = 7, shorts = mapOf(d(9) to 1_000L, d(10) to 200L, d(1) to 999L))
        assertEquals(1_200L, s.shortsSec)
    }

    @Test
    fun `TC#31 오늘만 고르면 오늘 기록만 센다`() {
        val s = PeriodSummary.of(daily, totals, today = d(10), days = 1)
        assertEquals(listOf("com.google.android.youtube" to 1_200L), s.apps)
        assertEquals(1_200L, s.totalSec)
    }
}
