package io.github.hwanyu365.argos

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class ManifestQueriesTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun queriedIntents(): Set<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val queries = doc.getElementsByTagName("queries").item(0) as Element
        val intents = queries.getElementsByTagName("intent")
        return (0 until intents.length).map { intents.item(it) as Element }.flatMap { intent ->
            val action = (intent.getElementsByTagName("action").item(0) as Element).getAttributeNS(ns, "name")
            val categories = intent.getElementsByTagName("category")
            (0 until categories.length).map { action to (categories.item(it) as Element).getAttributeNS(ns, "name") }
        }.toSet()
    }

    @Test
    fun `TC#60 패키지 조회 범위는 런처 앱과 홈 앱이다`() {
        assertEquals(
            setOf(
                "android.intent.action.MAIN" to "android.intent.category.LAUNCHER",
                "android.intent.action.MAIN" to "android.intent.category.HOME"
            ),
            queriedIntents()
        )
    }
}
