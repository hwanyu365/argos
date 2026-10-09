package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.data.Role

/** 화면 단위. 화면 수가 적어 Navigation 라이브러리 없이 상태로 전환한다. */
sealed interface Route {
    data object FirebaseMissing : Route
    data object Welcome : Route
    data object Join : Route
    data object ParentHome : Route
    data class ChildDetail(val uid: String) : Route
    data object ChildPermissions : Route
    data object ChildStatus : Route

    companion object {
        /** FR#1·FR#2: 저장된 상태로 첫 화면을 정한다. 역할은 가족에 참여할 때 정해지므로 가족이 없으면 시작 화면이다. */
        fun start(configured: Boolean, role: Role?, familyId: String?, monitoring: Boolean): Route = when {
            !configured -> FirebaseMissing
            familyId == null || role == null -> Welcome
            role == Role.PARENT -> ParentHome
            monitoring -> ChildStatus
            else -> ChildPermissions
        }
    }
}
