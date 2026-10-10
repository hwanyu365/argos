package io.github.hwanyu365.argos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.child.DetailExtractor
import io.github.hwanyu365.argos.data.FamilyRepository
import io.github.hwanyu365.argos.data.PkgKey
import java.time.LocalDate

/** FR#16: 기간별 앱 사용 합계와 일별 총 사용 시간. [days] 가 1 이면 막대 자리에 [live] 를 보여주는 실시간 탭이다 (GH-43). */
@Composable
internal fun PeriodTab(usage: FamilyRepository.Usage?, failed: Boolean, days: Int, apps: Map<String, String>, live: (@Composable () -> Unit)? = null) {
    var picked by remember(days) { mutableStateOf<LocalDate?>(null) }
    val summary = usage?.let { PeriodSummary.of(it.daily, it.totals, LocalDate.now(), days, it.shorts, picked) }
    val selected = summary?.selected

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (summary != null && !failed) {
            // 기간(또는 고른 날) 총합이 이 탭의 핵심 숫자다.
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = selected?.let { stringResource(R.string.period_day_total, it.monthValue, it.dayOfMonth) } ?: stringResource(if (days == 1) R.string.today_total else R.string.period_total)
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (selected != null) TextButton(onClick = { picked = null }) { Text(stringResource(R.string.period_all)) }
                }
                Text(hours(summary.totalSec), style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (live != null) {
            live()
        } else if (summary != null && !failed && (selected != null || !summary.isEmpty)) {
            // 고른 날이 비어 있어도 막대를 남겨야 다른 날을 고르거나 되돌릴 수 있다.
            DailyBars(summary.bars, selected) { picked = PeriodSummary.toggle(summary.selected, it) }
        }
        when {
            failed -> Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            summary == null -> Text("…")
            summary.isEmpty -> Text(stringResource(R.string.period_empty), style = MaterialTheme.typography.bodyLarge)
            else -> AppList(summary.apps, apps, summary.shortsSec)
        }
    }
}

@Composable
private fun hours(sec: Long) = formatDuration(sec * 1000, stringResource(R.string.unit_sec), stringResource(R.string.unit_min), stringResource(R.string.unit_hour))

/** 단일 계열 막대: 한 가지 색, 범례 없음, 끝은 둥글게, 막대 사이 간격. 누르면 그날 값을 보여준다. */
@Composable
private fun DailyBars(bars: List<Pair<LocalDate, Long>>, day: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val selected = bars.indexOfFirst { it.first == day }.takeIf { it >= 0 }
    // 제스처 감지는 bars 가 같으면 재시작하지 않으므로, 처음 람다가 아닌 최신 선택 상태로 토글하게 한다.
    val select by rememberUpdatedState(onSelect)
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val max = bars.maxOf { it.second }.coerceAtLeast(1)
    val desc = stringResource(R.string.chart_desc, bars.size, hours(max))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (selected == null) R.string.chart_hint else R.string.chart_hint_selected),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = desc }
                .pointerInput(bars) { detectTapGestures { o -> select(bars[(o.x / (size.width / bars.size)).toInt().coerceIn(0, bars.size - 1)].first) } }
        ) {
            val slot = size.width / bars.size
            val gap = 2.dp.toPx()
            bars.forEachIndexed { i, (_, sec) ->
                val h = size.height * sec / max
                if (h > 0f) {
                    roundedTopBar(
                        Offset(i * slot + gap / 2, size.height - h),
                        Size(slot - gap, h),
                        4.dp.toPx(),
                        if (selected == null || selected == i) color else color.copy(alpha = 0.4f)
                    )
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(bars.first().first, bars.last().first).forEach {
                Text("${it.monthValue}/${it.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// 막대 끝(위)만 둥글게 하고 바닥은 기준선에 붙인다.
private fun DrawScope.roundedTopBar(topLeft: Offset, size: Size, radius: Float, color: androidx.compose.ui.graphics.Color) {
    val r = radius.coerceAtMost(size.width / 2).coerceAtMost(size.height)
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + size.width,
                bottom = topLeft.y + size.height,
                topLeftCornerRadius = CornerRadius(r),
                topRightCornerRadius = CornerRadius(r)
            )
        )
    }
    drawPath(path, color)
}

/** 앱별 합계. 이 목록이 차트의 표 역할도 한다 (값을 글자로 모두 보여준다). */
@Composable
private fun AppList(rows: List<Pair<String, Long>>, labels: Map<String, String>, shortsSec: Long) {
    val max = rows.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(rows, key = { it.first }) { (pkg, sec) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(labels[PkgKey.encode(pkg)] ?: pkg, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(hours(sec), style = MaterialTheme.typography.bodyLarge)
                }
                // GH-39: YouTube 총 시간 안에서 Shorts 로 본 시간.
                if (pkg == DetailExtractor.YOUTUBE && shortsSec > 0) {
                    Text(stringResource(R.string.shorts_within, hours(shortsSec)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
                    Box(Modifier.fillMaxWidth(sec.toFloat() / max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(bar))
                }
            }
        }
    }
}
