package wikidesk.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlMarkdownTest {

    @Test
    fun `texto sem html passa intacto`() {
        val text = "# Título\n\nParágrafo normal com **negrito**."
        assertEquals(text, HtmlMarkdown.normalize(text))
    }

    @Test
    fun `tags com equivalente markdown sao convertidas`() {
        val result = HtmlMarkdown.normalize(
            "<h2>Seção</h2><p>Um <strong>destaque</strong> com <a href=\"guia.md\">link</a> e <code>trecho</code>.</p>"
        )
        assertTrue("## Seção" in result, "heading deveria virar ##: $result")
        assertTrue("**destaque**" in result)
        assertTrue("[link](guia.md)" in result)
        assertTrue("`trecho`" in result)
        assertFalse("<" in result.replace("<!--", ""), "não deveria sobrar tag: $result")
    }

    @Test
    fun `divs e spans sao descartados mantendo o texto`() {
        val result = HtmlMarkdown.normalize(
            "<div class=\"row\"> <div class=\"card\"> <h3 class=\"card-title\">Temas</h3> " +
                "<p class=\"card-text\">Há vários <a href=\"temas/\">temas</a> disponíveis.</p> </div> </div>"
        )
        assertTrue("### Temas" in result)
        assertTrue("[temas](temas/)" in result)
        assertFalse("class=" in result)
        assertFalse("div" in result)
    }

    @Test
    fun `img vira imagem markdown e br vira quebra de linha`() {
        val result = HtmlMarkdown.normalize("<img src=\"logo.png\" alt=\"Logo\">linha 1<br>linha 2")
        assertTrue("![Logo](logo.png)" in result)
        assertTrue("linha 1\nlinha 2" in result)
    }

    @Test
    fun `itens de lista html viram lista markdown`() {
        val result = HtmlMarkdown.normalize("<ul><li>Primeiro</li><li>Segundo</li></ul>")
        assertTrue("- Primeiro" in result)
        assertTrue("- Segundo" in result)
    }

    @Test
    fun `html dentro de bloco de codigo cercado e preservado`() {
        val text = "Antes\n\n```html\n<div class=\"card\">conteúdo</div>\n```\n\nDepois"
        val result = HtmlMarkdown.normalize(text)
        assertTrue("<div class=\"card\">conteúdo</div>" in result, "código cercado não pode ser alterado: $result")
    }

    @Test
    fun `html dentro de span de codigo inline e preservado`() {
        val result = HtmlMarkdown.normalize("Use a tag `<br>` para quebrar linha.<br>Fim.")
        assertTrue("`<br>`" in result, "span inline não pode ser alterado: $result")
        assertTrue("linha.\nFim." in result, "o <br> fora do span deveria virar quebra: $result")
    }

    @Test
    fun `entidades html comuns sao decodificadas`() {
        val result = HtmlMarkdown.normalize("<p>a &amp; b &lt;c&gt; &quot;d&quot;</p>")
        assertTrue("a & b <c> \"d\"" in result)
    }

    @Test
    fun `comentarios html sao removidos`() {
        val result = HtmlMarkdown.normalize("Visível<!-- oculto\nem duas linhas -->ainda visível")
        assertEquals("Visívelainda visível", result.trim())
    }

    @Test
    fun `parser integra normalizacao de html`() {
        val parsed = MarkdownParser.parse("# Doc\n\n<div class=\"text-center\"><a href=\"start/\" class=\"btn\">Começar</a></div>")
        val paragraph = parsed.blocks.filterIsInstance<wikidesk.domain.MarkdownBlock.Paragraph>().firstOrNull()
        assertTrue(paragraph != null && "[Começar](start/)" in paragraph.text, "esperava link markdown: ${parsed.blocks}")
    }
}
