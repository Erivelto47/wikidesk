package wikidesk.persistence.search

import wikidesk.persistence.database.WikiDeskDatabase

/**
 * Fallback usado quando o driver/plataforma SQLite não suporta FTS5 (ver
 * `Fts5SearchIndex.isFullTextAvailable`, checado uma única vez na composição
 * — ver `wikidesk.persistence.PersistenceContainer`). Busca por substring
 * (`LIKE`) direto nas tabelas relacionais — mais lenta e sem ranking de
 * relevância, mas funcional em qualquer SQLite; é o caminho "sempre
 * funciona" da abstração [SearchIndex], documentado explicitamente em vez de
 * deixar a busca simplesmente quebrada quando o módulo FTS5 não existe.
 *
 * Não mantém um índice próprio: como consulta `document_chunk`/`document`
 * diretamente através das queries tipadas do SQLDelight,
 * [indexChunks]/[removeDocument]/[clearWiki] não têm nada a fazer — os
 * dados já estão nas tabelas relacionais, mantidas por
 * `wikidesk.persistence.repository.DocumentIndexRepository`.
 */
class RelationalLikeSearchIndex(private val database: WikiDeskDatabase) : SearchIndex {

    override val isFullTextAvailable: Boolean = false

    override fun indexChunks(
        documentId: Long,
        wikiId: Long,
        title: String,
        relativePath: String,
        chunks: List<IndexedChunkText>
    ) = Unit

    override fun removeDocument(documentId: Long) = Unit

    override fun clearWiki(wikiId: Long) = Unit

    override fun search(wikiId: Long?, query: String, limit: Int): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val likePattern = "%" + escapeForLike(trimmed) + "%"

        return database.documentChunkQueries.searchFallback(
            wikiId = wikiId,
            likeQuery = likePattern,
            limitCount = limit.toLong()
        ).executeAsList().map { row ->
            SearchHit(
                documentId = row.documentId,
                wikiId = row.wikiId,
                relativePath = row.relativePath,
                title = row.title,
                snippet = row.content.take(160)
            )
        }
    }

    /** Escapa os coringas do próprio operador LIKE (`%`, `_`) — ver `ESCAPE '\'` na query `searchFallback`. */
    private fun escapeForLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
