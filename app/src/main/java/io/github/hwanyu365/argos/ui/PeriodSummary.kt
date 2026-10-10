package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.data.PkgKey
import java.time.LocalDate

/** FR#16 기간 탭에 보여줄 값. 날짜는 자녀 기기 시간대 기준으로 올라온 키를 그대로 쓴다 (D#3). */
data class PeriodSummary(val apps: List<Pair<String, Long>>, val bars: List<Pair<LocalDate, Long>>, val totalSec: Long, val shortsSec: Long = 0, val selected: LocalDate? = null) {
    // 하루 총합이 빠진 날이 있어도 앱별 기록이 있으면 '기록 없음'이 아니다.
    val isEmpty get() = totalSec == 0L && apps.isEmpty()

    companion object {
        /** 같은 막대를 다시 누르면 기간 전체로 돌아간다. */
        fun toggle(current: LocalDate?, tapped: LocalDate): LocalDate? = tapped.takeIf { it != current }

        /** [selected] 가 기간 안의 날짜면 막대는 기간 전체로 두고 나머지 값은 그날만 센다 (GH-43). */
        fun of(daily: Map<LocalDate, Map<String, Long>>, totals: Map<LocalDate, Long>, today: LocalDate, days: Int, shorts: Map<LocalDate, Long> = emptyMap(), selected: LocalDate? = null): PeriodSummary {
            val range = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
            val bars = range.map { it to (totals[it] ?: 0L) }
            // 화면을 켠 채 자정을 넘기면 고른 날이 기간 밖이 된다 → 선택을 풀어 값과 라벨을 맞춘다.
            val day = selected?.takeIf { it in range }
            val focus = day?.let { listOf(it) } ?: range
            val apps = focus.flatMap { daily[it].orEmpty().entries }
                .groupBy({ PkgKey.decode(it.key) }, { it.value })
                .mapValues { it.value.sum() }
                .toList()
                .sortedByDescending { it.second }
            // 앱별 합계를 더하면 PiP 겹침이 두 번 들어가므로, 총합은 하루 총합(A#4)을 더한다.
            return PeriodSummary(apps, bars, focus.sumOf { totals[it] ?: 0L }, focus.sumOf { shorts[it] ?: 0L }, day)
        }
    }
}
