package wikidesk.persistence.search

import wikidesk.persistence.PersistenceLog
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Índice de busca textual baseado em FTS5, mantido em uma conexão JDBC crua
 * separada da conexão usada pelo SQLDelight. A tabela virtual FTS5 e o
 * operador `MATCH` não passam pelas queries tipadas geradas a partir dos
 * arquivos `.sq` — em vez de tentar forçar o parser SQL do SQLDelight a
 * entender uma sintaxe que ele não precisa entender, esta classe fala JDBC
 * puro, isolando toda a superfície "arriscada" (SQL específico de FTS5) em
 * um único lugar (ver `SearchIndex`, a abstração que o resto do app usa).
 *
 * As duas conexões (esta e a do SQLDelight) apontam para o mesmo arquivo de
 * banco; isso é seguro porque o banco roda em modo WAL (ver
 * `wikidesk.persistence.database.DatabaseFactory`), que suporta múltiplos
 * leitores e um escritor concorrentes sobre o mesmo arquivo. Esta conexão é
 * aberta uma única vez e reaproveitada durante toda a vida do app (ver
 * `wikidesk.persistence.PersistenceContainer`) — nunca uma nova conexão por
 * operação de busca/indexação.
 *
 * Indexação textual fica deliberadamente separada da tabela principal
 * `document`: a tabela `document_fts` é escrita/lida só por esta classe.
 *
 * [connection] é exposta (não privada) para que
 * `wikidesk.persistence.maintenance.SqlDelightDatabaseMaintenanceService`
 * possa reaproveitar a mesma conexão crua em vez de abrir mais uma — ver
 * `wikidesk.persistence.PersistenceContainer`, onde as duas são compostas.
 */
class Fts5SearchIndex(
    val connection: Connection
) : SearchIndex {

    override val isFullTextAvailable: Boolean = probeAvailability()

    init {
        if (isFullTextAvailable) {
            ensureSchema()
        } else {
            PersistenceLog.warn(
                "FTS5 não está disponível neste driver/plataforma SQLite — busca textual usará o fallback " +
                    "relacional (ver wikidesk.persistence.search.RelationalLikeSearchIndex)."
            )
        }
    }

    /** Tenta criar e derrubar uma tabela virtual FTS5 descartável — se o módulo não existir, o driver lança SQLException. */
    private fun probeAvailability(): Boolean = try {
        connection.createStatement().use { statement ->
            statement.execute("CREATE VIRTUAL TABLE IF NOT EXISTS wikidesk_fts5_probe USING fts5(x)")
            statement.execute("DROP TABLE IF EXISTS wikidesk_fts5_probe")
        }
        true
    } catch (e: Exception) {
        PersistenceLog.warn("Probe de suporte a FTS5 falhou — assumindo indisponível: ${e.message}")
        false
    }

    private fun ensureSchema() {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS document_fts USING fts5(
                    title,
                    headingPath,
                    content,
                    relativePath,
                    documentId UNINDEXED,
                    wikiId UNINDEXED,
                    chunkId UNINDEXED,
                    tokenize = 'unicode61'
                )
                """.trimIndent()
            )
        }
    }

    override fun indexChunks(
        documentId: Long,
        wikiId: Long,
        title: String,
        relativePath: String,
        chunks: List<IndexedChunkText>
    ) {
        if (!isFullTextAvailable) return

        runCatching {
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                deleteDocumentRows(documentId)
                if (chunks.isNotEmpty()) {
                    connection.prepareStatement(
                        "INSERT INTO document_fts (title, headingPath, content, relativePath, documentId, wikiId, chunkId) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)"
                    ).use { insert ->
                        for (chunk in chunks) {
                            insert.setString(1, title)
                            insert.setString(2, chunk.headingPath.orEmpty())
                            insert.setString(3, chunk.content)
                            insert.setString(4, relativePath)
                            insert.setLong(5, documentId)
                            insert.setLong(6, wikiId)
                            insert.setLong(7, chunk.chunkId)
                            insert.addBatch()
                        }
                        insert.executeBatch()
                    }
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }.onFailure { e ->
            // O índice FTS5 é secundário/derivado — uma falha aqui não deve
            // derrubar a operação de indexação relacional que o chamou. A
            // busca textual só fica temporariamente desatualizada para este
            // documento até a próxima reindexação.
            PersistenceLog.warn(
                "Falha ao atualizar índice FTS5 para o documento $documentId — busca textual pode ficar " +
                    "temporariamente desatualizada para este documento.",
                e
            )
        }
    }

    override fun removeDocument(documentId: Long) {
        if (!isFullTextAvailable) return
        runCatching { deleteDocumentRows(documentId) }
            .onFailure { e -> PersistenceLog.warn("Falha ao remover documento $documentId do índice FTS5", e) }
    }

    private fun deleteDocumentRows(documentId: Long) {
        connection.prepareStatement("DELETE FROM document_fts WHERE documentId = ?").use { statement ->
            statement.setLong(1, documentId)
            statement.executeUpdate()
        }
    }

    override fun clearWiki(wikiId: Long) {
        if (!isFullTextAvailable) return
        runCatching {
            connection.prepareStatement("DELETE FROM document_fts WHERE wikiId = ?").use { statement ->
                statement.setLong(1, wikiId)
                statement.executeUpdate()
            }
        }.onFailure { e -> PersistenceLog.warn("Falha ao limpar índice FTS5 da wiki $wikiId", e) }
    }

    override fun search(wikiId: Long?, query: String, limit: Int): List<SearchHit> {
        if (!isFullTextAvailable || query.isBlank()) return emptyList()

        val sql = buildString {
            append(
                "SELECT documentId, wikiId, relativePath, title, snippet(document_fts, 2, '', '', '…', 12) AS snippet " +
                    "FROM document_fts WHERE document_fts MATCH ?"
            )
            if (wikiId != null) append(" AND wikiId = ?")
            append(" LIMIT ?")
        }

        return try {
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setString(index++, sanitizeMatchQuery(query))
                if (wikiId != null) statement.setLong(index++, wikiId)
                statement.setInt(index, limit)

                statement.executeQuery().use { resultSet ->
                    val hits = mutableListOf<SearchHit>()
                    while (resultSet.next()) {
                        hits += SearchHit(
                            documentId = resultSet.getLong("documentId"),
                            wikiId = resultSet.getLong("wikiId"),
                            relativePath = resultSet.getString("relativePath"),
                            title = resultSet.getString("title"),
                            snippet = resultSet.getString("snippet").orEmpty()
                        )
                    }
                    hits
                }
            }
        } catch (e: Exception) {
            PersistenceLog.warn("Busca FTS5 falhou para a consulta informada — retornando nenhum resultado.", e)
            emptyList()
        }
    }

    /**
     * FTS5 interpreta caracteres como `"`, `*`, `:`, `-` e parênteses como
     * sintaxe de consulta (frases, prefixo, coluna específica, exclusão...).
     * Para uma busca "livre" de usuário digitando texto qualquer, cada termo
     * é envolvido em aspas duplas (tratado como texto literal) com um
     * `*` de prefixo fora das aspas — evita que uma consulta como
     * `"como fazer: parte 2"` vire um erro de sintaxe MATCH em vez de uma
     * busca, e ainda permite encontrar palavras incompletas.
     */
    private fun sanitizeMatchQuery(query: String): String =
        query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { term -> "\"${term.replace("\"", "\"\"")}\"*" }

    fun close() {
        runCatching { connection.close() }
            .onFailure { e -> PersistenceLog.warn("Falha ao fechar conexão do índice FTS5", e) }
    }

    companion object {
        /**
         * Abre a conexão JDBC crua compartilhada — usada por esta classe e
         * por `wikidesk.persistence.maintenance.SqlDelightDatabaseMaintenanceService`
         * (ver `wikidesk.persistence.PersistenceContainer`) — sobre o mesmo
         * arquivo de banco da conexão principal do SQLDelight.
         *
         * `foreign_keys` é ligado aqui explicitamente porque é uma
         * configuração por conexão, não persistida no arquivo (ao contrário
         * do `journal_mode = WAL`, já ativo desde que
         * `wikidesk.persistence.database.DatabaseFactory` abriu o banco pela
         * primeira vez) — sem isso, um `DELETE` rodado nesta conexão (ver
         * `SqlDelightDatabaseMaintenanceService.clearDocumentIndex`) não
         * dispararia os `ON DELETE CASCADE` do schema.
         */
        fun open(databaseFile: File): Fts5SearchIndex {
            val connection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            return Fts5SearchIndex(connection)
        }
    }
}
