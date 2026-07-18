package wikidesk.persistence.repository

import kotlinx.coroutines.runBlocking
import wikidesk.persistence.model.AppTheme
import wikidesk.persistence.withTempPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {

    @Test
    fun `banco novo carrega valores padrao`() = withTempPersistence { persistence ->
        runBlocking {
            val settings = persistence.settingsRepository.loadSettings()

            assertEquals(AppTheme.SYSTEM, settings.theme)
            assertEquals(1440, settings.windowWidth)
            assertEquals(900, settings.windowHeight)
            assertEquals(272, settings.sidebarWidth)
            assertNull(settings.lastOpenedWikiId)
        }
    }

    @Test
    fun `tema gravado e persistido e lido de volta`() = withTempPersistence { persistence ->
        runBlocking {
            persistence.settingsRepository.updateTheme(AppTheme.DARK)

            assertEquals(AppTheme.DARK, persistence.settingsRepository.loadSettings().theme)
        }
    }

    @Test
    fun `janela sem posicao salva volta como nula ao carregar`() = withTempPersistence { persistence ->
        runBlocking {
            persistence.settingsRepository.updateWindowBounds(width = 1600, height = 1000, x = 100, y = 50, maximized = false)
            assertEquals(100, persistence.settingsRepository.loadSettings().windowPositionX)

            persistence.settingsRepository.updateWindowBounds(width = 1600, height = 1000, x = null, y = null, maximized = true)

            val settings = persistence.settingsRepository.loadSettings()
            assertNull(settings.windowPositionX)
            assertNull(settings.windowPositionY)
            assertEquals(true, settings.windowMaximized)
        }
    }

    @Test
    fun `resetToDefaults restaura todos os valores padrao`() = withTempPersistence { persistence ->
        runBlocking {
            persistence.settingsRepository.updateTheme(AppTheme.DARK)
            persistence.settingsRepository.updateSidebarWidth(350)
            persistence.settingsRepository.updateAutoFetch(enabled = true, intervalMinutes = 15)

            persistence.settingsRepository.resetToDefaults()

            val settings = persistence.settingsRepository.loadSettings()
            assertEquals(AppTheme.SYSTEM, settings.theme)
            assertEquals(272, settings.sidebarWidth)
            assertEquals(false, settings.autoFetchEnabled)
            assertEquals(30, settings.autoFetchIntervalMinutes)
        }
    }
}
