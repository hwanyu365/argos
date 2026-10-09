package io.github.hwanyu365.argos.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.child.DeviceState
import io.github.hwanyu365.argos.child.Permission
import io.github.hwanyu365.argos.child.Permissions

/** 설정 화면에서 돌아올 때마다 권한을 다시 읽는다 (FR#7). */
@Composable
private fun rememberPermissions(): Permissions {
    val context = LocalContext.current
    val device = remember { DeviceState(context) }
    var perms by remember { mutableStateOf(device.permissions()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = device.permissions() }
    return perms
}

/** FR#7: 필수 권한을 순서대로 안내한다. 선택 권한(접근성·알림 접근)은 해당 기능이 생기는 UC3 에서 추가한다. */
@Composable
internal fun ChildPermissions(onStart: () -> Unit, onReset: () -> Unit) {
    val perms = rememberPermissions()
    var resetting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.permissions_body), style = MaterialTheme.typography.bodyMedium)
        Permission.entries.filter { it.required }.forEach { PermissionRow(it, perms.granted(it)) }
        // FR#7 선택 권한: 없어도 시작할 수 있지만 영상 제목·주소가 모이지 않는다.
        Text(stringResource(R.string.optional_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Permission.entries.filterNot { it.required }.forEach { PermissionRow(it, perms.granted(it)) }
        Button(onClick = onStart, enabled = perms.canStart, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.start_monitoring)) }
        TextButton(onClick = { resetting = true }) { Text(stringResource(R.string.reset)) }
    }
    if (resetting) ResetDialog(onDismiss = { resetting = false }, onReset)
}

@SuppressLint("BatteryLife")
@Composable
private fun PermissionRow(p: Permission, granted: Boolean) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val (title, desc) = when (p) {
        Permission.USAGE -> R.string.perm_usage to R.string.perm_usage_desc
        Permission.POST_NOTIFICATIONS -> R.string.perm_post to R.string.perm_post_desc
        Permission.BATTERY -> R.string.perm_battery to R.string.perm_battery_desc
        Permission.ACCESSIBILITY -> R.string.perm_a11y to R.string.perm_a11y_desc
        Permission.NOTIFICATION_LISTENER -> R.string.perm_notif to R.string.perm_notif_desc
    }

    fun request() {
        val pkg = Uri.parse("package:${context.packageName}")

        // 일부 기기는 패키지 지정 설정 화면이 없어 전체 목록 화면으로 대신 연다.
        fun open(action: String, data: Uri?) = runCatching { context.startActivity(Intent(action, data)) }.recoverCatching { context.startActivity(Intent(action)) }
        when (p) {
            Permission.USAGE -> open(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg)
            Permission.POST_NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            Permission.BATTERY -> open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Permission.ACCESSIBILITY -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS, null)
            Permission.NOTIFICATION_LISTENER -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, null)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(desc), style = MaterialTheme.typography.bodySmall)
            }
            if (granted) {
                Text(stringResource(R.string.granted), color = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = ::request) { Text(stringResource(R.string.allow)) }
            }
        }
    }
}

/** NFR#6: 자녀 기기에도 감시 중임을 그대로 보여준다. 필수 권한이 꺼지면 다시 허용하도록 안내한다. */
@Composable
internal fun ChildStatus(onFix: () -> Unit, onReset: () -> Unit) {
    val perms = rememberPermissions()
    var resetting by remember { mutableStateOf(false) }

    Centered {
        val ok = perms.canStart
        Card(
            colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.status_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(if (ok) R.string.status_body else R.string.perms_missing), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (!ok) Button(onClick = onFix, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.fix_permissions)) }
        TextButton(onClick = { resetting = true }, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.reset)) }
    }
    if (resetting) ResetDialog(onDismiss = { resetting = false }, onReset)
}

@Composable
private fun ResetDialog(onDismiss: () -> Unit, onReset: () -> Unit) = ConfirmDialog(stringResource(R.string.reset_confirm), stringResource(R.string.reset), onDismiss) {
    onDismiss()
    onReset()
}
