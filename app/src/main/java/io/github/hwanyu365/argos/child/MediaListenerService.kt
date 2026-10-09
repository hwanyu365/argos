package io.github.hwanyu365.argos.child

import android.service.notification.NotificationListenerService

/**
 * X#1: 다른 앱의 미디어 세션(재생 중인 영상 제목)을 조회하려면 알림 접근 권한을 가진 리스너가 있어야 한다.
 * 알림 내용 자체는 읽지 않으므로 아무것도 처리하지 않는다.
 */
class MediaListenerService : NotificationListenerService()
