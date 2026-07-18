package wikidesk.persistence.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import wikidesk.persistence.database.WikiDeskDatabase
import wikidesk.persistence.mapper.mapWikiGitStateRow
import wikidesk.persistence.model.WikiGitState

/**
 * Implementação SQLite do [GitStateRepository]. Ver [mapWikiGitStateRow] —
 * mesmo padrão de mapper customizado usado em [SqlDelightWikiRepository],
 * para nunca expor o tipo de linha gerado pelo SQLDelight fora desta classe.
 */
class SqlDelightGitStateRepository(
    private val database: WikiDeskDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GitStateRepository {

    private val queries = database.wikiGitStateQueries

    override suspend fun saveSnapshot(state: WikiGitState): Unit = withContext(ioDispatcher) {
        queries.upsert(
            wikiId = state.wikiId,
            currentBranch = state.currentBranch,
            headCommit = state.headCommit,
            remoteHeadCommit = state.remoteHeadCommit,
            hasLocalChanges = state.hasLocalChanges?.let { if (it) 1L else 0L },
            aheadCount = state.aheadCount?.toLong(),
            behindCount = state.behindCount?.toLong(),
            lastFetchAt = state.lastFetchAt,
            lastScanAt = state.lastScanAt
        )
    }

    override suspend fun getSnapshot(wikiId: Long): WikiGitState? = withContext(ioDispatcher) {
        queries.selectByWikiId(wikiId, mapper = ::mapWikiGitStateRow).executeAsOneOrNull()
    }

    override fun observeSnapshot(wikiId: Long): Flow<WikiGitState?> =
        queries.selectByWikiId(wikiId, mapper = ::mapWikiGitStateRow).asFlow().mapToOneOrNull(ioDispatcher)

    override suspend fun invalidate(wikiId: Long): Unit = withContext(ioDispatcher) {
        // Se nunca houve snapshot, invalidar não deveria criar um "estado
        // desconhecido" do nada — não há distinção útil entre os dois casos
        // quando não existe linha, então o UPDATE simplesmente não afeta
        // nenhuma linha (comportamento correto e barato: WHERE não bate).
        queries.invalidate(invalidatedAt = System.currentTimeMillis(), wikiId = wikiId)
    }
}
