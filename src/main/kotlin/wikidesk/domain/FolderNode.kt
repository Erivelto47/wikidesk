package wikidesk.domain

/**
 * Nó da árvore de arquivos exibida na barra lateral. Representa uma pasta
 * contendo subpastas e referências a documentos Markdown.
 *
 * A árvore inteira é montada pela descoberta recursiva de arquivos dentro do
 * [Workspace] e é independente do conteúdo de cada documento (que só é
 * carregado quando o arquivo é aberto).
 */
data class FolderNode(
    val id: String,
    val name: String,
    val files: List<FileEntry> = emptyList(),
    val children: List<FolderNode> = emptyList()
)

/**
 * Referência leve a um arquivo Markdown dentro da árvore — carrega apenas o
 * necessário para exibição na sidebar e na busca por nome.
 */
data class FileEntry(
    val id: String,
    val name: String,
    val path: String
)
