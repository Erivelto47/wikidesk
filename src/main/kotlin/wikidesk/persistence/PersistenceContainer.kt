package wikidesk.persistence

import wikidesk.persistence.database.DatabaseFactory
import wikidesk.persistence.database.DatabasePathResolver
import wikidesk.persistence.database.WikiDeskDatabase
import wikidesk.persistence.maintenance.DatabaseMaintenanceService
import wikidesk.persistence.maintenance.SqlDelightDatabaseMaintenanceService
import wikidesk.persistence.repository.DocumentIndexRepository
import wikidesk.persistence.repository.GitStateRepository
import wikidesk.persistence.repository.SettingsRepository
import wikidesk.persistence.repository.SqlDelightDocumentIndexRepository
import wikidesk.persistence.repository.SqlDelightGitStateRepository
import wikidesk.persistence.repository.SqlDelightSettingsRepository
import wikidesk.persistence.repository.SqlDelightWikiRepository
import wikidesk.persistence.repository.WikiRepository
import wikidesk.persistence.search.Fts5SearchIndex
import wikidesk.persistence.search.RelationalLikeSearchIndex
import wikidesk.persistence.search.SearchIndex
import java.io.File

/**
 * Composição manual de toda a camada de persistência — sem framework de
 * injeção de dependências, no mesmo estilo já usado no resto do projeto
 * (`wikidesk.git.GitClient`, `wikidesk.workspace.WorkspaceScanner`: objetos
 * e classes construídos e ligados diretamente, sem Koin/Dagger/Hilt).
 *
 * Deve ser criado uma única vez por execução do app (ver `Main.kt`), guardado
 * em `wikidesk.ui.app.AppState`, e fechado com [close] só ao encerrar o
 * processo — nunca recriado a cada tela ou operação.
 */
class PersistenceContainer private constructor(
    val database: WikiDeskDatabase,
    val searchIndex: SearchIndex,
    val wikiRepository: WikiRepository,
    val gitStateRepository: GitStateRepository,
    val settingsRepository: SettingsRepository,
    val documentIndexRepository: DocumentIndexRepository,
    val maintenanceService: DatabaseMaintenanceService,
    private val fts: Fts5SearchIndex
) {

    /** Fecha a conexão JDBC crua compartilhada (FTS5/manutenção). Chamado uma única vez, ao encerrar o app. */
    fun close() {
        fts.close()
    }

    companion object {
        /** Composição usando a localização padrão do banco desta instalação (ver [DatabasePathResolver]). */
        fun create(): PersistenceContainer = createAt(DatabasePathResolver.databaseFile())

        /**
         * Como [create], mas nunca lança: se o banco na localização padrão
         * não puder ser aberto ou migrado (arquivo corrompido, migração
         * falhou — ver [wikidesk.persistence.database.DatabaseMigrationException]),
         * a sessão atual cai para um banco temporário descartável em vez de
         * impedir o app de abrir. O arquivo original não é tocado nesse
         * caminho de erro (ver `DatabaseFactory`) — só não é usado nesta
         * sessão. Chamada usada por `Main.kt`; [create] continua disponível
         * para quem preferir que a falha seja explícita (ex.: testes).
         */
        fun createOrFallback(): PersistenceContainer = try {
            create()
        } catch (e: Exception) {
            PersistenceLog.error(
                "Não foi possível abrir o banco de dados padrão — iniciando esta sessão com um banco " +
                    "temporário (sem persistência entre execuções). O arquivo original, se existir, não foi alterado.",
                e
            )
            val fallbackFile = File.createTempFile("wikidesk-fallback-", ".db").apply { deleteOnExit() }
            createAt(fallbackFile)
        }

        /**
         * Composição apontando para um arquivo específico — usada tanto pelo
         * app real (via [create]) quanto pelos testes, com um arquivo
         * temporário descartável.
         */
        fun createAt(databaseFile: File): PersistenceContainer {
            val database = DatabaseFactory.createAt(databaseFile)

            // A escolha entre FTS5 e o fallback relacional acontece uma
            // única vez aqui, na composição — ver `wikidesk.persistence.
            // search.SearchIndex` para o porquê de nada mais no app precisar
            // saber qual das duas está em uso.
            val fts = Fts5SearchIndex.open(databaseFile)
            val searchIndex: SearchIndex = if (fts.isFullTextAvailable) fts else RelationalLikeSearchIndex(database)

            return PersistenceContainer(
                database = database,
                searchIndex = searchIndex,
                wikiRepository = SqlDelightWikiRepository(database),
                gitStateRepository = SqlDelightGitStateRepository(database),
                settingsRepository = SqlDelightSettingsRepository(database),
                documentIndexRepository = SqlDelightDocumentIndexRepository(database, searchIndex),
                maintenanceService = SqlDelightDatabaseMaintenanceService(databaseFile, fts.connection),
                fts = fts
            )
        }
    }
}
