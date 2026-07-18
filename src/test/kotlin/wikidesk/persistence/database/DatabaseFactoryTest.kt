package wikidesk.persistence.database

import wikidesk.persistence.createTempDatabaseFile
import wikidesk.persistence.deleteDatabaseFiles
import wikidesk.persistence.mapper.mapWikiRow
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseFactoryTest {

    @Test
    fun `cria banco novo com schema na versao mais recente`() {
        val file = createTempDatabaseFile()
        try {
            val database = DatabaseFactory.createAt(file)
            assertTrue(file.exists(), "Arquivo do banco deveria ter sido criado")
            // Não deveria lançar — tabela existe e está vazia.
            assertEquals(emptyList(), database.wikiQueries.selectActive(mapper = ::mapWikiRow).executeAsList())

            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA user_version").use { rs ->
                        rs.next()
                        assertEquals(WikiDeskDatabase.Schema.version, rs.getLong(1))
                    }
                }
            }
        } finally {
            deleteDatabaseFiles(file)
        }
    }

    @Test
    fun `reabrir o banco mantem os dados gravados`() {
        val file = createTempDatabaseFile()
        try {
            val first = DatabaseFactory.createAt(file)
            // insertWiki agora termina em `RETURNING id` (ver Wiki.sq), então
            // o SQLDelight a gera como uma ExecutableQuery<Long> preguiçosa —
            // só a chamada por si só não executa nada; é preciso um método
            // terminal como executeAsOne() para de fato rodar o INSERT.
            first.wikiQueries.insertWiki(
                name = "Minha Wiki",
                rootPath = "/tmp/minha-wiki",
                normalizedPath = "/tmp/minha-wiki",
                sourceType = "LOCAL_FOLDER",
                remoteUrl = null,
                defaultBranch = null,
                createdAt = 1L,
                updatedAt = 1L,
                lastOpenedAt = null,
                isActive = 1L
            ).executeAsOne()

            val second = DatabaseFactory.createAt(file)
            val rows = second.wikiQueries.selectActive(mapper = ::mapWikiRow).executeAsList()
            assertEquals(1, rows.size)
            assertEquals("Minha Wiki", rows.single().name)
        } finally {
            deleteDatabaseFiles(file)
        }
    }

    @Test
    fun `migra um banco v1 preservando dados e aplicando o schema mais recente`() {
        val file = createTempDatabaseFile()
        try {
            createHandCraftedV1Database(file)

            val migrated = DatabaseFactory.createAt(file)

            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA user_version").use { rs ->
                        rs.next()
                        assertEquals(2L, rs.getLong(1), "user_version deveria ter avançado para v2 após a migração")
                    }
                    statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'wiki_source_type_idx'"
                    ).use { rs ->
                        assertTrue(rs.next(), "Índice adicionado na migração 1.sqm deveria existir após migrar")
                    }
                }
            }

            val rows = migrated.wikiQueries.selectActive(mapper = ::mapWikiRow).executeAsList()
            assertEquals(1, rows.size, "A wiki gravada antes da migração deveria continuar presente")
            assertEquals("Wiki Pré-Migração", rows.single().name)
        } finally {
            deleteDatabaseFiles(file)
        }
    }

    @Test
    fun `banco corrompido lanca erro claro e nao e sobrescrito`() {
        val file = createTempDatabaseFile()
        try {
            file.writeText("isto claramente não é um arquivo SQLite válido — só texto qualquer para simular corrupção")
            val originalBytes = file.readBytes()

            assertFailsWith<IllegalStateException> { DatabaseFactory.createAt(file) }

            assertTrue(file.readBytes().contentEquals(originalBytes), "Arquivo corrompido não deveria ter sido alterado")
        } finally {
            deleteDatabaseFiles(file)
        }
    }

    /**
     * Monta, com SQL cru (não via SQLDelight), um banco no estado exato da
     * v1 — antes do índice adicionado em `1.sqm` — com uma linha em `wiki`,
     * para testar a migração de ponta a ponta sem depender de já ter existido
     * uma versão anterior real do app para gerá-lo.
     */
    private fun createHandCraftedV1Database(file: File) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE wiki (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        rootPath TEXT NOT NULL,
                        normalizedPath TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        remoteUrl TEXT,
                        defaultBranch TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastOpenedAt INTEGER,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                statement.execute("CREATE UNIQUE INDEX wiki_normalized_path_idx ON wiki(normalizedPath)")
                statement.execute("CREATE INDEX wiki_is_active_idx ON wiki(isActive)")

                statement.execute(
                    """
                    CREATE TABLE wiki_git_state (
                        wikiId INTEGER NOT NULL PRIMARY KEY REFERENCES wiki(id) ON DELETE CASCADE,
                        currentBranch TEXT,
                        headCommit TEXT,
                        remoteHeadCommit TEXT,
                        hasLocalChanges INTEGER,
                        aheadCount INTEGER,
                        behindCount INTEGER,
                        lastFetchAt INTEGER,
                        lastScanAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                statement.execute(
                    "CREATE TABLE app_settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
                )

                statement.execute(
                    """
                    CREATE TABLE document (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        wikiId INTEGER NOT NULL REFERENCES wiki(id) ON DELETE CASCADE,
                        relativePath TEXT NOT NULL,
                        title TEXT NOT NULL,
                        contentHash TEXT NOT NULL,
                        fileSize INTEGER NOT NULL,
                        modifiedAt INTEGER NOT NULL,
                        indexedAt INTEGER NOT NULL,
                        deletedAt INTEGER
                    )
                    """.trimIndent()
                )
                statement.execute("CREATE UNIQUE INDEX document_wiki_path_idx ON document(wikiId, relativePath)")
                statement.execute("CREATE INDEX document_wiki_id_idx ON document(wikiId)")
                statement.execute("CREATE INDEX document_deleted_at_idx ON document(deletedAt)")

                statement.execute(
                    """
                    CREATE TABLE document_chunk (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        documentId INTEGER NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                        chunkIndex INTEGER NOT NULL,
                        headingPath TEXT,
                        content TEXT NOT NULL,
                        contentHash TEXT NOT NULL,
                        tokenCount INTEGER,
                        embeddingModel TEXT,
                        embeddingDimension INTEGER,
                        embeddingBlob BLOB,
                        embeddedAt INTEGER
                    )
                    """.trimIndent()
                )
                statement.execute("CREATE UNIQUE INDEX document_chunk_doc_index_idx ON document_chunk(documentId, chunkIndex)")

                statement.execute(
                    "INSERT INTO wiki (name, rootPath, normalizedPath, sourceType, createdAt, updatedAt, isActive) " +
                        "VALUES ('Wiki Pré-Migração', '/tmp/pre-migracao', '/tmp/pre-migracao', 'LOCAL_FOLDER', 1, 1, 1)"
                )

                statement.execute("PRAGMA user_version = 1")
            }
        }
    }
}
