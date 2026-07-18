package wikidesk.persistence.model

/**
 * Snapshot/cache do último estado Git conhecido de uma wiki — não é a fonte
 * de verdade (o repositório em disco é, via `wikidesk.git.GitClient`), só o
 * que a UI pode mostrar sem reabrir o repositório.
 *
 * `null` ausente de propriedade (não a linha inteira) representa "campo
 * desconhecido nesta leitura" — ver `wikidesk.persistence.repository.
 * GitStateRepository` para a distinção completa entre nunca lido, lido e
 * limpo, e explicitamente invalidado.
 */
data class WikiGitState(
    val wikiId: Long,
    val currentBranch: String?,
    val headCommit: String?,
    val remoteHeadCommit: String?,
    /** `null` = desconhecido (nunca lido ou invalidado); `false` = repositório limpo; `true` = há alterações locais. */
    val hasLocalChanges: Boolean?,
    val aheadCount: Int?,
    val behindCount: Int?,
    val lastFetchAt: Long?,
    val lastScanAt: Long
)
