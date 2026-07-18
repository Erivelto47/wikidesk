package wikidesk.persistence.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import wikidesk.persistence.PersistenceLog
import wikidesk.persistence.database.WikiDeskDatabase
import wikidesk.persistence.model.AppSettings
import wikidesk.persistence.model.AppTheme

/** Chaves conhecidas de `app_settings` — únicas usadas por esta implementação; qualquer outra chave é ignorada ao carregar. */
private object SettingsKeys {
    const val THEME = "theme"
    const val LAST_OPENED_WIKI_ID = "lastOpenedWikiId"
    const val WINDOW_WIDTH = "windowWidth"
    const val WINDOW_HEIGHT = "windowHeight"
    const val WINDOW_POSITION_X = "windowPositionX"
    const val WINDOW_POSITION_Y = "windowPositionY"
    const val WINDOW_MAXIMIZED = "windowMaximized"
    const val SIDEBAR_WIDTH = "sidebarWidth"
    const val AUTO_FETCH_ENABLED = "autoFetchEnabled"
    const val AUTO_FETCH_INTERVAL_MINUTES = "autoFetchIntervalMinutes"
}

/**
 * Implementação SQLite do [SettingsRepository]. Cada preferência é uma linha
 * de `app_settings`; [loadSettings]/[observeSettings] leem todas de uma vez
 * e montam [AppSettings], usando o valor padrão do campo sempre que a chave
 * não existir ainda ou não puder ser interpretada como o tipo esperado — é
 * assim que uma instalação nova (tabela vazia) e uma chave desconhecida
 * escrita por uma versão futura do app são toleradas sem nenhum tratamento
 * especial: simplesmente não entram no mapa lido, e o padrão declarado em
 * [AppSettings] prevalece.
 */
class SqlDelightSettingsRepository(
    private val database: WikiDeskDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val queries = database.appSettingsQueries

    override suspend fun loadSettings(): AppSettings = withContext(ioDispatcher) {
        readAllAsMap().toAppSettings()
    }

    override fun observeSettings(): Flow<AppSettings> =
        queries.selectAll(mapper = { key, value, _ -> key to value })
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.toMap().toAppSettings() }

    override suspend fun updateTheme(theme: AppTheme): Unit = withContext(ioDispatcher) {
        write(SettingsKeys.THEME, theme.name)
    }

    override suspend fun updateLastOpenedWiki(wikiId: Long?): Unit = withContext(ioDispatcher) {
        if (wikiId == null) delete(SettingsKeys.LAST_OPENED_WIKI_ID) else write(SettingsKeys.LAST_OPENED_WIKI_ID, wikiId.toString())
    }

    override suspend fun updateWindowBounds(width: Int, height: Int, x: Int?, y: Int?, maximized: Boolean): Unit =
        withContext(ioDispatcher) {
            database.transaction {
                writeInTransaction(SettingsKeys.WINDOW_WIDTH, width.toString())
                writeInTransaction(SettingsKeys.WINDOW_HEIGHT, height.toString())
                if (x == null) deleteInTransaction(SettingsKeys.WINDOW_POSITION_X) else writeInTransaction(SettingsKeys.WINDOW_POSITION_X, x.toString())
                if (y == null) deleteInTransaction(SettingsKeys.WINDOW_POSITION_Y) else writeInTransaction(SettingsKeys.WINDOW_POSITION_Y, y.toString())
                writeInTransaction(SettingsKeys.WINDOW_MAXIMIZED, maximized.toString())
            }
        }

    override suspend fun updateSidebarWidth(width: Int): Unit = withContext(ioDispatcher) {
        write(SettingsKeys.SIDEBAR_WIDTH, width.toString())
    }

    override suspend fun updateAutoFetch(enabled: Boolean, intervalMinutes: Int): Unit = withContext(ioDispatcher) {
        database.transaction {
            writeInTransaction(SettingsKeys.AUTO_FETCH_ENABLED, enabled.toString())
            writeInTransaction(SettingsKeys.AUTO_FETCH_INTERVAL_MINUTES, intervalMinutes.toString())
        }
    }

    override suspend fun resetToDefaults(): Unit = withContext(ioDispatcher) {
        queries.deleteAll()
        PersistenceLog.info("Preferências restauradas para os valores padrão.")
    }

    private fun readAllAsMap(): Map<String, String> =
        queries.selectAll(mapper = { key, value, _ -> key to value }).executeAsList().toMap()

    private fun write(key: String, value: String) = writeInTransaction(key, value)

    private fun delete(key: String) = deleteInTransaction(key)

    private fun writeInTransaction(key: String, value: String) {
        queries.upsert(key = key, value = value, updatedAt = System.currentTimeMillis())
    }

    private fun deleteInTransaction(key: String) {
        queries.deleteByKey(key)
    }

    private fun Map<String, String>.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = this[SettingsKeys.THEME]?.let { raw -> runCatching { AppTheme.valueOf(raw) }.getOrNull() } ?: defaults.theme,
            lastOpenedWikiId = this[SettingsKeys.LAST_OPENED_WIKI_ID]?.toLongOrNull() ?: defaults.lastOpenedWikiId,
            windowWidth = this[SettingsKeys.WINDOW_WIDTH]?.toIntOrNull() ?: defaults.windowWidth,
            windowHeight = this[SettingsKeys.WINDOW_HEIGHT]?.toIntOrNull() ?: defaults.windowHeight,
            windowPositionX = this[SettingsKeys.WINDOW_POSITION_X]?.toIntOrNull() ?: defaults.windowPositionX,
            windowPositionY = this[SettingsKeys.WINDOW_POSITION_Y]?.toIntOrNull() ?: defaults.windowPositionY,
            windowMaximized = this[SettingsKeys.WINDOW_MAXIMIZED]?.toBooleanStrictOrNull() ?: defaults.windowMaximized,
            sidebarWidth = this[SettingsKeys.SIDEBAR_WIDTH]?.toIntOrNull() ?: defaults.sidebarWidth,
            autoFetchEnabled = this[SettingsKeys.AUTO_FETCH_ENABLED]?.toBooleanStrictOrNull() ?: defaults.autoFetchEnabled,
            autoFetchIntervalMinutes = this[SettingsKeys.AUTO_FETCH_INTERVAL_MINUTES]?.toIntOrNull() ?: defaults.autoFetchIntervalMinutes
        )
    }
}
