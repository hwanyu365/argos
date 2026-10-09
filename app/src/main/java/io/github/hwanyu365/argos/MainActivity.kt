package io.github.hwanyu365.argos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.hwanyu365.argos.child.MonitorService
import io.github.hwanyu365.argos.ui.ArgosApp
import io.github.hwanyu365.argos.ui.theme.ArgosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 화면 회전 같은 재생성 때는 이미 복구했으므로 처음 만들어질 때만 확인한다.
        if (savedInstanceState == null) MonitorService.resumeFromUi(this)
        setContent {
            ArgosTheme {
                Surface(Modifier.fillMaxSize()) {
                    Surface(Modifier.safeDrawingPadding()) { ArgosApp() }
                }
            }
        }
    }
}
