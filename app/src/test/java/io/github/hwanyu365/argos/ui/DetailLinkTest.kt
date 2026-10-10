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
        assertNull(DetailLink.of(chrome, "제목", null))
    }

    @Test
    fun `TC#59 YouTube 앱 제목은 YouTube 검색 링크로, Shorts 표시는 빼고 연다`() {
        assertEquals("https://www.youtube.com/results?search_query=Me+at+the+zoo", DetailLink.of(yt, "Me at the zoo", null))
        assertEquals("https://www.youtube.com/results?search_query=%EA%B3%A0%EC%96%91%EC%9D%B4", DetailLink.of(yt, "Shorts · 고양이", null))
        assertNull(DetailLink.of(yt, "Shorts", null))
        assertNull(DetailLink.of(yt, null, null))
    }
}
