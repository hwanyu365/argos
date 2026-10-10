package io.github.hwanyu365.argos.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.hwanyu365.argos.ArgosApp
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.data.FamilyRepository
import io.github.hwanyu365.argos.data.FamilySnapshot
import io.github.hwanyu365.argos.data.Member
import io.github.hwanyu365.argos.data.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val Empty = FamilySnapshot(emptyList(), emptyMap(), emptyMap())

/** 1초마다 갱신되는 서버 기준 현재 시각 (D#2). 경과 시간 표시가 흐르게 한다. */
@Composable
private fun rememberServerNow(repo: FamilyRepository): Long {
    val offset by remember { repo.serverOffset().catch { emit(0) } }.collectAsState(0L)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return ChildCard.serverNow(now, offset)
}

@Composable
private fun rememberFamily(repo: FamilyRepository, fid: String): FamilySnapshot {
    val flow: Flow<FamilySnapshot> = remember(fid) { repo.family(fid).catch { emit(Empty) } }
    return flow.collectAsState(Empty).value
}

@Composable
private fun durationText(ms: Long) = formatDuration(ms, stringResource(R.string.unit_sec), stringResource(R.string.unit_min), stringResource(R.string.unit_hour))

/** FR#15: 자녀별 현재 앱과 경과 시간을 실시간으로 보여준다. */
@Composable
internal fun ParentHome(repo: FamilyRepository, fid: String, onOpen: (String) -> Unit, onReset: () -> Unit) {
    val family = rememberFamily(repo, fid)
    val now = rememberServerNow(repo)
    var inviting by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var removeFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val children = family.members.filter { it.role == Role.CHILD }

    Scaffold(floatingActionButton = { ExtendedFloatingActionButton(onClick = { inviting = true }) { Text(stringResource(R.string.invite)) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.parent_home_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { resetting = true }) { Text(stringResource(R.string.reset)) }
            }
            if (removeFailed) Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            if (children.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_children), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(children, key = { it.uid }) { m ->
                        val c = family.live[m.uid]
                        ChildCardView(ChildCard.of(m.name, c?.live, c?.updatedAt, family.apps, now, c?.usageGranted ?: true, c?.detailsGranted ?: true), onRemove = {
                            removeFailed = false
                            removing = m
                        }) { onOpen(m.uid) }
                    }
                }
            }
        }
    }

    if (inviting) InviteDialog(repo, fid) { inviting = false }
    removing?.let { m ->
        ConfirmDialog(stringResource(R.string.remove_confirm, m.name), stringResource(R.string.remove), onDismiss = { removing = null }) {
            removing = null
            removeFailed = false
            scope.launch {
                // FR#6: 실패하면 목록에 남은 채로 알린다.
                runCatching { repo.removeMember(fid, m.uid) }.onFailure {
                    Log.w(TAG, "remove member failed", it)
                    removeFailed = true
                }
            }
        }
    }
    if (resetting) {
        ConfirmDialog(stringResource(R.string.reset_confirm), stringResource(R.string.reset), onDismiss = { resetting = false }) {
            resetting = false
            onReset()
        }
    }
}

@Composable
private fun ChildCardView(card: ChildCard, onRemove: () -> Unit, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                card.liveness?.let { LivenessBadge(it) }
                // GH-43: 기기를 추가하는 화면에서 제거도 한다.
                Box {
                    IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_device), color = MaterialTheme.colorScheme.error) }, onClick = {
                            menu = false
                            onRemove()
                        })
                    }
                }
            }
            LiveLines(card)
        }
    }
}

@Composable
private fun LiveLines(card: ChildCard) {
    val live = card.liveness?.current == true
    when {
        card.sinceUpdateMs == null -> Text(stringResource(R.string.never_updated), style = MaterialTheme.typography.bodyMedium)
        // FR#18: 기록이 끊겼으면 '지금' 상태가 아니라 마지막으로 확인된 상태임을 밝힌다.
        !live -> Text(
            stringResource(R.string.last_seen, if (!card.screenOn) stringResource(R.string.screen_off) else card.appLabel ?: stringResource(R.string.no_app)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        !card.screenOn -> Text(stringResource(R.string.screen_off), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        card.appLabel == null -> Text(stringResource(R.string.no_app), style = MaterialTheme.typography.bodyLarge)
        else -> {
            Text(card.appLabel, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            card.elapsedMs?.let { Text(stringResource(R.string.using_for, durationText(it)), style = MaterialTheme.typography.bodyMedium) }
            (card.title ?: card.url)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
    // S#9: PiP 로 함께 보이는 앱. 현재 앱이 없을 때(홈 화면 위 PiP)도 보여준다.
    if (live) {
        card.pipLabel?.let { label ->
            Text(stringResource(R.string.pip_line, label, durationText(card.pipElapsedMs ?: 0)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
    card.sinceUpdateMs?.let { Text(stringResource(R.string.updated_ago, durationText(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** FR#18 감시 상태 배지. 색에만 의존하지 않도록 글자로도 상태를 쓴다 (NFR#9). */
@Composable
private fun LivenessBadge(state: Liveness) {
    val (label, bg, fg) = when (state) {
        Liveness.OK -> Triple(R.string.liveness_ok, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Liveness.LIMITED -> Triple(R.string.liveness_limited, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Liveness.DELAYED -> Triple(R.string.liveness_delayed, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        Liveness.STOPPED -> Triple(R.string.liveness_stopped, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** FR#4: 역할을 고르면 코드를 발급하고, 닫거나 다시 발급하면 이전 코드를 지운다. */
@Composable
private fun InviteDialog(repo: FamilyRepository, fid: String, onDismiss: () -> Unit) {
    // 발급·삭제는 앱 수준 scope 에서 보낸다. 다이얼로그가 닫혀 화면 scope 가 취소돼도 늦게 끝난 발급분까지 지운다.
    val appScope = (LocalContext.current.applicationContext as ArgosApp).scope
    val codes = remember { InviteCodes() }
    val now = rememberServerNow(repo)
    var role by remember { mutableStateOf(Role.CHILD) }
    var shown by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var error by remember { mutableStateOf(false) }
    val remaining = shown?.let { (it.second - now).coerceAtLeast(0) } ?: 0

    fun delete(list: List<String>) = list.forEach { c -> appScope.launch { runCatching { repo.deleteInvite(c) }.onFailure { Log.w(TAG, "invite delete failed", it) } } }

    fun issue() {
        val token = codes.begin()
        delete(codes.takeObsolete())
        shown = null
        error = false
        appScope.launch {
            runCatching { repo.createInvite(fid, role) }.onSuccess { (code, expiresAt) ->
                if (codes.complete(token, code)) shown = code to expiresAt
                delete(codes.takeObsolete())
            }.onFailure {
                Log.w(TAG, "invite failed", it)
                error = true
            }
        }
    }

    LaunchedEffect(role) { issue() }
    DisposableEffect(Unit) { onDispose { delete(codes.close()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = ::issue) { Text(stringResource(R.string.reissue)) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 다이얼로그 폭이 좁아 체크 아이콘까지 그리면 글자가 줄바꿈되며 버튼이 타원형으로 늘어난다 → 아이콘 없이 한 줄로.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Role.CHILD to R.string.invite_child, Role.PARENT to R.string.invite_parent).forEachIndexed { i, (r, label) ->
                        SegmentedButton(
                            selected = role == r,
                            onClick = { role = r },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {}
                        ) {
                            // 체크 아이콘이 없으므로 색에만 기대지 않게 선택된 쪽을 굵게도 표시한다 (NFR#9).
                            Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (role == r) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                val c = shown?.first
                when {
                    error -> Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    c == null -> Text("…")
                    else -> {
                        val qr = remember(c) { qrBitmap(c) }
                        Image(qr.asImageBitmap(), contentDescription = c, modifier = Modifier.size(200.dp))
                        Text(
                            c.chunked(5).joinToString("-"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.semantics { contentDescription = c.toList().joinToString(" ") }
                        )
                        Text(
                            if (remaining > 0) stringResource(R.string.expires_in, durationText(remaining)) else stringResource(R.string.expired),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(stringResource(R.string.invite_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    )
}

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val px = IntArray(size * size) { if (m[it % size, it / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** 자녀 한 명의 상세: [실시간 | 7일 | 30일] 탭을 좌우로 넘긴다 (GH-43). */
@Composable
internal fun ChildDetail(repo: FamilyRepository, fid: String, uid: String, onBack: () -> Unit) {
    val family = rememberFamily(repo, fid)
    val now = rememberServerNow(repo)
    val scope = rememberCoroutineScope()
    val member = family.members.firstOrNull { it.uid == uid }
    val c = family.live[uid]
    // 세 탭이 같은 기록을 쓰므로 구독은 한 번만 한다.
    // 구독 오류를 빈 기록으로 바꾸면 '사용하지 않음'으로 오해되므로 따로 표시한다.
    var failed by remember(fid, uid) { mutableStateOf(false) }
    val usage by remember(fid, uid) { repo.usage(fid, uid).catch { failed = true } }.collectAsState(null)
    val pager = rememberPagerState { DETAIL_TABS.size }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
            Text(member?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        // Secondary 표시 줄은 탭 폭 전체를 차지한다.
        SecondaryTabRow(selectedTabIndex = pager.currentPage) {
            DETAIL_TABS.forEachIndexed { i, (_, label) ->
                Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(stringResource(label)) })
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
            val days = DETAIL_TABS[page].first
            val live: (@Composable () -> Unit)? = if (days > 1) {
                null
            } else {
                {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val card = ChildCard.of(member?.name.orEmpty(), c?.live, c?.updatedAt, family.apps, now, c?.usageGranted ?: true, c?.detailsGranted ?: true)
                            LiveLines(card)
                            card.url?.takeIf { card.title != null }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            PeriodTab(usage, failed, days, family.apps, live)
        }
    }
}

private val DETAIL_TABS = listOf(1 to R.string.tab_live, 7 to R.string.period_7, 30 to R.string.period_30)
