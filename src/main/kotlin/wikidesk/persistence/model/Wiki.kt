package wikidesk.persistence.model

/** Origem de uma wiki registrada — equivalente persistido de `wikidesk.domain.SourceType`. */
enum class WikiSourceType {
    LOCAL_FOLDER,
    GIT_REPOSITORY
}

/**
 * Uma wiki (pasta local ou repositório Git) conhecida por esta instalação,
 * já lida do banco. Todos os timestamps são epoch millis (`System.
 * currentTimeMillis()`), no mesmo padrão usado pelo restante do projeto
 * (ver `wikidesk.domain.MarkdownDocument.lastModifiedEpochMillis`).
 *
 * `isActive = false` é remoção lógica (ver `wikidesk.persistence.repository.
 * WikiRepository.softDelete`): os metadados continuam no banco, só deixam de
 * aparecer nas listagens padrão.
 */
data class Wiki(
    val id: Long,
    val name: String,
    val rootPath: String,
    val normalizedPath: String,
    val sourceType: WikiSourceType,
    val remoteUrl: String?,
    val defaultBranch: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val isActive: Boolean
)
