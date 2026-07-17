package wikidesk.domain

/** Tipo de origem de uma fonte de documentação. */
enum class SourceType {
    LOCAL,
    GIT
}

/**
 * Uma fonte de documentação exibida na sidebar: hoje sempre uma pasta local
 * ([SourceType.LOCAL]); [SourceType.GIT] existe no modelo para suportar a
 * futura conexão de repositórios remotos (ver `wikidesk.platform.git`),
 * mas ainda não é populada por um scanner real.
 *
 * Múltiplas fontes podem coexistir simultaneamente (multi-workspace),
 * cada uma com sua própria árvore de pastas/arquivos.
 */
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val rootPath: String,
    val rootFiles: List<FileEntry> = emptyList(),
    val tree: List<FolderNode> = emptyList(),
    val initialDocumentId: String? = null
)
