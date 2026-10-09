package io.github.hwanyu365.argos.child

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** spec §6.2. 날짜는 자녀 기기의 시간대 기준이다 (D#3). */
object DailyAggregator {
    /** 하루치 앱별 사용 초와, 겹치는 구간을 한 번만 센 총 사용 초. */
    data class Day(val apps: Map<String, Long>, val totalSec: Long, val shortsSec: Long = 0)

    // FR#14: 조회 기본값 30일보다 넉넉하되 무료 저장 한도 안에 머무는 보관 기간.
    const val RETENTION_DAYS = 90L

    fun aggregate(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Day> {
        // A#1: 자정 경계로 잘라 날짜별 구간을 모은다.
        val pieces = sessions.flatMap { s -> split(s, zone).map { it to s } }.groupBy { it.first.first }
        return pieces.mapValues { (_, list) ->
            val apps = list.groupBy({ it.first.second }, { it.first.third.last - it.first.third.first }).mapValues { it.value.sum() / 1000 }
            // A#5: YouTube 앱에서 Shorts 로 본 시간 (X#1b 제목 표시로 구분). 브라우저의 Shorts 는 주소로만 보이므로 세지 않는다.
            val shorts = list.filter { (_, s) -> s.pkg == DetailExtractor.YOUTUBE && DetailExtractor.isShortsTitle(s.title) }.sumOf { it.first.third.last - it.first.third.first } / 1000
            Day(apps, union(list.map { it.first.third }) / 1000, shorts)
        }
    }

    /** A#3: 올린 날은 다시 올려도 같은 값이므로, 실패했을 수 있는 마지막 날부터 오늘까지 다시 올린다. */
    fun uploadDays(lastUploaded: LocalDate?, earliest: LocalDate?, today: LocalDate): List<LocalDate> {
        val from = maxOf(lastUploaded ?: earliest ?: today, today.minusDays(RETENTION_DAYS - 1))
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    }

    fun retentionCutoff(today: LocalDate): LocalDate = today.minusDays(RETENTION_DAYS)

    private fun split(s: Session, zone: ZoneId): List<Triple<LocalDate, String, LongRange>> {
        val out = mutableListOf<Triple<LocalDate, String, LongRange>>()
        var start = s.start
        while (start < s.end) {
            val date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(s.end, nextMidnight)
            out += Triple(date, s.pkg, start..end)
            start = end
        }
        return out
    }

    // PiP 와 현재 앱이 겹친 시간은 하루 총합에서 한 번만 센다 (S#9 결정).
    private fun union(ranges: List<LongRange>): Long {
        var total = 0L
        var curStart = Long.MIN_VALUE
        var curEnd = Long.MIN_VALUE
        for (r in ranges.sortedBy { it.first }) {
            if (r.first > curEnd) {
                if (curEnd > curStart) total += curEnd - curStart
                curStart = r.first
                curEnd = r.last
            } else {
                curEnd = maxOf(curEnd, r.last)
            }
        }
        if (curEnd > curStart) total += curEnd - curStart
        return total
    }
}
