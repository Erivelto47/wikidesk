package wikidesk.domain

enum class DocumentLinkType {
    INTERNAL_DOCUMENT,
    INTERNAL_ANCHOR,
    EXTERNAL
}

/**
 * Representa um link encontrado dentro de um documento Markdown, seja para
 * outro documento local, uma âncora interna ou uma URL externa.
 */
data class DocumentLink(
    val text: String,
    val target: String,
    val type: DocumentLinkType,
    val sourceDocumentId: String
)
