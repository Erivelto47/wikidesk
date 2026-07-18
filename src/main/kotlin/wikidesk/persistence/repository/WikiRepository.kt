package wikidesk.persistence.repository

import kotlinx.coroutines.flow.Flow
import wikidesk.persistence.model.Wiki
import wikidesk.persistence.model.WikiSourceType

/**
 * Acesso às wikis registradas por esta instalação (pastas locais e
 * repositórios Git clonados). Implementação SQLite em
 * [SqlDelightWikiRepository].
 *
 * Nenhuma operação aqui apaga arquivos do disco — "remover" uma wiki é
 * sempre remoção lógica (ver [softDelete]); quem decide apagar pastas de
 * verdade é o usuário, fora desta camada.
 */
interface WikiRepository {

    /** Wikis ativas (`isActive = true`), ordenadas por nome. Emite de novo a cada alteração relevante. */
    fun observeActiveWikis(): Flow<List<Wiki>>

    suspend fun listActiveWikis(): List<Wiki>

    suspend fun findById(id: Long): Wiki?

    /** Busca por caminho, comparando a forma normalizada (ver `normalizeWikiPath`) — não a string original. */
    suspend fun findByPath(path: String): Wiki?

    /**
     * Registra uma wiki nova, ou reativa/atualiza uma já registrada com o
     * mesmo caminho normalizado (nunca cria duplicata) — é o que garante
     * "evite registrar duplicadamente o mesmo diretório" mesmo se o usuário
     * apontar para a mesma pasta por um caminho textualmente diferente
     * (symlink, `..`, etc.).
     */
    suspend fun register(
        name: String,
        rootPath: String,
        sourceType: WikiSourceType,
        remoteUrl: String? = null,
        defaultBranch: String? = null
    ): Wiki

    /** Atualiza metadados de uma wiki já existente (ex.: relink de caminho, branch padrão). */
    suspend fun update(wiki: Wiki): Wiki

    /** Marca o instante em que a wiki foi aberta pela última vez — usado para restaurar a última wiki aberta no próximo startup. */
    suspend fun markOpened(id: Long)

    /** Remoção lógica: `isActive = false`. Metadados e histórico continuam no banco. */
    suspend fun softDelete(id: Long)

    suspend fun reactivate(id: Long)
}
