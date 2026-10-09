package io.github.hwanyu365.argos.data

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {
    @Test
    fun `TC#5 코드는 Crockford Base32 10자리다`() {
        val format = Regex("^[0-9A-HJKMNP-TV-Z]{10}$")
        repeat(1_000) { assertTrue(format.matches(PairingCode.generate())) }
    }

    @Test
    fun `TC#5 코드는 매번 달라진다`() {
        assertEquals(1_000, List(1_000) { PairingCode.generate() }.toSet().size)
    }

    @Test
    fun `TC#5 같은 난수열이면 같은 코드가 나온다`() {
        assertEquals(PairingCode.generate(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(1) }), PairingCode.generate(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(1) }))
    }

    @Test
    fun `TC#5 만료 시각은 발급 시각 + 10분이다`() {
        assertEquals(1_000L + 600_000L, PairingCode.expiresAt(nowMs = 1_000L))
    }

    @Test
    fun `TC#5 입력 코드는 공백 제거·대문자화하고 혼동 문자를 정규화한다`() {
        assertEquals("ABCD0121EF", PairingCode.normalize(" abcd-o12l ef"))
    }
}
