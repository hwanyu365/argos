package io.github.hwanyu365.argos.child

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePruneTest {
    private val nodes = listOf("daily", "dailyTotal", "dailyShorts", "timeline")
    private val day = LocalDate.of(2026, 8, 17)

    private class Fake(val answer: Boolean = true, val old: Map<String, List<String>> = emptyMap()) {
        val fetched = mutableListOf<String>()
        val removed = mutableListOf<Map<String, Any?>>()

        fun fetch(node: String, onKeys: (List<String>) -> Unit) {
            fetched += node
            if (answer) onKeys(old[node].orEmpty())
        }

        fun remove(update: Map<String, Any?>) {
            removed += update
        }
    }

    private fun RemotePrune.run(cutoff: LocalDate, fake: Fake) = start(cutoff, fake::fetch, fake::remove)

    @Test
    fun `TC#61 앞 정리가 응답하지 않은 채 같은 기준일로 다시 업로드하면 조회하지 않는다`() {
        val prune = RemotePrune(nodes)
        val fake = Fake(answer = false)
        prune.run(day, fake)
        prune.run(day, fake)
        assertEquals(nodes, fake.fetched)
    }

    @Test
    fun `TC#61 앞 정리가 끝난 뒤 같은 기준일로 다시 업로드해도 조회하지 않는다`() {
        val prune = RemotePrune(nodes)
        val fake = Fake()
        prune.run(day, fake)
        prune.run(day, fake)
        assertEquals(nodes, fake.fetched)
    }

    @Test
    fun `TC#61 기준일이 바뀌면 다시 조회한다`() {
        val prune = RemotePrune(nodes)
        val fake = Fake(answer = false)
        prune.run(day, fake)
        prune.run(day.plusDays(1), fake)
        assertEquals(nodes + nodes, fake.fetched)
    }

    @Test
    fun `TC#61 노드마다 받은 키만 지우고 지울 키가 없는 노드는 삭제 요청을 보내지 않는다`() {
        val fake = Fake(old = mapOf("daily" to listOf("2026-01-01", "2026-01-02"), "timeline" to listOf("2026-01-01")))
        RemotePrune(nodes).run(day, fake)
        assertEquals(
            listOf(mapOf<String, Any?>("daily/2026-01-01" to null, "daily/2026-01-02" to null), mapOf<String, Any?>("timeline/2026-01-01" to null)),
            fake.removed
        )
    }
}
