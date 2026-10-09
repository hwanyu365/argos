package io.github.hwanyu365.argos.child

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailExtractorTest {
    private fun n(id: String? = null, text: String? = null, desc: String? = null, focused: Boolean = false, cls: String? = null, vararg kids: UiNode) = TestNode(id, text, desc, focused, cls, kids.toList())

    private val youtube = "com.google.android.youtube"

    @Test
    fun `TC#22 포그라운드 앱의 재생 중인 미디어 제목만 쓴다`() {
        val sessions = listOf(
            MediaInfo("com.spotify.music", playing = true, title = "노래"),
            MediaInfo(youtube, playing = false, title = "멈춘 영상"),
            MediaInfo(youtube, playing = true, title = "보는 영상")
        )
        assertEquals("보는 영상", DetailExtractor.mediaTitle(youtube, sessions))
        assertNull(DetailExtractor.mediaTitle("com.kakao.talk", sessions))
    }

    @Test
    fun `TC#23 Chrome 주소창의 주소를 읽는다`() {
        val root = n(kids = arrayOf(n("com.android.chrome:id/url_bar", text = "en.wikipedia.org/wiki/Argus")))
        assertEquals(Detail(url = "en.wikipedia.org/wiki/Argus"), DetailExtractor.fromScreen("com.android.chrome", root))
    }

    @Test
    fun `TC#23 삼성 인터넷 주소창의 방향 제어 문자를 지운다`() {
        val root = n(kids = arrayOf(n("com.sec.android.app.sbrowser:id/location_bar_edit_text", text = "‎en.wikipedia.org")))
        assertEquals(Detail(url = "en.wikipedia.org"), DetailExtractor.fromScreen("com.sec.android.app.sbrowser", root))
    }

    @Test
    fun `TC#54 주소의 검색어와 영상 ID 만 남기고 나머지 쿼리와 조각은 지운다`() {
        assertEquals("www.google.com/search?q=고양이", DetailExtractor.sanitizeUrl("www.google.com/search?q=고양이&sca_esv=abc&ei=xyz"))
        assertEquals("m.search.naver.com/search.naver?query=펭귄", DetailExtractor.sanitizeUrl("m.search.naver.com/search.naver?sm=mtp&query=펭귄&where=m"))
        assertEquals("m.youtube.com/watch?v=dQw4w9WgXcQ", DetailExtractor.sanitizeUrl("m.youtube.com/watch?v=dQw4w9WgXcQ&pp=token#t=10"))
        assertEquals("m.youtube.com/results?search_query=mukbang", DetailExtractor.sanitizeUrl("m.youtube.com/results?search_query=mukbang&sp=x"))
        assertEquals("example.com/login", DetailExtractor.sanitizeUrl("example.com/login?token=secret&session=1"))
        assertEquals("example.com/page", DetailExtractor.sanitizeUrl("example.com/page#section"))
    }

    @Test
    fun `TC#54 주소창의 주소는 쿼리를 정리한 뒤 기록한다`() {
        val root = n(kids = arrayOf(n("com.android.chrome:id/url_bar", text = "www.google.com/search?q=cat&ei=1")))
        assertEquals(Detail(url = "www.google.com/search?q=cat"), DetailExtractor.fromScreen("com.android.chrome", root))
    }

    @Test
    fun `TC#24 주소창을 편집 중이면 입력 중인 글자를 기록하지 않는다`() {
        val root = n(kids = arrayOf(n("com.android.chrome:id/url_bar", text = "검색어 입력 중", focused = true)))
        assertNull(DetailExtractor.fromScreen("com.android.chrome", root))
    }

    @Test
    fun `TC#42 Shorts 화면이면 버튼 밖의 첫 글자를 제목으로 쓰고 Shorts 임을 표시한다`() {
        // 실측(GH-36): 접근성 서비스가 보는 Shorts 하단 영역. 구독·좋아요 라벨은 버튼 안, 제목은 버튼 밖에 있다.
        val footer = n(
            "$youtube:id/reel_player_footer_container",
            kids = arrayOf(
                n(cls = "ViewGroup", kids = arrayOf(n(cls = "Button", kids = arrayOf(n(desc = "@백수묵시록 채널로 이동", cls = "ImageView"))), n(desc = "@백수묵시록", text = "@백수묵시록", cls = "ViewGroup"))),
                n(desc = "@백수묵시록을(를) 구독합니다.", cls = "Button", kids = arrayOf(n(desc = "구독", text = "구독", cls = "ViewGroup"))),
                n(cls = "ViewGroup", kids = arrayOf(n(desc = "[백묵숏츠] 폭발물과 독극물", text = "[백묵숏츠] 폭발물과 독극물", cls = "ViewGroup", kids = arrayOf(n(text = "#", cls = "Button"))))),
                n(desc = "다른 사용자 1.8만명과 함께 이 동영상에 좋아요 표시", cls = "RadioButton", kids = arrayOf(n(desc = "1.8만", text = "1.8만", cls = "ViewGroup")))
            )
        )
        assertEquals(Detail(title = "Shorts · [백묵숏츠] 폭발물과 독극물"), DetailExtractor.fromScreen(youtube, n(kids = arrayOf(footer))))
    }

    @Test
    fun `TC#42 구독 다음의 첫 문구를 제목으로 쓴다 (설명만 있는 노드, 뒤따르는 링크 무시)`() {
        // 실측(GH-36) uiautomator 형태: 글자 없이 설명만 있고, 제목 뒤에 다른 영상 링크가 붙기도 한다.
        val footer = n(
            "$youtube:id/reel_player_footer_container",
            kids = arrayOf(
                n(desc = "@햄식이 채널로 이동", cls = "ImageView"),
                n(desc = "@햄식이을(를) 구독합니다.", cls = "ViewGroup"),
                n(desc = "발로란트 브론즈 근황 ㅋㅋㅋㅋ", cls = "ViewGroup"),
                n(desc = "대학교에서 코스프레 하는 미X놈 ㅋㅋㅋ[서코 브이로그]", cls = "ViewGroup"),
                n(desc = "다른 사용자 3만명과 함께 이 동영상에 좋아요 표시", cls = "ViewGroup")
            )
        )
        assertEquals(Detail(title = "Shorts · 발로란트 브론즈 근황 ㅋㅋㅋㅋ"), DetailExtractor.fromScreen(youtube, n(kids = arrayOf(footer))))
    }

    @Test
    fun `TC#42 채널 문구보다 앞에 붙은 라벨은 제목으로 쓰지 않는다`() {
        val footer = n(
            "$youtube:id/reel_player_footer_container",
            kids = arrayOf(n(text = "자동 더빙", cls = "TextView"), n(desc = "@ch 채널로 이동"), n(desc = "@ch을(를) 구독합니다.", cls = "Button", kids = arrayOf(n(text = "구독"))), n(desc = "진짜 제목", text = "진짜 제목"))
        )
        assertEquals(Detail(title = "Shorts · 진짜 제목"), DetailExtractor.fromScreen(youtube, n(kids = arrayOf(footer))))
    }

    @Test
    fun `TC#42 Shorts 제목을 찾지 못하면 Shorts 로 남긴다`() {
        val footer = n("$youtube:id/reel_player_footer_container", kids = arrayOf(n(desc = "@channel", text = "@channel"), n(cls = "Button", kids = arrayOf(n(text = "구독")))))
        assertEquals(Detail(title = "Shorts"), DetailExtractor.fromScreen(youtube, n(kids = arrayOf(footer))))
    }

    @Test
    fun `TC#42 미디어 제목이 있으면 그것을, 없으면 화면(Shorts)을 쓰고, 브라우저는 주소를 함께 남긴다`() {
        assertEquals(Detail(title = "보는 영상"), DetailExtractor.combine(mediaTitle = "보는 영상", screen = Detail(title = "Shorts")))
        assertEquals(Detail(title = "Shorts"), DetailExtractor.combine(mediaTitle = null, screen = Detail(title = "Shorts")))
        assertEquals(Detail(), DetailExtractor.combine(mediaTitle = null, screen = null))
        assertEquals(Detail(title = "웹 영상", url = "m.youtube.com/shorts/abc"), DetailExtractor.combine(mediaTitle = "웹 영상", screen = Detail(url = "m.youtube.com/shorts/abc")))
    }

    @Test
    fun `TC#44 지원 브라우저·YouTube 가 아닌 앱의 화면은 읽지 않는다`() {
        val root = n(kids = arrayOf(n("com.kakao.talk:id/message", text = "비밀 메시지"), n(desc = "대화방")))
        assertNull(DetailExtractor.fromScreen("com.kakao.talk", root))
        assertNull(DetailExtractor.fromScreen(youtube, n(kids = arrayOf(n(text = "일반 영상 화면")))))
    }

    @Test
    fun `TC#44 지원 앱만 접근성 대상으로 등록한다`() {
        assertEquals(setOf("com.android.chrome", "com.sec.android.app.sbrowser", youtube), DetailExtractor.WATCHED_PACKAGES)
    }
}

class AccessibilityConfigTest {
    @Test
    fun `TC#44 접근성 서비스 설정의 대상 앱이 코드의 목록과 같다 (OS 수준 범위 제한)`() {
        val xml = java.io.File("src/main/res/xml/accessibility_service.xml").readText()
        val pkgs = Regex("android:packageNames=\"([^\"]+)\"").find(xml)!!.groupValues[1].split(',').map { it.trim() }.toSet()
        assertEquals(DetailExtractor.WATCHED_PACKAGES, pkgs)
    }
}

data class TestNode(override val viewId: String?, override val text: String?, override val desc: String?, override val focused: Boolean, override val cls: String?, override val children: List<UiNode>) : UiNode
