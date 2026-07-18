package wikidesk.persistence.repository

import kotlinx.coroutines.flow.Flow
import wikidesk.persistence.model.DocumentChunkInput
import wikidesk.persistence.model.IndexedDocument
import wikidesk.persistence.search.SearchHit

/**
 * Metadados e conteúdo indexado (em chunks) dos documentos Markdown de cada
 * wiki. Não guarda o texto completo de um documento na tabela principal —
 * só em `document_chunk`/no índice de busca (ver `wikidesk.persistence.
 * search.SearchIndex`).
 *
 * Esta camada é infraestrutura pronta para uso: nada aqui dispara scans de
 * pasta sozinho — quem varre o disco (`wikidesk.workspace.WorkspaceScanner`)
 * é quem decide quando chamar [upsertDocument]/[replaceChunks]/[markDeleted].
 *
 * Implementação SQLite em [SqlDelightDocumentIndexRepository].
 */
interface DocumentIndexRepository {

    /**
     * Insere ou atualiza os metadados de um documento (upsert por
     * `wikiId` + `relativePath`, ver índice único no schema). Não mexe em
     * chunks — ver [replaceChunks].
     */
    suspend fun upsertDocument(
        wikiId: Long,
        relativePath: String,
        title: String,
        contentHash: String,
        fileSize: Long,
        modifiedAt: Long
    ): IndexedDocument

    /**
     * Compara [currentHashesByPath] (resultado de um scan fresco: caminho
     * relativo -> hash do conteúdo atual em disco) contra o que já está
     * indexado, e devolve os caminhos que são novos ou têm hash diferente do
     * gravado — os únicos que realmente precisam ser reprocessados.
     */
    suspend fun findChangedPaths(wikiId: Long, currentHashesByPath: Map<String, String>): Set<String>

    /**
     * Marca como removidos (logicamente) os documentos da wiki cujo caminho
     * relativo não está em [stillPresentRelativePaths] — usado depois de um
     * scan completo da pasta, para refletir arquivos apagados/movidos.
     */
    suspend fun markDeleted(wikiId: Long, stillPresentRelativePaths: Set<String>)

    /**
     * Substitui, em uma única transação, todos os chunks de [documentId]
     * pelos [chunks] informados (remove os antigos, insere os novos) — nunca
     * uma transação por linha. Também atualiza o índice de busca para este
     * documento (ver `wikidesk.persistence.search.SearchIndex`).
     */
    suspend fun replaceChunks(documentId: Long, chunks: List<DocumentChunkInput>)

    /** Documentos cujo arquivo em disco mudou depois da última indexação de conteúdo. */
    suspend fun listNeedingReindex(wikiId: Long): List<IndexedDocument>

    /** Remove todos os documentos (e, em cascata, chunks) de uma wiki — usado ao remover a wiki ou reconstruir o índice do zero. */
    suspend fun clearWikiIndex(wikiId: Long)

    /** Busca textual — delega para a [wikidesk.persistence.search.SearchIndex] configurada (FTS5 ou fallback). [wikiId] nulo busca em todas as wikis. */
    suspend fun searchDocuments(wikiId: Long? = null, query: String, limit: Int = 50): List<SearchHit>

    fun observeDocuments(wikiId: Long): Flow<List<IndexedDocument>>
}
