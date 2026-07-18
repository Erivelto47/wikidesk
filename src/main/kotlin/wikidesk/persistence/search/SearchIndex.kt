package wikidesk.persistence.search

/** Um resultado de busca textual, já resolvido para o documento a que pertence. */
data class SearchHit(
    val documentId: Long,
    val wikiId: Long,
    val relativePath: String,
    val title: String,
    val snippet: String
)

/** Texto de um chunk já persistido, pronto para ser espelhado no índice de busca. */
data class IndexedChunkText(
    val chunkId: Long,
    val headingPath: String?,
    val content: String
)

/**
 * Abstração de busca textual: quem consome (`wikidesk.persistence.repository.
 * DocumentIndexRepository`) não sabe, nem precisa saber, se por baixo é FTS5,
 * um fallback relacional com `LIKE`, ou — no futuro — uma combinação com
 * filtros estruturados e busca semântica por embeddings sobre
 * `document_chunk.embeddingBlob`. Nenhuma tela da UI deve depender de uma
 * implementação específica desta interface nem montar consultas FTS5
 * diretamente.
 *
 * Duas implementações hoje: [wikidesk.persistence.search.Fts5SearchIndex]
 * (quando o SQLite embutido suporta o módulo FTS5) e
 * [wikidesk.persistence.search.RelationalLikeSearchIndex] (fallback
 * documentado, sempre disponível). A escolha entre as duas acontece uma
 * única vez, na composição (`wikidesk.persistence.PersistenceContainer`),
 * com base em [isFullTextAvailable].
 */
interface SearchIndex {

    /** Se `false`, esta implementação é o fallback — buscas ainda funcionam, só sem ranking por relevância. */
    val isFullTextAvailable: Boolean

    /** Substitui, de forma atômica, todos os chunks indexados de um documento. */
    fun indexChunks(documentId: Long, wikiId: Long, title: String, relativePath: String, chunks: List<IndexedChunkText>)

    /** Remove um documento inteiro do índice (ex.: arquivo apagado do disco). */
    fun removeDocument(documentId: Long)

    /** Remove todos os documentos de uma wiki do índice (ex.: wiki removida ou índice sendo reconstruído). */
    fun clearWiki(wikiId: Long)

    /** Busca textual livre. [wikiId] nulo busca em todas as wikis registradas. */
    fun search(wikiId: Long?, query: String, limit: Int): List<SearchHit>
}
