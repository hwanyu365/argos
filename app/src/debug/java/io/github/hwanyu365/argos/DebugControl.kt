package io.github.hwanyu365.argos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hwanyu365.argos.child.MonitorService
import io.github.hwanyu365.argos.data.Prefs

/**
 * 페어링 화면 없이 실기기에서 수집 루프를 검증하기 위한 debug 빌드 전용 진입점. release 에는 포함되지 않는다.
 * exported 이고 권한 검사가 없어 같은 기기의 다른 앱도 familyId 를 바꿀 수 있다 → 실사용 자녀 기기에는 debug APK 를 설치하지 않는다.
 * adb shell am broadcast -n io.github.hwanyu365.argos.debug/io.github.hwanyu365.argos.DebugControl --es cmd start [--es familyId FID]
 */
class DebugControl : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("familyId")?.let { Prefs(context).familyId = it }
        when (intent.getStringExtra("cmd")) {
            "start" -> MonitorService.start(context)
            "stop" -> MonitorService.stop(context)
        }
    }
}
