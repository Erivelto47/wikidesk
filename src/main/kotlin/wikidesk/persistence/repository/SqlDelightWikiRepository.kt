package wikidesk.persistence.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import wikidesk.persistence.PersistenceLog
import wikidesk.persistence.database.WikiDeskDatabase
import wikidesk.persistence.mapper.mapWikiRow
import wikidesk.persistence.model.Wiki
import wikidesk.persistence.model.WikiSourceType

/**
 * Implementação SQLite do [WikiRepository]. Toda operação passa
 * [mapWikiRow] como o `mapper` customizado das queries geradas pelo
 * SQLDelight (em vez de deixá-las devolver a data class padrão que o
 * SQLDelight geraria para `SELECT * FROM wiki`) — assim a conversão para o
 * modelo de domínio [Wiki] acontece na borda do banco, sem expor o tipo de
 * linha gerado para o resto do app.
 *
 * Toda operação roda em [ioDispatcher] (por padrão `Dispatchers.IO`) — nunca
 * no thread principal da UI.
 */
class SqlDelightWikiRepository(
    private val database: WikiDeskDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WikiRepository {

    private val queries = database.wikiQueries

    override fun observeActiveWikis(): Flow<List<Wiki>> =
        queries.selectActive(mapper = ::mapWikiRow).asFlow().mapToList(ioDispatcher)

    override suspend fun listActiveWikis(): List<Wiki> = withContext(ioDispatcher) {
        queries.selectActive(mapper = ::mapWikiRow).executeAsList()
    }

    override suspend fun findById(id: Long): Wiki? = withContext(ioDispatcher) {
        queries.selectById(id, mapper = ::mapWikiRow).executeAsOneOrNull()
    }

    override suspend fun findByPath(path: String): Wiki? = withContext(ioDispatcher) {
        queries.selectByNormalizedPath(normalizeWikiPath(path), mapper = ::mapWikiRow).executeAsOneOrNull()
    }

    override suspend fun register(
        name: String,
        rootPath: String,
        sourceType: WikiSourceType,
        remoteUrl: String?,
        defaultBranch: String?
    ): Wiki = withContext(ioDispatcher) {
        val normalizedPath = normalizeWikiPath(rootPath)
        val now = System.currentTimeMillis()
        var resultId = -1L

        database.transaction {
            val existing = queries.selectByNormalizedPath(normalizedPath, mapper = ::mapWikiRow).executeAsOneOrNull()
            if (existing != null) {
                // Já registrada (ativa ou não) — atualiza os metadados e
                // reativa em vez de duplicar (contrato de `register`, ver
                // WikiRepository).
                queries.updateWiki(
                    name = name,
                    rootPath = rootPath,
                    normalizedPath = normalizedPath,
                    sourceType = sourceType.name,
                    remoteUrl = remoteUrl,
                    defaultBranch = defaultBranch,
                    updatedAt = now,
                    id = existing.id
                )
                if (!existing.isActive) {
                    queries.setActive(isActive = 1L, updatedAt = now, id = existing.id)
                }
                resultId = existing.id
                PersistenceLog.info("Wiki '$name' já estava registrada (id=${existing.id}) — metadados atualizados.")
            } else {
                resultId = queries.insertWiki(
                    name = name,
                    rootPath = rootPath,
                    normalizedPath = normalizedPath,
                    sourceType = sourceType.name,
                    remoteUrl = remoteUrl,
                    defaultBranch = defaultBranch,
                    createdAt = now,
                    updatedAt = now,
                    lastOpenedAt = null,
                    isActive = 1L
                ).executeAsOne()
                PersistenceLog.info("Wiki '$name' registrada (id=$resultId, tipo=$sourceType).")
            }
        }

        queries.selectById(resultId, mapper = ::mapWikiRow).executeAsOne()
    }

    override suspend fun update(wiki: Wiki): Wiki = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        queries.updateWiki(
            name = wiki.name,
            rootPath = wiki.rootPath,
            normalizedPath = normalizeWikiPath(wiki.rootPath),
            sourceType = wiki.sourceType.name,
            remoteUrl = wiki.remoteUrl,
            defaultBranch = wiki.defaultBranch,
            updatedAt = now,
            id = wiki.id
        )
        queries.selectById(wiki.id, mapper = ::mapWikiRow).executeAsOne()
    }

    override suspend fun markOpened(id: Long): Unit = withContext(ioDispatcher) {
        queries.markOpened(openedAt = System.currentTimeMillis(), id = id)
    }

    override suspend fun softDelete(id: Long): Unit = withContext(ioDispatcher) {
        queries.setActive(isActive = 0L, updatedAt = System.currentTimeMillis(), id = id)
        PersistenceLog.info("Wiki id=$id removida logicamente (arquivos no disco preservados).")
    }

    override suspend fun reactivate(id: Long): Unit = withContext(ioDispatcher) {
        queries.setActive(isActive = 1L, updatedAt = System.currentTimeMillis(), id = id)
    }
}
