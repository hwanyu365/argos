package io.github.hwanyu365.argos.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hwanyu365.argos.FirebaseConfig
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.child.MonitorService
import io.github.hwanyu365.argos.data.FamilyRepository
import io.github.hwanyu365.argos.data.JoinException
import io.github.hwanyu365.argos.data.Prefs
import io.github.hwanyu365.argos.data.Role
import kotlinx.coroutines.launch

/** 화면 전환과 기기 상태(역할·가족)를 한 곳에서 관리한다. */
@Composable
fun ArgosApp() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val configured = remember { FirebaseConfig.fromBuildConfig().isComplete }
    var route by remember { mutableStateOf(Route.start(configured, prefs.role, prefs.familyId, prefs.monitoring)) }
    val repo = remember(configured) { if (configured) FamilyRepository() else null }
    val scope = rememberCoroutineScope()
    var resetFailed by remember { mutableStateOf(false) }

    // FR#1: 가족에서 먼저 탈퇴하고, 성공했을 때만 로컬을 지운다. 실패하면 이 기기는 그대로 남아 다시 시도할 수 있다.
    fun reset() {
        val fid = prefs.familyId
        scope.launch {
            runCatching { if (repo != null && fid != null) repo.leave(fid) }
                .onSuccess {
                    MonitorService.stop(context)
                    prefs.clear()
                    route = Route.Welcome
                }.onFailure {
                    Log.w(TAG, "leave failed", it)
                    resetFailed = true
                }
        }
    }

    if (resetFailed) {
        AlertDialog(
            onDismissRequest = { resetFailed = false },
            text = { Text(stringResource(R.string.error_generic)) },
            confirmButton = { TextButton(onClick = { resetFailed = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    when (val r = route) {
        Route.FirebaseMissing -> FirebaseMissing()
        Route.Welcome -> Welcome(repo!!, onCreated = { fid ->
            prefs.role = Role.PARENT
            prefs.familyId = fid
            route = Route.ParentHome
        }, onJoin = { route = Route.Join })
        Route.Join -> {
            BackHandler { route = Route.Welcome }
            JoinScreen(repo!!, onBack = { route = Route.Welcome }) { fid, role ->
                prefs.role = role
                prefs.familyId = fid
                route = if (role == Role.PARENT) Route.ParentHome else Route.ChildPermissions
            }
        }
        Route.ParentHome -> ParentHome(repo!!, prefs.familyId!!, onOpen = { route = Route.ChildDetail(it) }, onReset = ::reset)
        is Route.ChildDetail -> {
            BackHandler { route = Route.ParentHome }
            ChildDetail(repo!!, prefs.familyId!!, r.uid, onBack = { route = Route.ParentHome })
        }
        Route.ChildPermissions -> ChildPermissions(onStart = {
            MonitorService.start(context)
            route = Route.ChildStatus
        }, onReset = ::reset)
        Route.ChildStatus -> ChildStatus(onFix = { route = Route.ChildPermissions }, onReset = ::reset)
    }
}

/** FR#2: 설정이 없으면 Firebase 를 초기화하지 않고 안내만 한다. */
@Composable
private fun FirebaseMissing() = Centered {
    Text(stringResource(R.string.firebase_missing_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.firebase_missing_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Welcome(repo: FamilyRepository, onCreated: (String) -> Unit, onJoin: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.error_generic)

    Centered {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
        Button(onClick = { asking = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_family)) }
        TextButton(onClick = onJoin, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.join_with_code)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp)) }
    }

    if (asking) {
        NameDialog(onDismiss = { asking = false }) { name ->
            asking = false
            busy = true
            scope.launch {
                runCatching { repo.createFamily(name) }.onSuccess(onCreated).onFailure {
                    Log.w(TAG, "create family failed", it)
                    error = failed
                }
                busy = false
            }
        }
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.your_name)) },
        text = { NameField(name) { name = it } },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun NameField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = { onChange(it.take(40)) },
    label = { Text(stringResource(R.string.your_name)) },
    placeholder = { Text(stringResource(R.string.name_hint)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
)

@Composable
internal fun Centered(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) { content() }

@Composable
internal fun ConfirmDialog(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
)

internal const val TAG = "ArgosUi"

internal fun Context.joinError(e: Throwable): String = getString(
    when ((e as? JoinException)?.reason) {
        JoinException.Reason.NOT_FOUND -> R.string.join_error_not_found
        JoinException.Reason.EXPIRED -> R.string.join_error_expired
        null -> R.string.error_generic
    }
)
