package io.github.hwanyu365.argos.child

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * X#0·X#1b·X#2: 지원 브라우저 주소와 YouTube Shorts 제목만 읽는다.
 * 대상 앱은 res/xml/accessibility_service.xml 에서 OS 수준으로 한정되고, 읽은 값은 메모리에만 두어 감시 루프가 가져간다.
 */
class UrlAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in DetailExtractor.WATCHED_PACKAGES) return
        val root = rootInActiveWindow ?: return
        // 활성 창이 이벤트를 낸 앱이 아니면(알림창·팝업 등) 다른 화면을 읽게 되므로 건너뛴다 (X#0).
        if (root.packageName?.toString() != pkg) return
        val tree = A11yTree(root)
        try {
            ScreenDetail.update(pkg, DetailExtractor.fromScreen(pkg, tree.root))
        } finally {
            tree.recycle()
        }
    }

    override fun onInterrupt() = Unit
}

/** 펼친 노드를 모아 두었다가 API 33 미만에서 반환한다 (그 이상에서는 recycle 이 아무 일도 하지 않는다). */
private class A11yTree(rootNode: AccessibilityNodeInfo) {
    private val created = mutableListOf(rootNode)
    val root: UiNode = A11yNode(rootNode)

    fun recycle() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) created.forEach { it.recycle() }
    }

    /** 접근성 노드를 필요할 때만 펼친다. 찾는 노드를 만나면 더 내려가지 않는다. */
    private inner class A11yNode(private val node: AccessibilityNodeInfo) : UiNode {
        override val viewId: String? get() = node.viewIdResourceName
        override val text: String? get() = node.text?.toString()
        override val desc: String? get() = node.contentDescription?.toString()
        override val focused: Boolean get() = node.isFocused
        override val cls: String? get() = node.className?.toString()?.substringAfterLast('.')
        override val children: List<UiNode> by lazy { (0 until node.childCount).mapNotNull { node.getChild(it) }.onEach { created += it }.map(::A11yNode) }
    }
}

/** 접근성 서비스와 감시 서비스(같은 프로세스) 사이에서 마지막으로 읽은 화면 상세를 넘긴다. */
object ScreenDetail {
    @Volatile private var last: Pair<String, Detail?>? = null

    fun update(pkg: String, detail: Detail?) {
        last = pkg to detail
    }

    /** 현재 앱의 화면에서 읽은 값. 다른 앱의 값이 남아 있으면 쓰지 않는다. */
    fun of(pkg: String): Detail? = last?.takeIf { it.first == pkg }?.second
}
