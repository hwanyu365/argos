package io.github.hwanyu365.argos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseConfigTest {
    private val full = FirebaseConfig(apiKey = "k", appId = "a", projectId = "p", databaseUrl = "https://p.firebaseio.com")

    @Test
    fun `TC#2 설정값이 모두 있으면 완전한 설정이다`() {
        assertTrue(full.isComplete)
    }

    @Test
    fun `TC#2 설정값이 하나라도 비면 미설정이다`() {
        listOf(
            full.copy(apiKey = ""),
            full.copy(appId = " "),
            full.copy(projectId = ""),
            full.copy(databaseUrl = "")
        ).forEach { assertFalse(it.toString(), it.isComplete) }
    }
}
