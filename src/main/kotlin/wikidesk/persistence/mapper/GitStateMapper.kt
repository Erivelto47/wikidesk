package wikidesk.persistence.mapper

import wikidesk.persistence.model.WikiGitState

/**
 * Converte os campos "crus" de uma linha `wiki_git_state` (colunas INTEGER
 * chegam como `Long` do SQLDelight) para [WikiGitState]. Ver
 * `wikidesk.persistence.mapper.mapWikiRow` para o porquê de receber campos
 * individuais em vez do tipo de linha gerado.
 */
fun mapWikiGitStateRow(
    wikiId: Long,
    currentBranch: String?,
    headCommit: String?,
    remoteHeadCommit: String?,
    hasLocalChanges: Long?,
    aheadCount: Long?,
    behindCount: Long?,
    lastFetchAt: Long?,
    lastScanAt: Long
): WikiGitState = WikiGitState(
    wikiId = wikiId,
    currentBranch = currentBranch,
    headCommit = headCommit,
    remoteHeadCommit = remoteHeadCommit,
    hasLocalChanges = hasLocalChanges?.let { it != 0L },
    aheadCount = aheadCount?.toInt(),
    behindCount = behindCount?.toInt(),
    lastFetchAt = lastFetchAt,
    lastScanAt = lastScanAt
)
