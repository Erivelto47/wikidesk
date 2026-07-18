package wikidesk.persistence.repository

import kotlinx.coroutines.flow.Flow
import wikidesk.persistence.model.AppSettings
import wikidesk.persistence.model.AppTheme

/**
 * API tipada sobre `app_settings` (chave/valor) — o resto do app nunca deve
 * ler/escrever chaves diretamente na tabela, só através desta interface (ver
 * `wikidesk.persistence.model.AppSettings` para os campos e seus padrões).
 *
 * Implementação SQLite em [SqlDelightSettingsRepository].
 */
interface SettingsRepository {

    /**
     * Carrega as preferências atuais, com valores padrão para qualquer chave
     * ainda não gravada (instalação nova) ou com valor que não pôde ser
     * interpretado (ex.: uma versão futura do app gravou um formato que esta
     * versão não entende) — nunca lança exceção por causa de uma preferência
     * malformada ou desconhecida.
     */
    suspend fun loadSettings(): AppSettings

    /** Emite as preferências atuais e de novo a cada alteração. */
    fun observeSettings(): Flow<AppSettings>

    suspend fun updateTheme(theme: AppTheme)

    suspend fun updateLastOpenedWiki(wikiId: Long?)

    suspend fun updateWindowBounds(width: Int, height: Int, x: Int?, y: Int?, maximized: Boolean)

    suspend fun updateSidebarWidth(width: Int)

    suspend fun updateAutoFetch(enabled: Boolean, intervalMinutes: Int)

    /** Apaga todas as preferências gravadas — próxima leitura volta a devolver [AppSettings] com todos os padrões. */
    suspend fun resetToDefaults()
}
