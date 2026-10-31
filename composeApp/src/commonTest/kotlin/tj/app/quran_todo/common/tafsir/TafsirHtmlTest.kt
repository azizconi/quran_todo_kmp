package tj.app.quran_todo.common.tafsir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TafsirHtmlTest {
    @Test
    fun aPlainTextIsNotHtmlAndStaysAsItIs() {
        val text = "Он сказал: «Это — 2 < 3 и 5 > 4»."
        assertFalse(TafsirHtml.isHtml(text))
        assertEquals(text, TafsirHtml.clean(text))
    }

    @Test
    fun attributesAndWrapperTagsAreCutOffAndTheStructureStays() {
        val html = "<h2 class=\"x\">Title</h2><p dir=\"rtl\"><span style=\"color: rgb(59, 62, 102);\">Text</span> <i>that</i> <b>matters</b></p>"
        assertTrue(TafsirHtml.isHtml(html))
        assertEquals("<h2>Title</h2><p>Text <i>that</i> <b>matters</b></p>", TafsirHtml.clean(html))
        assertEquals("a<br>b", TafsirHtml.clean("<p>a<br/>b</p>").removePrefix("<p>").removeSuffix("</p>"))
    }

    @Test
    fun muyassarQuotesLoseTheirSpans() {
        assertEquals("أبتدئ <b>اللهِ</b>", TafsirHtml.clean("أبتدئ <span class=\"green\"><b>اللهِ</b></span>"))
    }

    @Test
    fun anHtmlTextIsCutIntoBlocksAfterParagraphsAndHeadings() {
        val html = "<h2>First</h2><p>one</p><p>two <i>x</i></p>tail"
        assertEquals(listOf("<h2>First</h2>", "<p>one</p>", "<p>two <i>x</i></p>", "tail"), TafsirHtml.blocks(html))
    }

    @Test
    fun aPlainTextIsCutAtLineBreaksAndEmptyBlocksAreLeftOut() {
        assertEquals(listOf("a", "b", "c"), TafsirHtml.blocks("a\n\n b \nc\n"))
        assertEquals(listOf("<p>x</p>"), TafsirHtml.blocks("<p></p> <p>x</p><p> </p>"))
        assertEquals(emptyList(), TafsirHtml.blocks(""))
    }
}
