package wikidesk.domain

/**
 * Representa uma seção (heading) de um [MarkdownDocument], usada para montar
 * o sumário exibido no painel direito.
 */
data class DocumentHeading(
    val level: Int,
    val text: String,
    val anchor: String,
    val position: Int
)
