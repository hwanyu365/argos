package io.github.hwanyu365.argos.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveTest {
    private val denied = Exception("Firebase Database error: Permission denied")

    @Test
    fun `TC#10 탈퇴가 거부됐을 때 자기 레코드가 없거나 읽기도 거부되면 이미 제거된 기기다`() {
        assertTrue(FamilyRepository.alreadyRemoved(Result.success(false)))
        assertTrue(FamilyRepository.alreadyRemoved(Result.failure(denied)))
    }

    @Test
    fun `TC#10 아직 멤버이거나 읽기가 네트워크 등으로 실패하면 탈퇴 실패다`() {
        assertFalse(FamilyRepository.alreadyRemoved(Result.success(true)))
        assertFalse(FamilyRepository.alreadyRemoved(Result.failure(Exception("timeout"))))
    }
}
