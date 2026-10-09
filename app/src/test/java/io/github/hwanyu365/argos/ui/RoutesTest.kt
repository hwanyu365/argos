package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.data.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    private fun route(configured: Boolean = true, role: Role? = null, familyId: String? = null, monitoring: Boolean = false) = Route.start(configured, role, familyId, monitoring)

    @Test
    fun `TC#2 Firebase 설정이 없으면 다른 상태와 무관하게 미설정 화면이다`() {
        assertEquals(Route.FirebaseMissing, route(configured = false, role = Role.PARENT, familyId = "f"))
    }

    @Test
    fun `TC#1 가족이 없으면 시작 화면이다`() {
        assertEquals(Route.Welcome, route())
        assertEquals(Route.Welcome, route(role = Role.CHILD))
    }

    @Test
    fun `TC#1 저장된 역할과 가족으로 다음 실행 때 해당 홈에 바로 들어간다`() {
        assertEquals(Route.ParentHome, route(role = Role.PARENT, familyId = "f"))
        assertEquals(Route.ChildPermissions, route(role = Role.CHILD, familyId = "f", monitoring = false))
        assertEquals(Route.ChildStatus, route(role = Role.CHILD, familyId = "f", monitoring = true))
    }
}
