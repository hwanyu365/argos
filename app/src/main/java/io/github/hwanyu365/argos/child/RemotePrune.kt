package io.github.hwanyu365.argos.child

import java.time.LocalDate

/** FR#14: 기준일마다 한 번만 노드별 기준일 이전 키를 조회해 지운다. 실패해 남은 키는 다음 기준일 조회 범위에 다시 들어온다. */
class RemotePrune(private val nodes: List<String>) {
    private var started: LocalDate? = null

    fun start(
        cutoff: LocalDate,
        fetchOldKeys: (node: String, onKeys: (List<String>) -> Unit) -> Unit,
        remove: (update: Map<String, Any?>) -> Unit
    ) {
        if (cutoff == started) return
        started = cutoff
        nodes.forEach { node ->
            fetchOldKeys(node) { keys ->
                if (keys.isNotEmpty()) remove(keys.associate { "$node/$it" to null })
            }
        }
    }
}
