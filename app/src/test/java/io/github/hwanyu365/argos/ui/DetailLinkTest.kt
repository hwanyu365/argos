package io.github.hwanyu365.argos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailLinkTest {
    private val yt = "com.google.android.youtube"
    private val chrome = "com.android.chrome"

    @Test
    fun `TC#59 주소는 https 링크로 열고 이미 http(s) 면 그대로 둔다`() {
        assertEquals("https://namu.wiki/w/고양이", DetailLink.of(chrome, null, "namu.wiki/w/고양이"))
        assertEquals("https://google.com/search?q=penguin", DetailLink.of(chrome, null, "https://google.com/search?q=penguin"))
        assertEquals("http://example.com", DetailLink.of(chrome, null, "http://example.com"))
        // 브라우저의 YouTube 는 v 가 남아 있어 제목이 있어도 주소가 정확하다.
        assertEquals("https://m.youtube.com/watch?v=jNQXAC9IVRw", DetailLink.of(chrome, "Me at the zoo", "m.youtube.com/watch?v=jNQXAC9IVRw"))
    }

    @Test
    fun `TC#59 가린 토큰·다른 스킴·공백·호스트 없는 주소는 링크를 만들지 않는다`() {
        assertNull(DetailLink.of(chrome, null, "accounts.google.com/reset/…"))
        assertNull(DetailLink.of(chrome, null, "intent://scan#Intent;scheme=zxing;end"))
        assertNull(DetailLink.of(chrome, null, "javascript:alert(1)"))
        assertNull(DetailLink.of(chrome, null, "a b.com"))
        assertNull(DetailLink.of(chrome, null, "/path/only"))
        assertNull(DetailLink.of(chrome, null, "evil.com/\u202Emoc.elgoog"))
        assertNull(DetailLink.of(chrome, null, "evil.com/\u200Bx"))
        assertNull(DetailLink.of(chrome, null, "evil.com/\u2028x"))
        assertNull(DetailLink.of(chrome, null, "evil.com/\u061Cx"))
        // userinfo 로 신뢰할 만한 이름을 앞에 붙여 실제 호스트를 숨길 수 있다.
        assertNull(DetailLink.of(chrome, null, "https://google.com@evil.com/x"))
        assertNull(DetailLink.of(chrome, "제목", null))
    }

    @Test
    fun `TC#59 YouTube 앱 제목은 YouTube 검색 링크로, Shorts 표시는 빼고 연다`() {
        assertEquals("https://www.youtube.com/results?search_query=Me+at+the+zoo", DetailLink.of(yt, "Me at the zoo", null))
        assertEquals("https://www.youtube.com/results?search_query=%EA%B3%A0%EC%96%91%EC%9D%B4", DetailLink.of(yt, "Shorts · 고양이", null))
        assertNull(DetailLink.of(yt, "Shorts", null))
        assertNull(DetailLink.of(yt, null, null))
    }

    @Test
    fun `TC#59 확인 창은 우리가 만든 검색어만 풀어 보여주고 제어·방향 문자는 뺀다`() {
        assertEquals("https://www.youtube.com/results?search_query=고양이 영상", DetailLink.display("https://www.youtube.com/results?search_query=%EA%B3%A0%EC%96%91%EC%9D%B4+%EC%98%81%EC%83%81"))
        assertEquals("https://www.youtube.com/results?search_query=ab", DetailLink.display("https://www.youtube.com/results?search_query=a%0A%E2%80%AEb"))
        // 자녀가 올린 주소는 풀지 않는다 → 줄바꿈·경로 구분자로 확인 창을 속일 수 없다.
        assertEquals("https://evil.com/%0Aok%2Fgoogle.com", DetailLink.display("https://evil.com/%0Aok%2Fgoogle.com"))
    }

    @Test
    fun `TC#59 대문자 스킴도 웹 주소로 보고 소문자로 연다`() {
        assertEquals("https://example.com", DetailLink.of(chrome, null, "HTTPS://example.com"))
    }
}
