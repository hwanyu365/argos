package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.child.DetailExtractor
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** spec §6.3 X#6: 부모 화면에서 자녀가 보던 상세를 열 링크. */
object DetailLink {
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private const val SEARCH = "https://www.youtube.com/results?search_query="

    // 줄바꿈 같은 제어 문자와 표시 순서를 뒤집는 방향 문자는 확인 창을 속일 수 있다.
    private val HIDDEN = Regex("[\\p{Cc}\u200E\u200F\u202A-\u202E\u2066-\u2069]")

    /** FR#20 확인 창에 보일 주소. 우리가 만든 검색어만 읽을 수 있게 풀고, 자녀가 올린 주소는 그대로 보여준다. */
    fun display(link: String): String {
        if (!link.startsWith(SEARCH)) return link
        val query = runCatching { URLDecoder.decode(link.removePrefix(SEARCH), "UTF-8") }.getOrNull() ?: return link
        return SEARCH + HIDDEN.replace(query, "")
    }

    fun of(pkg: String?, title: String?, url: String?): String? {
        if (url != null) return web(url)
        // YouTube 앱은 영상 ID 를 주지 않으므로 제목으로 검색한다 (GH-47 실측).
        if (pkg != DetailExtractor.YOUTUBE || title == null) return null
        val query = title.removePrefix("${DetailExtractor.SHORTS} · ").takeIf { it.isNotBlank() && title != DetailExtractor.SHORTS } ?: return null
        return SEARCH + URLEncoder.encode(query, "UTF-8")
    }

    // 자녀 기기는 변조될 수 있으므로 http(s) 웹 주소만 연다. 가린 토큰이 있는 주소는 열어도 깨진 페이지다.
    private fun web(url: String): String? {
        if ('…' in url) return null
        val full = if (SCHEME.containsMatchIn(url)) url else "https://$url"
        val uri = runCatching { URI(full) }.getOrNull() ?: return null
        return full.takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty() }
    }
}
