package io.github.hwanyu365.argos.child

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAggregatorTest {
    private val kst = ZoneId.of("Asia/Seoul")
    private fun t(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, kst).toInstant().toEpochMilli()
    private fun day(d: Int) = LocalDate.of(2026, 10, d)

    @Test
    fun `TC#18 자정을 넘는 세션은 자녀 기기 시간대의 자정으로 나눠 각 날짜에 더한다`() {
        val days = DailyAggregator.aggregate(listOf(Session("yt", t(9, 23, 50), t(10, 0, 10))), kst)
        assertEquals(600L, days.getValue(day(9)).apps["yt"])
        assertEquals(600L, days.getValue(day(10)).apps["yt"])
    }

    @Test
    fun `TC#18 같은 날 같은 앱은 합산한다`() {
        val days = DailyAggregator.aggregate(listOf(Session("a", t(10, 9, 0), t(10, 9, 10)), Session("a", t(10, 21, 0), t(10, 21, 5))), kst)
        assertEquals(900L, days.getValue(day(10)).apps["a"])
    }

    @Test
    fun `TC#19 같은 입력이면 다시 계산해도 같은 값이다 (덮어쓰기 업로드)`() {
        val sessions = listOf(Session("a", t(10, 9, 0), t(10, 9, 10)))
        assertEquals(DailyAggregator.aggregate(sessions, kst), DailyAggregator.aggregate(sessions, kst))
    }

    @Test
    fun `TC#51 PiP 처럼 겹친 구간은 앱별로 각각 세고 하루 총합에서는 한 번만 센다`() {
        val days = DailyAggregator.aggregate(listOf(Session("kakao", t(10, 10, 0), t(10, 10, 10)), Session("yt", t(10, 10, 2), t(10, 10, 20))), kst)
        val d = days.getValue(day(10))
        assertEquals(600L, d.apps["kakao"])
        assertEquals(1080L, d.apps["yt"])
        assertEquals(1200L, d.totalSec)
    }

    @Test
    fun `TC#55 YouTube 의 Shorts 세션 시간을 날짜별로 따로 센다`() {
        val yt = "com.google.android.youtube"
        val days = DailyAggregator.aggregate(
            listOf(
                Session(yt, t(10, 9, 0), t(10, 9, 10), title = "Shorts · 고양이"),
                Session(yt, t(10, 9, 10), t(10, 9, 30), title = "일반 영상"),
                Session(yt, t(10, 21, 0), t(10, 21, 5), title = "Shorts"),
                Session("com.android.chrome", t(10, 22, 0), t(10, 22, 5), title = "Shorts 이야기", url = "m.youtube.com/shorts/x"),
                // 미디어 세션이 준 일반 영상 제목이 우연히 'Shorts' 로 시작하는 경우는 Shorts 가 아니다.
                Session(yt, t(10, 23, 0), t(10, 23, 10), title = "Shorts 만드는 법")
            ),
            kst
        )
        val d = days.getValue(day(10))
        assertEquals(2_700L, d.apps[yt])
        assertEquals(900L, d.shortsSec)
    }

    @Test
    fun `TC#26 업로드는 마지막으로 올린 날부터 오늘까지 다시 올린다 (실패분 재전송)`() {
        assertEquals(listOf(day(8), day(9), day(10)), DailyAggregator.uploadDays(lastUploaded = day(8), earliest = day(1), today = day(10)))
    }

    @Test
    fun `TC#26 처음 올릴 때는 가장 오래된 기록부터, 보관 기간을 넘지 않게 올린다`() {
        assertEquals(listOf(day(9), day(10)), DailyAggregator.uploadDays(lastUploaded = null, earliest = day(9), today = day(10)))
        val today = LocalDate.of(2026, 12, 31)
        val days = DailyAggregator.uploadDays(lastUploaded = null, earliest = LocalDate.of(2026, 1, 1), today = today)
        assertEquals(today.minusDays(89), days.first())
        assertEquals(90, days.size)
    }

    @Test
    fun `TC#27 보관 기준일은 오늘로부터 90일 전이다`() {
        assertEquals(LocalDate.of(2026, 7, 12), DailyAggregator.retentionCutoff(day(10)))
    }
}
