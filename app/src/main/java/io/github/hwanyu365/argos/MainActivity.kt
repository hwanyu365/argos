package io.github.hwanyu365.argos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.hwanyu365.argos.ui.ArgosApp
import io.github.hwanyu365.argos.ui.theme.ArgosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArgosTheme {
                Surface(Modifier.fillMaxSize()) {
                    Surface(Modifier.safeDrawingPadding()) { ArgosApp() }
                }
            }
        }
    }
}
