package io.github.hwanyu365.argos.child

/** 접근성 노드에서 필요한 값만 옮긴 것. 추출 규칙을 Android 없이 시험하기 위해 둔다. */
interface UiNode {
    val viewId: String?
    val text: String?
    val desc: String?
    val focused: Boolean
    val cls: String?
    val children: List<UiNode>
}

data class MediaInfo(val pkg: String, val playing: Boolean, val title: String?)

/** spec §6.3. 읽는 범위는 X#0 으로 한정한다: 지원 브라우저 주소창, YouTube Shorts 제목. */
object DetailExtractor {
    private const val YOUTUBE = "com.google.android.youtube"

    // X#2: 실기기에서 확인한 브라우저만 (GH-1). 다른 브라우저는 확인 후 추가한다.
    private val URL_BARS = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text"
    )
    private const val SHORTS = "Shorts"
    private const val SHORTS_FOOTER = "$YOUTUBE:id/reel_player_footer_container"

    /** 접근성 서비스 설정의 대상 앱. OS 가 이 앱들의 화면만 전달하므로 X#0 이 코드가 아니라 설정으로도 보장된다. */
    val WATCHED_PACKAGES: Set<String> = URL_BARS.keys + YOUTUBE

    // 화면에 보이지 않는 방향 제어 문자 (삼성 인터넷이 주소 앞에 붙인다).
    private val BIDI = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]")

    /** X#1: 포그라운드 앱의 재생 중인 미디어 제목. */
    fun mediaTitle(foreground: String, sessions: List<MediaInfo>): String? = sessions.firstOrNull { it.pkg == foreground && it.playing && !it.title.isNullOrBlank() }?.title

    /** X#0·X#1b·X#2·X#3: 지원 앱이 아니면 아무것도 읽지 않는다(null). */
    fun fromScreen(pkg: String, root: UiNode): Detail? {
        URL_BARS[pkg]?.let { id ->
            val bar = find(root) { it.viewId == id } ?: return null
            // X#3: 편집 중인 주소창의 글자는 방문 주소가 아니라 입력 중인 검색어일 수 있다.
            if (bar.focused) return null
            return bar.text?.replace(BIDI, "")?.trim()?.takeIf { it.isNotEmpty() }?.let { Detail(url = sanitizeUrl(it)) }
        }
        if (pkg == YOUTUBE) {
            val footer = find(root) { it.viewId == SHORTS_FOOTER } ?: return null
            // 실측(GH-36): 순서가 '@채널 → @채널 구독 → 제목 → …' 이다. 마지막 '@' 문구 뒤의, 버튼 안 라벨이 아닌 첫 문구가 제목이다.
            // 채널 앞에 붙는 라벨(예: 자동 더빙)과 버튼 안 라벨(구독·좋아요 수)은 이렇게 걸러진다.
            val labels = labelsOutsideButtons(footer).toList()
            val title = labels.drop(labels.indexOfLast { it.startsWith("@") } + 1).firstOrNull()
            return Detail(title = title?.let { "$SHORTS · $it" } ?: SHORTS)
        }
        return null
    }

    // X#5: 무엇을 찾아봤는지(검색어)와 어떤 영상인지(v)만 남긴다. 저장소 소유자 결정 (GH-36).
    private val KEPT_QUERY = setOf("q", "query", "search_query", "v")
    private val TOKEN = Regex("(?=.*[0-9])(?=.*[A-Za-z])[A-Za-z0-9_-]{24,}")

    /** X#5: 쿼리는 검색어·영상 ID 만 남기고 지운다. 로그인 토큰·추적 값이 Firebase 로 올라가지 않게 한다. */
    fun sanitizeUrl(url: String): String {
        val noFragment = url.substringBefore('#')
        // 경로 조각 중 24자 이상의 영문·숫자 덩어리는 토큰으로 보고 가린다 (영상 ID 같은 짧은 식별자는 남는다).
        val path = noFragment.substringBefore('?').split('/').joinToString("/") { if (TOKEN.matches(it)) "…" else it }
        val kept = noFragment.substringAfter('?', "").split('&').filter { it.substringBefore('=') in KEPT_QUERY && it.contains('=') }
        return if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
    }

    /** X#1b: 미디어 세션 제목이 있으면 그것을, 없으면 화면에서 읽은 제목을 쓴다. 브라우저 주소는 함께 남긴다 (웹 영상 재생 중에도 주소가 보이게). */
    fun combine(mediaTitle: String?, screen: Detail?): Detail = Detail(title = mediaTitle ?: screen?.title, url = screen?.url)

    private fun find(node: UiNode, pred: (UiNode) -> Boolean): UiNode? = if (pred(node)) node else node.children.firstNotNullOfOrNull { find(it, pred) }

    private val BUTTONS = setOf("Button", "RadioButton", "ImageButton", "ToggleButton", "CheckBox")

    /** 문서 순서대로 각 노드의 글자(없으면 설명). 버튼은 자기 설명(예: '@채널 구독')만 내고 안쪽 라벨은 건너뛴다. */
    private fun labelsOutsideButtons(node: UiNode): Sequence<String> {
        val own = sequenceOf((node.text?.takeIf { it.isNotBlank() } ?: node.desc)?.trim()).filterNotNull().filter { it.isNotEmpty() }
        return if (node.cls in BUTTONS) own else own + node.children.asSequence().flatMap { labelsOutsideButtons(it) }
    }
}
