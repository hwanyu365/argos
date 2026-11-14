package io.github.hwanyu365.argos.child

/** FR#14: 노드마다 기준일 이전 키를 조회해 지우고, 모든 노드가 성공했을 때만 [onAllSucceeded] 를 한 번 부른다. 실패한 조회·삭제는 콜백을 부르지 않는다. */
object RemotePrune {
    fun run(
        nodes: List<String>,
        fetchOldKeys: (node: String, onKeys: (List<String>) -> Unit) -> Unit,
        remove: (update: Map<String, Any?>, onDone: () -> Unit) -> Unit,
        onAllSucceeded: () -> Unit
    ) {
        var succeeded = 0
        val done = { if (++succeeded == nodes.size) onAllSucceeded() }
        nodes.forEach { node ->
            fetchOldKeys(node) { keys ->
                if (keys.isEmpty()) done() else remove(keys.associate { "$node/$it" to null }) { done() }
            }
        }
    }
}
