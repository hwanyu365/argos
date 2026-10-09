package io.github.hwanyu365.argos.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.hwanyu365.argos.R
import io.github.hwanyu365.argos.data.FamilyRepository
import io.github.hwanyu365.argos.data.Role
import kotlinx.coroutines.launch

/** FR#5: 초대 코드(QR 또는 입력)로 가족에 참여한다. 역할은 코드가 정한다. */
@Composable
internal fun JoinScreen(repo: FamilyRepository, onBack: () -> Unit, onJoined: (String, Role) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Google 코드 스캐너는 카메라 권한 없이 Play 서비스 UI 로 스캔한다.
    fun scan() {
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            .startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { code = it } }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.join_with_code), style = MaterialTheme.typography.headlineSmall)
        NameField(name) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(14) },
                label = { Text(stringResource(R.string.invite_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = ::scan, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.scan_qr)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    runCatching { repo.join(code, name) }
                        .onSuccess { (fid, role) -> onJoined(fid, role) }
                        .onFailure {
                            Log.w(TAG, "join failed", it)
                            error = context.joinError(it)
                        }
                    busy = false
                }
            },
            enabled = !busy && name.isNotBlank() && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.join)) }
    }
}
