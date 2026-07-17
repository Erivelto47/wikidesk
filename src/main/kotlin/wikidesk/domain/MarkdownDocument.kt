package wikidesk.domain

/**
 * Representa um documento Markdown carregado a partir do sistema de arquivos.
 *
 * @property id identificador estável (derivado do caminho) usado para abas,
 *   histórico e destaque na árvore lateral.
 * @property path caminho absoluto do arquivo no disco.
 * @property title título do documento (heading H1 ou nome do arquivo).
 * @property blocks conteúdo já convertido em blocos renderizáveis.
 * @property headings lista de seções, usada para montar o sumário.
 * @property links links encontrados no documento (internos e externos).
 * @property lastModifiedEpochMillis timestamp de última modificação, usado
 *   para detectar alterações externas ao arquivo.
 */
data class MarkdownDocument(
    val id: String,
    val path: String,
    val title: String,
    val blocks: List<MarkdownBlock> = emptyList(),
    val headings: List<DocumentHeading> = emptyList(),
    val links: List<DocumentLink> = emptyList(),
    val lastModifiedEpochMillis: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)
