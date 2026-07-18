package wikidesk.persistence.repository

import kotlinx.coroutines.flow.Flow
import wikidesk.persistence.model.WikiGitState

/**
 * Acesso ao snapshot/cache do último estado Git conhecido de cada wiki (ver
 * `wiki_git_state` no schema). O repositório Git em disco continua sendo a
 * fonte de verdade — isto é só o que a UI mostra sem precisar reabri-lo (ver
 * `wikidesk.git.GitClient.readStatus`, chamado antes de [saveSnapshot]).
 *
 * Implementação SQLite em [SqlDelightGitStateRepository].
 */
interface GitStateRepository {

    /** Grava (ou substitui) o snapshot da wiki — sempre a foto completa mais recente, nunca um patch parcial. */
    suspend fun saveSnapshot(state: WikiGitState)

    /** `null` = nunca houve snapshot para esta wiki (distinto de um snapshot com campos desconhecidos, ver [invalidate]). */
    suspend fun getSnapshot(wikiId: Long): WikiGitState?

    fun observeSnapshot(wikiId: Long): Flow<WikiGitState?>

    /**
     * Marca o snapshot atual como não confiável (ex.: falha ao ler o
     * repositório, ou detecção de que o caminho da wiki ficou indisponível)
     * sem apagar a linha — `getSnapshot` volta a retornar um estado com os
     * campos de branch/commit/mudanças locais em `null`, distinguível de
     * "nunca leu" (ausência de linha) e de "limpo" (`hasLocalChanges = false`).
     */
    suspend fun invalidate(wikiId: Long)
}
