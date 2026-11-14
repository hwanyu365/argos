package io.github.hwanyu365.argos.child

import org.junit.Assert.assertEquals
import org.junit.Test

class AllSucceededTest {
    @Test
    fun `TC#61 일부만 성공하면 완료로 보지 않는다`() {
        var done = 0
        val all = AllSucceeded(4) { done++ }
        repeat(3) { all.succeeded() }
        assertEquals(0, done)
    }

    @Test
    fun `TC#61 모두 성공하면 한 번만 완료로 본다`() {
        var done = 0
        val all = AllSucceeded(4) { done++ }
        repeat(5) { all.succeeded() }
        assertEquals(1, done)
    }
}
