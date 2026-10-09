package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.data.PkgKey
import java.time.LocalDate

/** FR#16 기간 탭에 보여줄 값. 날짜는 자녀 기기 시간대 기준으로 올라온 키를 그대로 쓴다 (D#3). */
data class PeriodSummary(val apps: List<Pair<String, Long>>, val bars: List<Pair<LocalDate, Long>>, val totalSec: Long) {
    // 하루 총합이 빠진 날이 있어도 앱별 기록이 있으면 '기록 없음'이 아니다.
    val isEmpty get() = totalSec == 0L && apps.isEmpty()

    companion object {
        fun of(daily: Map<LocalDate, Map<String, Long>>, totals: Map<LocalDate, Long>, today: LocalDate, days: Int): PeriodSummary {
            val range = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
            val apps = range.flatMap { daily[it].orEmpty().entries }
                .groupBy({ PkgKey.decode(it.key) }, { it.value })
                .mapValues { it.value.sum() }
                .toList()
                .sortedByDescending { it.second }
            val bars = range.map { it to (totals[it] ?: 0L) }
            // 앱별 합계를 더하면 PiP 겹침이 두 번 들어가므로, 총합은 하루 총합(A#4)을 더한다.
            return PeriodSummary(apps, bars, bars.sumOf { it.second })
        }
    }
}
