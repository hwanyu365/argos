package io.github.hwanyu365.argos.child

import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePruneTest {
    private val nodes = listOf("daily", "dailyTotal", "dailyShorts", "timeline")

    private class Fake(val failFetch: Set<String> = emptySet(), val failRemove: Set<String> = emptySet(), val old: Map<String, List<String>> = emptyMap()) {
        val removed = mutableListOf<Map<String, Any?>>()

        fun fetch(node: String, onKeys: (List<String>) -> Unit) {
            if (node !in failFetch) onKeys(old[node].orEmpty())
        }

        fun remove(update: Map<String, Any?>, onDone: () -> Unit) {
            removed += update
            if (update.keys.none { it.substringBefore('/') in failRemove }) onDone()
        }
    }

    private fun run(fake: Fake): Int {
        var done = 0
        RemotePrune.run(nodes, fake::fetch, fake::remove) { done++ }
        return done
    }

    @Test
    fun `TC#61 조회가 하나라도 실패하면 기준일을 기록하지 않는다`() {
        assertEquals(0, run(Fake(failFetch = setOf("timeline"))))
    }

    @Test
    fun `TC#61 삭제가 하나라도 실패하면 기준일을 기록하지 않는다`() {
        assertEquals(0, run(Fake(failRemove = setOf("daily"), old = mapOf("daily" to listOf("2026-01-01")))))
    }

    @Test
    fun `TC#61 지울 키가 없는 노드를 포함해 네 노드가 모두 성공하면 한 번 기록한다`() {
        val fake = Fake(old = mapOf("daily" to listOf("2026-01-01", "2026-01-02")))
        assertEquals(1, run(fake))
        assertEquals(listOf(mapOf<String, Any?>("daily/2026-01-01" to null, "daily/2026-01-02" to null)), fake.removed)
    }
}
