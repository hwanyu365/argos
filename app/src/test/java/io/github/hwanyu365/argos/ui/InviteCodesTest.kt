package io.github.hwanyu365.argos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {
    @Test
    fun `TC#47 새로 발급하면 이전 코드는 지울 목록으로 넘어간다`() {
        val codes = InviteCodes()
        assertTrue(codes.complete(codes.begin(), "AAAAAAAAAA"))
        val t = codes.begin()
        assertEquals(listOf("AAAAAAAAAA"), codes.takeObsolete())
        assertTrue(codes.complete(t, "BBBBBBBBBB"))
    }

    @Test
    fun `TC#47 발급이 끝나기 전에 다시 발급하면 먼저 끝난 이전 발급분은 바로 지운다`() {
        val codes = InviteCodes()
        val first = codes.begin()
        val second = codes.begin()
        assertFalse(codes.complete(first, "AAAAAAAAAA"))
        assertEquals(listOf("AAAAAAAAAA"), codes.takeObsolete())
        assertTrue(codes.complete(second, "BBBBBBBBBB"))
    }

    @Test
    fun `TC#47 닫으면 표시 중인 코드와 이후 끝나는 발급분을 모두 지운다`() {
        val codes = InviteCodes()
        codes.complete(codes.begin(), "AAAAAAAAAA")
        val pending = codes.begin()
        assertEquals(listOf("AAAAAAAAAA"), codes.close())
        assertFalse(codes.complete(pending, "BBBBBBBBBB"))
        assertEquals(listOf("BBBBBBBBBB"), codes.takeObsolete())
    }

    @Test
    fun `TC#47 QR 로 읽은 값도 입력과 같이 정규화하고 10자리로 자른다`() {
        assertEquals("ABCDE01234", InviteCodes.fromScan("abcde-o1234-extra"))
    }
}
