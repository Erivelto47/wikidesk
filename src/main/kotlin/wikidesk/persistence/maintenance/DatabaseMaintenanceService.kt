package wikidesk.persistence.maintenance

import java.io.File

/** Resultado de `PRAGMA integrity_check` — [messages] tem uma única entrada `"ok"` quando [ok] é `true`. */
data class IntegrityCheckResult(val ok: Boolean, val messages: List<String>)

/**
 * Operações simples de manutenção do banco local — nada aqui envolve
 * sincronização em nuvem ou qualquer serviço externo; é só arquivo local
 * lido/copiado/verificado no próprio disco do usuário.
 *
 * Implementação SQLite em [SqlDelightDatabaseMaintenanceService].
 */
interface DatabaseMaintenanceService {

    /** Caminho do arquivo do banco em uso. */
    fun databaseFile(): File

    /** Tamanho atual do arquivo do banco, em bytes. */
    fun databaseSizeBytes(): Long

    /** Roda `PRAGMA integrity_check` — útil para diagnosticar suspeita de corrupção antes de reportar um bug. */
    suspend fun checkIntegrity(): IntegrityCheckResult

    /**
     * Exporta uma cópia consistente do banco para [destination] via
     * `VACUUM INTO` (captura um estado coerente mesmo com o banco em uso,
     * ao contrário de uma cópia de arquivo simples, que poderia pegar o
     * banco no meio de uma escrita). Devolve [destination].
     */
    suspend fun exportBackup(destination: File): File

    /**
     * Remove os dados de índice de documentos (metadados + chunks + índice
     * de busca) sem apagar nenhuma wiki registrada. `wikiId` nulo limpa o
     * índice de todas as wikis de uma vez.
     */
    suspend fun clearDocumentIndex(wikiId: Long? = null)

    /**
     * Prepara [wikiId] para reindexação completa (limpa o índice existente
     * dessa wiki). Não varre a pasta nem gera chunks novos sozinha — quem
     * chama ainda precisa rescanear os arquivos e chamar
     * `wikidesk.persistence.repository.DocumentIndexRepository.upsertDocument`/
     * `replaceChunks` para cada um, exatamente como faria numa primeira
     * indexação. Mantida separada de [clearDocumentIndex] para deixar a
     * intenção ("vou reconstruir isto agora") explícita nos logs.
     */
    suspend fun rebuildIndex(wikiId: Long)
}
