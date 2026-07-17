package wikidesk.document

import wikidesk.domain.CalloutKind
import wikidesk.domain.MarkdownBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownParserMermaidCalloutTest {

    @Test
    fun `fence mermaid vira bloco Mermaid em vez de CodeBlock`() {
        val text = """
            # Título

            ```mermaid
            graph TD
              A --> B
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parse(text).blocks
        val mermaid = blocks.filterIsInstance<MarkdownBlock.Mermaid>().single()

        assertTrue(mermaid.code.contains("graph TD"))
        assertTrue(mermaid.code.contains("A --> B"))
        assertTrue(blocks.none { it is MarkdownBlock.CodeBlock })
    }

    @Test
    fun `fence de outra linguagem continua sendo CodeBlock`() {
        val text = """
            ```kotlin
            val x = 1
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parse(text).blocks
        assertIs<MarkdownBlock.CodeBlock>(blocks.single())
    }

    @Test
    fun `callout estilo GitHub NOTE vira bloco Callout`() {
        val text = """
            > [!NOTE]
            > Isto é uma nota importante.
            > Com uma segunda linha.
        """.trimIndent()

        val callout = MarkdownParser.parse(text).blocks.filterIsInstance<MarkdownBlock.Callout>().single()

        assertEquals(CalloutKind.NOTE, callout.kind)
        assertTrue(callout.text.contains("Isto é uma nota importante."))
        assertTrue(callout.text.contains("Com uma segunda linha."))
    }

    @Test
    fun `callout estilo GitHub WARNING mapeia para kind WARNING`() {
        val text = """
            > [!WARNING]
            > Cuidado aqui.
        """.trimIndent()

        val callout = MarkdownParser.parse(text).blocks.filterIsInstance<MarkdownBlock.Callout>().single()
        assertEquals(CalloutKind.WARNING, callout.kind)
    }

    @Test
    fun `citacao normal sem marcador continua sendo Quote`() {
        val text = """
            > Apenas uma citação comum.
        """.trimIndent()

        val blocks = MarkdownParser.parse(text).blocks
        assertIs<MarkdownBlock.Quote>(blocks.single())
    }

    @Test
    fun `callout estilo MkDocs com titulo explicito`() {
        val text = """
            !!! tip "Dica rápida"
                Use atalhos de teclado para navegar mais rápido.
                Segunda linha do corpo.

            Parágrafo depois do admonition.
        """.trimIndent()

        val blocks = MarkdownParser.parse(text).blocks
        val callout = blocks.filterIsInstance<MarkdownBlock.Callout>().single()

        assertEquals(CalloutKind.SUCCESS, callout.kind)
        assertEquals("Dica rápida", callout.title)
        assertTrue(callout.text.contains("Use atalhos de teclado"))
        assertTrue(blocks.any { it is MarkdownBlock.Paragraph })
    }

    @Test
    fun `callout estilo MkDocs sem titulo usa titulo padrao do tipo`() {
        val text = """
            !!! danger
                Isso é perigoso.
        """.trimIndent()

        val callout = MarkdownParser.parse(text).blocks.filterIsInstance<MarkdownBlock.Callout>().single()
        assertEquals(CalloutKind.ERROR, callout.kind)
        assertEquals("Erro", callout.title)
    }
}
