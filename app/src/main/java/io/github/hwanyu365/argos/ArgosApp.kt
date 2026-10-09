package io.github.hwanyu365.argos

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ArgosApp : Application() {
    /** 화면이 닫혀도 끝까지 보내야 하는 쓰기(예: 초대 코드 삭제)용. 프로세스와 수명이 같다. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
