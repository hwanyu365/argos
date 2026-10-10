package io.github.hwanyu365.argos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.child.DailyAggregator
import io.github.hwanyu365.argos.data.FamilyRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.catch

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** FR#17: 고른 날의 세션을 시작 순으로 보여준다. 날짜는 오늘부터 보관 기간 안에서 옮긴다. */
@Composable
internal fun TimelineTab(repo: FamilyRepository, fid: String, uid: String, apps: Map<String, String>) {
    val today = LocalDate.now()
    var date by remember { mutableStateOf(today) }
    // 구독 오류를 빈 기록으로 바꾸면 '사용하지 않음'으로 오해되므로 따로 표시한다.
    var failed by remember(fid, uid, date) { mutableStateOf(false) }
    val sessions by remember(fid, uid, date) { repo.timeline(fid, uid, date).catch { failed = true } }.collectAsState(null)
    val rows = sessions?.let { TimelineRow.of(it, apps) }
    val oldest = today.minusDays(DailyAggregator.RETENTION_DAYS - 1)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = date.minusDays(1) }, enabled = date > oldest) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.timeline_prev)) }
            Text(
                if (date == today) stringResource(R.string.timeline_today, date.monthValue, date.dayOfMonth) else stringResource(R.string.timeline_date, date.monthValue, date.dayOfMonth),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { date = date.plusDays(1) }, enabled = date < today) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.timeline_next)) }
        }
        when {
            failed -> Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            rows == null -> Text("…")
            rows.isEmpty() -> Text(stringResource(R.string.timeline_empty), style = MaterialTheme.typography.bodyLarge)
            else -> LazyColumn {
                items(rows, key = { "${it.start}_${it.label}" }) { row ->
                    TimelineItem(row)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(row: TimelineRow) {
    val zone = ZoneId.systemDefault()
    fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(CLOCK)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${clock(row.start)}\n${clock(row.end)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Text(formatDuration(row.durationMs, stringResource(R.string.unit_sec), stringResource(R.string.unit_min), stringResource(R.string.unit_hour)), style = MaterialTheme.typography.bodyMedium)
    }
}
