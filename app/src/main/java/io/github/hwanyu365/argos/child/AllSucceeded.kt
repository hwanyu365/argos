package io.github.hwanyu365.argos.child

/** 비동기 작업 [total] 개가 모두 성공했을 때 [onAll] 을 한 번 부른다. 실패한 작업은 [succeeded] 를 부르지 않는다. */
class AllSucceeded(private val total: Int, private val onAll: () -> Unit) {
    private var count = 0

    fun succeeded() {
        if (++count == total) onAll()
    }
}
