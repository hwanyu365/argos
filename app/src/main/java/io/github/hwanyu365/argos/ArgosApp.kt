package io.github.hwanyu365.argos

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class ArgosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // google-services 플러그인 없이 빌드 시 주입된 값으로 초기화한다 (NFR#5). 값이 없으면 FR#2 안내 화면만 쓴다.
        val cfg = FirebaseConfig.fromBuildConfig()
        if (cfg.isComplete) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApiKey(cfg.apiKey)
                    .setApplicationId(cfg.appId)
                    .setProjectId(cfg.projectId)
                    .setDatabaseUrl(cfg.databaseUrl)
                    .build()
            )
        }
    }
}
