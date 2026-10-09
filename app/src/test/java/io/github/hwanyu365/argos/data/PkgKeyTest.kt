package io.github.hwanyu365.argos.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PkgKeyTest {
    @Test
    fun `TC#20 패키지명의 점은 쉼표로 바뀌고 왕복하면 원래 값이다`() {
        val pkg = "com.google.android.youtube"
        assertEquals("com,google,android,youtube", PkgKey.encode(pkg))
        assertEquals(pkg, PkgKey.decode(PkgKey.encode(pkg)))
    }

    @Test
    fun `TC#20 밑줄이 있는 패키지도 왕복이 유일하다`() {
        val pkg = "com.sec.android.app_store"
        assertEquals(pkg, PkgKey.decode(PkgKey.encode(pkg)))
    }
}
