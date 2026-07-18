package wikidesk.persistence

import java.io.File
import java.nio.file.Files

/**
 * Utilitários compartilhados pelos testes da camada de persistência — cada
 * teste usa seu próprio arquivo de banco temporário (nunca o banco real do
 * usuário) e limpa tudo ao final, inclusive os arquivos auxiliares que o
 * modo WAL cria ao lado do banco (`-wal`, `-shm`).
 */
internal fun createTempDatabaseFile(): File {
    val file = File.createTempFile("wikidesk-test-", ".db")
    file.delete() // queremos só um caminho reservado, não o arquivo em si — DatabaseFactory cria o arquivo real.
    file.deleteOnExit()
    return file
}

internal fun createTempDirectory(prefix: String): File =
    Files.createTempDirectory(prefix).toFile().apply { deleteOnExit() }

internal fun deleteDatabaseFiles(file: File) {
    file.delete()
    File(file.parentFile, "${file.name}-wal").delete()
    File(file.parentFile, "${file.name}-shm").delete()
}

internal fun withTempPersistence(block: (PersistenceContainer) -> Unit) {
    val file = createTempDatabaseFile()
    val container = PersistenceContainer.createAt(file)
    try {
        block(container)
    } finally {
        container.close()
        deleteDatabaseFiles(file)
    }
}
