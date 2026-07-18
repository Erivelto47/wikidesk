package wikidesk.persistence.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import wikidesk.persistence.PersistenceLog
import wikidesk.persistence.database.WikiDeskDatabase
import wikidesk.persistence.mapper.mapDocumentRow
import wikidesk.persistence.model.DocumentChunkInput
import wikidesk.persistence.model.IndexedDocument
import wikidesk.persistence.search.IndexedChunkText
import wikidesk.persistence.search.SearchHit
import wikidesk.persistence.search.SearchIndex

/**
 * Implementação SQLite do [DocumentIndexRepository]. [searchIndex] é
 * injetado (não construído aqui) para que a escolha entre FTS5 e o fallback
 * relacional (feita uma única vez na composição — ver
 * `wikidesk.persistence.PersistenceContainer`) fique fora desta classe, que
 * só conhece a abstração [SearchIndex].
 */
class SqlDelightDocumentIndexRepository(
    private val database: WikiDeskDatabase,
    private val searchIndex: SearchIndex,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentIndexRepository {

    private val documentQueries = database.documentQueries
    private val chunkQueries = database.documentChunkQueries

    override suspend fun upsertDocument(
        wikiId: Long,
        relativePath: String,
        title: String,
        contentHash: String,
        fileSize: Long,
        modifiedAt: Long
    ): IndexedDocument = withContext(ioDispatcher) {
        documentQueries.insertOrUpdateDocument(
            wikiId = wikiId,
            relativePath = relativePath,
            title = title,
            contentHash = contentHash,
            fileSize = fileSize,
            modifiedAt = modifiedAt,
            indexedAt = System.currentTimeMillis()
        )
        documentQueries.selectByWikiAndPath(wikiId, relativePath, mapper = ::mapDocumentRow).executeAsOne()
    }

    override suspend fun findChangedPaths(wikiId: Long, currentHashesByPath: Map<String, String>): Set<String> =
        withContext(ioDispatcher) {
            val existingHashes = documentQueries.selectHashesByWiki(
                wikiId,
                mapper = { relativePath, contentHash -> relativePath to contentHash }
            ).executeAsList().toMap()

            currentHashesByPath
                .filter { (relativePath, hash) -> existingHashes[relativePath] != hash }
                .keys
        }

    override suspend fun markDeleted(wikiId: Long, stillPresentRelativePaths: Set<String>): Unit =
        withContext(ioDispatcher) {
            val deletedAt = System.currentTimeMillis()
            if (stillPresentRelativePaths.isEmpty()) {
                documentQueries.markAllDeleted(deletedAt = deletedAt, wikiId = wikiId)
            } else {
                documentQueries.markDeletedExcept(
                    deletedAt = deletedAt,
                    wikiId = wikiId,
                    keepPaths = stillPresentRelativePaths.toList()
                )
            }
        }

    override suspend fun replaceChunks(documentId: Long, chunks: List<DocumentChunkInput>): Unit =
        withContext(ioDispatcher) {
            val document = documentQueries.selectById(documentId, mapper = ::mapDocumentRow).executeAsOneOrNull()
            if (document == null) {
                PersistenceLog.warn("replaceChunks chamado para documentId=$documentId, que não existe mais — ignorando.")
                return@withContext
            }

            database.transaction {
                chunkQueries.deleteByDocument(documentId)
                for (chunk in chunks) {
                    chunkQueries.insertChunk(
                        documentId = documentId,
                        chunkIndex = chunk.chunkIndex.toLong(),
                        headingPath = chunk.headingPath,
                        content = chunk.content,
                        contentHash = chunk.contentHash,
                        tokenCount = chunk.tokenCount?.toLong(),
                        embeddingModel = null,
                        embeddingDimension = null,
                        embeddingBlob = null,
                        embeddedAt = null
                    )
                }
            }

            PersistenceLog.info("Documento id=$documentId reindexado (${chunks.size} chunk(s)).")

            val indexedChunks = chunkQueries.selectForFtsSync(
                documentId,
                mapper = { chunkId, _, headingPath, content -> IndexedChunkText(chunkId, headingPath, content) }
            ).executeAsList()
            searchIndex.indexChunks(documentId, document.wikiId, document.title, document.relativePath, indexedChunks)
        }

    override suspend fun listNeedingReindex(wikiId: Long): List<IndexedDocument> = withContext(ioDispatcher) {
        documentQueries.selectNeedingReindex(wikiId, mapper = ::mapDocumentRow).executeAsList()
    }

    override suspend fun clearWikiIndex(wikiId: Long): Unit = withContext(ioDispatcher) {
        // ON DELETE CASCADE (foreign_keys=ON, ver DatabaseFactory) cuida de
        // remover junto os chunks de cada documento apagado.
        documentQueries.deleteByWiki(wikiId)
        searchIndex.clearWiki(wikiId)
        PersistenceLog.info("Índice de documentos da wiki id=$wikiId limpo.")
    }

    override suspend fun searchDocuments(wikiId: Long?, query: String, limit: Int): List<SearchHit> =
        withContext(ioDispatcher) {
            searchIndex.search(wikiId, query, limit)
        }

    override fun observeDocuments(wikiId: Long): Flow<List<IndexedDocument>> =
        documentQueries.selectByWiki(wikiId, mapper = ::mapDocumentRow).asFlow().mapToList(ioDispatcher)
}
