package io.github.hwanyu365.argos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hwanyu365.argos.ui.theme.ArgosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val configured = FirebaseConfig.fromBuildConfig().isComplete
        setContent {
            ArgosTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        if (configured) Text(stringResource(R.string.ready), style = MaterialTheme.typography.headlineSmall) else FirebaseMissing()
                    }
                }
            }
        }
    }
}

// FR#2: 설정이 없으면 Firebase 를 초기화하지 않고 안내만 한다.
@Composable
private fun FirebaseMissing() {
    Text(stringResource(R.string.firebase_missing_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.firebase_missing_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
}
