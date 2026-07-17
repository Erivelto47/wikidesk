package wikidesk.workspace

import wikidesk.document.MarkdownParser
import wikidesk.domain.FileEntry
import wikidesk.domain.FolderNode
import wikidesk.domain.MarkdownDocument
import wikidesk.domain.Source
import wikidesk.domain.SourceType
import java.io.File

/** Resultado de uma leitura de pasta: a fonte (árvore de navegação) e os documentos já interpretados. */
data class LoadedSource(
    val source: Source,
    val documents: Map<String, MarkdownDocument>
)

/**
 * Descobre documentos Markdown dentro de uma pasta local (recursivamente) e
 * monta tanto a árvore de navegação da sidebar quanto o conteúdo já
 * interpretado de cada arquivo.
 *
 * Todos os ids (de documentos e pastas) são prefixados com o [sourceId] no
 * formato `"$sourceId::$caminhoRelativo"`, para que permaneçam únicos mesmo
 * quando várias fontes estão abertas ao mesmo tempo (multi-workspace) e duas
 * fontes diferentes têm arquivos com o mesmo caminho relativo (ex.: ambas
 * têm um `README.md` na raiz).
 *
 * Implementação síncrona e "eager": lê e interpreta todos os `.md` de uma vez
 * ao abrir a fonte. Adequada para pastas de documentação (dezenas a poucas
 * centenas de arquivos), que é o caso de uso alvo do MVP.
 */
object WorkspaceScanner {

    private val ignoredDirNames = setOf(".git", ".idea", ".obsidian", ".vscode", "node_modules", ".gradle", "build")

    fun scanLocal(sourceId: String, rootPath: String): LoadedSource? {
        val rootDir = File(rootPath)
        if (!rootDir.isDirectory) return null

        fun compositeId(relativePath: String) = "$sourceId::$relativePath"
        fun relativePathOf(file: File) = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')

        val markdownFiles = rootDir.walkTopDown()
            .onEnter { dir -> dir.name !in ignoredDirNames }
            .filter { it.isFile && it.extension.lowercase() == "md" }
            .toList()

        val fileEntryByFile = mutableMapOf<File, FileEntry>()
        val documents = mutableMapOf<String, MarkdownDocument>()

        for (file in markdownFiles) {
            val relativePath = relativePathOf(file)
            val id = compositeId(relativePath)
            val rawText = runCatching { file.readText() }.getOrDefault("")
            val parsed = MarkdownParser.parse(rawText)
            val title = parsed.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension

            documents[id] = MarkdownDocument(
                id = id,
                path = file.absolutePath,
                title = title,
                blocks = parsed.blocks,
                headings = parsed.headings,
                lastModifiedEpochMillis = file.lastModified()
            )
            fileEntryByFile[file] = FileEntry(id = id, name = file.name, path = relativePath)
        }

        val filesByParentDir = markdownFiles.groupBy { it.parentFile }

        fun containsMarkdown(dir: File): Boolean {
            val prefix = dir.absolutePath + File.separator
            return markdownFiles.any { it.absolutePath.startsWith(prefix) }
        }

        fun listSubDirs(dir: File): List<File> {
            return (dir.listFiles { f -> f.isDirectory && f.name !in ignoredDirNames && !f.name.startsWith(".") }
                ?: emptyArray())
                .filter { containsMarkdown(it) }
                .sortedBy { it.name.lowercase() }
        }

        fun buildFolder(dir: File): FolderNode {
            val filesHere = (filesByParentDir[dir] ?: emptyList())
                .sortedBy { it.name.lowercase() }
                .mapNotNull { fileEntryByFile[it] }

            val children = listSubDirs(dir).map { buildFolder(it) }

            return FolderNode(
                id = compositeId(relativePathOf(dir)),
                name = dir.name,
                files = filesHere,
                children = children
            )
        }

        val rootFiles = (filesByParentDir[rootDir] ?: emptyList())
            .sortedBy { it.name.lowercase() }
            .mapNotNull { fileEntryByFile[it] }

        val topFolders = listSubDirs(rootDir).map { buildFolder(it) }

        val initialDocumentId = rootFiles.firstOrNull()?.id
            ?: markdownFiles.minByOrNull { it.absolutePath }?.let { compositeId(relativePathOf(it)) }

        val source = Source(
            id = sourceId,
            name = rootDir.name,
            type = SourceType.LOCAL,
            rootPath = rootDir.absolutePath,
            rootFiles = rootFiles,
            tree = topFolders,
            initialDocumentId = initialDocumentId
        )

        return LoadedSource(source = source, documents = documents)
    }
}
