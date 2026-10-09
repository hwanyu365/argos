package io.github.hwanyu365.argos.child

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPolicyTest {
    private val allRequired = Permissions(usage = true, postNotifications = true, batteryExempt = true, accessibility = false, notificationListener = false)

    @Test
    fun `TC#11 필수 권한이 모두 있으면 선택 권한 없이도 시작할 수 있다`() {
        assertTrue(allRequired.canStart)
        assertEquals(listOf(Permission.ACCESSIBILITY, Permission.NOTIFICATION_LISTENER), allRequired.missing)
    }

    @Test
    fun `TC#11 필수 권한이 하나라도 없으면 시작할 수 없다`() {
        listOf(
            allRequired.copy(usage = false),
            allRequired.copy(postNotifications = false),
            allRequired.copy(batteryExempt = false)
        ).forEach { assertFalse(it.toString(), it.canStart) }
    }

    @Test
    fun `TC#11 체크리스트는 필수 항목을 먼저 보여준다`() {
        val order = Permission.entries.map { it.required }
        assertEquals(order.sortedDescending(), order)
    }

    private val live = Live(pkg = "A", since = 0, screenOn = true)

    @Test
    fun `TC#25 앱이 바뀌면 즉시 올린다`() {
        assertTrue(LiveReporter.shouldUpload(next = live.copy(pkg = "B"), last = live, lastSentAt = 0, now = 1_000))
    }

    @Test
    fun `TC#25 상세나 화면 상태가 바뀌어도 즉시 올린다`() {
        assertTrue(LiveReporter.shouldUpload(next = live.copy(title = "영상"), last = live, lastSentAt = 0, now = 1_000))
        assertTrue(LiveReporter.shouldUpload(next = live.copy(screenOn = false), last = live, lastSentAt = 0, now = 1_000))
    }

    @Test
    fun `TC#25 변화가 없으면 60초마다 heartbeat 로만 올린다`() {
        assertFalse(LiveReporter.shouldUpload(next = live, last = live, lastSentAt = 0, now = 59_999))
        assertTrue(LiveReporter.shouldUpload(next = live, last = live, lastSentAt = 0, now = 60_000))
    }

    @Test
    fun `TC#25 아직 올린 적이 없으면 올린다`() {
        assertTrue(LiveReporter.shouldUpload(next = live, last = null, lastSentAt = 0, now = 0))
    }

    @Test
    fun `TC#33 화면이 꺼지면 폴링을 멈추고 heartbeat 만 유지한다`() {
        assertEquals(3_000L, MonitorSchedule.pollDelay(screenOn = true))
        assertNull(MonitorSchedule.pollDelay(screenOn = false))
        assertEquals(60_000L, MonitorSchedule.HEARTBEAT_MS)
    }
}
