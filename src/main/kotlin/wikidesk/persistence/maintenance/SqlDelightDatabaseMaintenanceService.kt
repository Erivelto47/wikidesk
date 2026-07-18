package wikidesk.persistence.maintenance

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wikidesk.persistence.PersistenceLog
import java.io.File
import java.sql.Connection

/**
 * Implementação baseada na conexão JDBC crua compartilhada com
 * `wikidesk.persistence.search.Fts5SearchIndex` (ver `wikidesk.persistence.
 * PersistenceContainer`) — reaproveitada aqui em vez de abrir mais uma
 * conexão, seguindo a mesma regra de "não abrir/fechar banco a cada
 * operação" aplicada ao resto da camada de persistência.
 */
class SqlDelightDatabaseMaintenanceService(
    private val file: File,
    private val connection: Connection,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DatabaseMaintenanceService {

    override fun databaseFile(): File = file

    override fun databaseSizeBytes(): Long = file.length()

    override suspend fun checkIntegrity(): IntegrityCheckResult = withContext(ioDispatcher) {
        val messages = mutableListOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA integrity_check").use { resultSet ->
                while (resultSet.next()) {
                    messages += resultSet.getString(1)
                }
            }
        }
        val ok = messages.size == 1 && messages[0].equals("ok", ignoreCase = true)
        if (!ok) {
            PersistenceLog.error("Verificação de integridade do banco encontrou problemas: $messages")
        }
        IntegrityCheckResult(ok, messages)
    }

    override suspend fun exportBackup(destination: File): File = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        // Caminho embutido como literal (escapando aspas simples) em vez de
        // bind parameter: `VACUUM INTO` não tem suporte garantido/uniforme a
        // parâmetros posicionais em todos os drivers JDBC SQLite.
        val escapedPath = destination.absolutePath.replace("'", "''")
        connection.createStatement().use { statement ->
            statement.execute("VACUUM INTO '$escapedPath'")
        }
        PersistenceLog.info("Backup do banco exportado para ${destination.absolutePath} (${destination.length()} bytes).")
        destination
    }

    override suspend fun clearDocumentIndex(wikiId: Long?): Unit = withContext(ioDispatcher) {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            if (wikiId == null) {
                connection.createStatement().use { it.execute("DELETE FROM document") }
                clearFtsTableIfPresent(wikiId = null)
                PersistenceLog.info("Índice de documentos limpo para todas as wikis (registros de wiki preservados).")
            } else {
                connection.prepareStatement("DELETE FROM document WHERE wikiId = ?").use { statement ->
                    statement.setLong(1, wikiId)
                    statement.executeUpdate()
                }
                clearFtsTableIfPresent(wikiId)
                PersistenceLog.info("Índice de documentos limpo para a wiki id=$wikiId (registro da wiki preservado).")
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            PersistenceLog.error("Falha ao limpar índice de documentos (wikiId=$wikiId)", e)
            throw e
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override suspend fun rebuildIndex(wikiId: Long): Unit = withContext(ioDispatcher) {
        PersistenceLog.info("Reconstrução do índice solicitada para a wiki id=$wikiId — limpando índice atual; nova indexação depende de um rescan da pasta pelo chamador.")
        clearDocumentIndex(wikiId)
    }

    /** `document_fts` só existe quando o driver suporta FTS5 (ver Fts5SearchIndex) — ausência da tabela não é um erro aqui. */
    private fun clearFtsTableIfPresent(wikiId: Long?) {
        runCatching {
            if (wikiId == null) {
                connection.createStatement().use { it.execute("DELETE FROM document_fts") }
            } else {
                connection.prepareStatement("DELETE FROM document_fts WHERE wikiId = ?").use { statement ->
                    statement.setLong(1, wikiId)
                    statement.executeUpdate()
                }
            }
        }
    }
}
