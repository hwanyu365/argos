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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.data.FamilyRepository
import io.github.hwanyu365.argos.data.PkgKey
import java.time.LocalDate
import kotlinx.coroutines.flow.catch

private val PERIODS = listOf(1 to R.string.period_today, 7 to R.string.period_7, 30 to R.string.period_30)

/** FR#16: 기간별 앱 사용 합계와 일별 총 사용 시간. 기본은 30일이다. */
@Composable
internal fun PeriodTab(repo: FamilyRepository, fid: String, uid: String, apps: Map<String, String>) {
    val usage by remember(fid, uid) { repo.usage(fid, uid).catch { emit(FamilyRepository.Usage(emptyMap(), emptyMap())) } }.collectAsState(null)
    var days by remember { mutableIntStateOf(30) }
    val summary = usage?.let { PeriodSummary.of(it.daily, it.totals, LocalDate.now(), days) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PERIODS.forEachIndexed { i, (d, label) ->
                SegmentedButton(selected = days == d, onClick = { days = d }, shape = SegmentedButtonDefaults.itemShape(i, PERIODS.size), icon = {}) {
                    Text(stringResource(label), maxLines = 1, fontWeight = if (days == d) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        when {
            summary == null -> Text("…")
            summary.totalSec == 0L -> Text(stringResource(R.string.period_empty), style = MaterialTheme.typography.bodyLarge)
            else -> {
                // 기간 총합이 이 탭의 핵심 숫자다.
                Column {
                    Text(stringResource(R.string.period_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(hours(summary.totalSec), style = MaterialTheme.typography.headlineLarge)
                }
                if (days > 1) DailyBars(summary.bars)
                AppList(summary.apps, apps)
            }
        }
    }
}

@Composable
private fun hours(sec: Long) = formatDuration(sec * 1000, stringResource(R.string.unit_sec), stringResource(R.string.unit_min), stringResource(R.string.unit_hour))

/** 단일 계열 막대: 한 가지 색, 범례 없음, 끝은 둥글게, 막대 사이 간격. 누르면 그날 값을 보여준다. */
@Composable
private fun DailyBars(bars: List<Pair<LocalDate, Long>>) {
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val max = bars.maxOf { it.second }.coerceAtLeast(1)
    val sel = selected?.let { bars.getOrNull(it) }
    val desc = stringResource(R.string.chart_desc, bars.size, hours(max))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            sel?.let { "${it.first.monthValue}/${it.first.dayOfMonth}  ${hours(it.second)}" } ?: stringResource(R.string.chart_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = desc }
                .pointerInput(bars) { detectTapGestures { o -> selected = (o.x / (size.width / bars.size)).toInt().coerceIn(0, bars.size - 1) } }
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
private fun AppList(rows: List<Pair<String, Long>>, labels: Map<String, String>) {
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
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
                    Box(Modifier.fillMaxWidth(sec.toFloat() / max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(bar))
                }
            }
        }
    }
}
