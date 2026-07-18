package wikidesk.persistence.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import wikidesk.persistence.PersistenceLog
import java.io.File
import java.sql.DriverManager

/**
 * Ponto único de abertura do banco SQLite do WikiDesk: resolve o caminho (ver
 * [DatabasePathResolver]), cria o schema em uma instalação nova ou migra um
 * banco existente para a versão mais recente, e devolve uma
 * [WikiDeskDatabase] já pronta para uso pelos repositórios.
 *
 * Chamada uma única vez por execução do app, a partir de
 * `wikidesk.persistence.PersistenceContainer` — nada aqui deve ser chamado a
 * cada operação (abrir/fechar conexão por query seria o oposto do que a spec
 * de persistência pede).
 */
object DatabaseFactory {

    /** Cria/abre o banco na localização padrão da instalação. */
    fun create(): WikiDeskDatabase = createAt(DatabasePathResolver.databaseFile())

    /**
     * Cria/abre o banco em [file]. Separada de [create] para que testes
     * possam apontar para um arquivo temporário sem tocar no diretório de
     * dados real do usuário (ver `wikidesk.persistence.database.
     * DatabaseFactoryTest`).
     */
    fun createAt(file: File): WikiDeskDatabase {
        file.parentFile?.mkdirs()
        val isNewDatabase = !file.exists()
        val targetVersion = WikiDeskDatabase.Schema.version

        if (!isNewDatabase) {
            val currentVersion = try {
                readUserVersion(file)
            } catch (e: Exception) {
                PersistenceLog.error(
                    "Não foi possível ler a versão do banco existente em ${file.absolutePath} — " +
                        "o arquivo pode estar corrompido ou não ser um banco SQLite válido. " +
                        "Nenhuma alteração foi feita nele.",
                    e
                )
                throw IllegalStateException(
                    "Banco de dados em ${file.absolutePath} parece corrompido ou inválido.", e
                )
            }

            when {
                currentVersion in 1 until targetVersion -> migrateWithBackup(file, currentVersion, targetVersion)

                currentVersion > targetVersion -> PersistenceLog.warn(
                    "Banco ${file.name} está na versão $currentVersion, mais nova que o schema desta build do " +
                        "app (v$targetVersion) — abrindo sem migrar. Provavelmente uma build mais antiga do app " +
                        "foi aberta depois de o banco já ter sido usado por uma build mais nova."
                )

                currentVersion == 0L -> PersistenceLog.warn(
                    "Banco ${file.name} já existia no disco mas não tinha PRAGMA user_version definido — " +
                        "tratando como já estando em v$targetVersion sem migrar, para não arriscar recriar ou " +
                        "perder dados de um arquivo que não foi criado por esta camada de persistência."
                )

                else -> Unit // já está na versão mais recente
            }
        }

        val driver = openDriver(file)
        if (isNewDatabase) {
            WikiDeskDatabase.Schema.create(driver)
            writeUserVersion(file, targetVersion)
            PersistenceLog.info("Banco criado em ${file.absolutePath} (schema v$targetVersion)")
        }

        return WikiDeskDatabase(driver)
    }

    /**
     * Abre o driver JDBC usado para todo o resto da vida do app, com as
     * pragmas de concorrência/integridade já configuradas na conexão:
     * `foreign_keys` (para que os `ON DELETE CASCADE` do schema realmente
     * funcionem — o SQLite não os aplica por padrão), `busy_timeout` (evita
     * `SQLITE_BUSY` imediato em vez de esperar um pouco por um lock
     * concorrente) e `journal_mode = WAL` (permite leituras concorrentes
     * durante uma escrita/indexação em lote).
     *
     * WAL é um modo persistido no arquivo do banco — uma vez ativado aqui,
     * continua valendo em conexões futuras (inclusive a conexão JDBC crua
     * usada por `Fts5SearchIndex`/`DatabaseMaintenanceService`) mesmo sem
     * repetir a pragma.
     */
    private fun openDriver(file: File): SqlDriver {
        val config = SQLiteConfig().apply {
            enforceForeignKeys(true)
            setBusyTimeout(5_000)
            setJournalMode(SQLiteConfig.JournalMode.WAL)
        }
        return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", config.toProperties())
    }

    private fun readUserVersion(file: File): Long {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { resultSet ->
                    return if (resultSet.next()) resultSet.getLong(1) else 0L
                }
            }
        }
    }

    private fun writeUserVersion(file: File, version: Long) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = $version")
            }
        }
    }

    /**
     * Migra [file] de [oldVersion] para [newVersion] sem nunca escrever
     * diretamente no arquivo original até a migração inteira ter dado certo:
     * ela roda em uma cópia de trabalho descartável (`*.migrating-<ts>`); só
     * quando termina com sucesso é que o arquivo original é substituído por
     * essa cópia já migrada. Além disso, uma cópia de segurança adicional do
     * estado pré-migração (`*.bak-v<versão>-<ts>`) fica no disco mesmo em
     * caso de sucesso — não é removida automaticamente, para o usuário
     * sempre ter como voltar atrás manualmente se notar algo errado depois.
     *
     * Se qualquer etapa falhar, a cópia de trabalho é descartada, o arquivo
     * original permanece intocado, o erro é logado com detalhes suficientes
     * para diagnóstico, e uma [DatabaseMigrationException] é lançada — nunca
     * uma falha silenciosa.
     */
    private fun migrateWithBackup(file: File, oldVersion: Long, newVersion: Long) {
        val timestamp = System.currentTimeMillis()
        val backupFile = File(file.parentFile, "${file.name}.bak-v$oldVersion-$timestamp")
        val workingCopy = File(file.parentFile, "${file.name}.migrating-$timestamp")

        try {
            file.copyTo(backupFile, overwrite = false)
            file.copyTo(workingCopy, overwrite = false)

            val migrationDriver = JdbcSqliteDriver("jdbc:sqlite:${workingCopy.absolutePath}")
            try {
                WikiDeskDatabase.Schema.migrate(migrationDriver, oldVersion, newVersion)
            } finally {
                migrationDriver.close()
            }
            writeUserVersion(workingCopy, newVersion)

            if (!workingCopy.renameTo(file)) {
                // renameTo pode falhar entre filesystems diferentes (bind
                // mounts, volumes distintos) — cópia como alternativa.
                workingCopy.copyTo(file, overwrite = true)
                workingCopy.delete()
            }

            PersistenceLog.info(
                "Banco migrado de v$oldVersion para v$newVersion em ${file.name} " +
                    "(backup de segurança: ${backupFile.name})"
            )
        } catch (e: Exception) {
            workingCopy.delete()
            PersistenceLog.error(
                "Falha ao migrar banco de v$oldVersion para v$newVersion em ${file.absolutePath} — " +
                    "banco original preservado sem alterações, backup em ${backupFile.absolutePath}",
                e
            )
            throw DatabaseMigrationException(file, backupFile, oldVersion, newVersion, e)
        }
    }
}
