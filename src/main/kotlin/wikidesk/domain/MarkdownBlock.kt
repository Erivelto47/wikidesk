package wikidesk.domain

/**
 * Representa um bloco de conteúdo já interpretado de um documento Markdown.
 *
 * Este modelo é propositalmente simples (sem AST completa) porque o MVP não
 * precisa de um parser Markdown completo para validar a experiência de leitura.
 * Quando o parser real (ex.: CommonMark) for integrado, ele deve produzir uma
 * lista de [MarkdownBlock] a partir do texto bruto do documento.
 */
sealed class MarkdownBlock {

    data class Heading(
        val level: Int,
        val text: String,
        val anchor: String
    ) : MarkdownBlock()

    data class Paragraph(
        val text: String
    ) : MarkdownBlock()

    data class BulletList(
        val items: List<String>,
        val ordered: Boolean = false
    ) : MarkdownBlock()

    data class Quote(
        val text: String
    ) : MarkdownBlock()

    data class CodeBlock(
        val language: String,
        val code: String
    ) : MarkdownBlock()

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : MarkdownBlock()

    /**
     * Imagem em bloco (uma linha contendo apenas `![alt](destino)`). Imagens
     * inline misturadas a outro texto no mesmo parágrafo ainda não são
     * suportadas — permanecem como texto bruto dentro do parágrafo.
     */
    data class Image(
        val alt: String,
        val target: String
    ) : MarkdownBlock()

    /** Bloco de código cercado por ` ```mermaid ` — renderizado como diagrama, não como código. */
    data class Mermaid(
        val code: String
    ) : MarkdownBlock()

    /**
     * Callout/admonition (nota, atenção, sucesso ou erro). Reconhecido a
     * partir de duas sintaxes de origem (ver `MarkdownParser`): o formato
     * GitHub (`> [!NOTE]`) e o formato MkDocs/Python-Markdown (`!!! note "título"`).
     * As dezenas de variações de rótulo de cada ecossistema são normalizadas
     * para apenas 4 categorias visuais.
     */
    data class Callout(
        val kind: CalloutKind,
        val title: String,
        val text: String
    ) : MarkdownBlock()

    data object Divider : MarkdownBlock()
}

/** As 4 categorias visuais suportadas para callouts/admonitions. */
enum class CalloutKind {
    NOTE,
    WARNING,
    SUCCESS,
    ERROR
}
