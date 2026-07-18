package wikidesk.persistence.model

/** Preferência de tema — `SYSTEM` segue o tema do sistema operacional (resolução fica na UI, não aqui). */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Todas as preferências tipadas do app, com seus valores padrão — o que
 * `wikidesk.persistence.repository.SettingsRepository.loadSettings()`
 * devolve mesmo em uma instalação nova, sem nenhuma linha ainda gravada em
 * `app_settings`.
 *
 * Adicionar um campo novo aqui é sempre compatível com bancos já existentes:
 * a chave correspondente simplesmente não existe ainda em `app_settings`,
 * então o valor padrão declarado aqui é o que é usado — não é necessária
 * nenhuma migração de schema (é chave/valor) nem tratamento especial no
 * carregamento.
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val lastOpenedWikiId: Long? = null,
    val windowWidth: Int = 1440,
    val windowHeight: Int = 900,
    val windowPositionX: Int? = null,
    val windowPositionY: Int? = null,
    val windowMaximized: Boolean = false,
    val sidebarWidth: Int = 272,
    val autoFetchEnabled: Boolean = false,
    val autoFetchIntervalMinutes: Int = 30
)
