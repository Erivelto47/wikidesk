import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.multiplatform.webview.util.addTempDirectoryRemovalHook
import kotlinx.coroutines.runBlocking
import wikidesk.mermaid.MermaidRuntime
import wikidesk.persistence.PersistenceContainer
import wikidesk.persistence.PersistenceLog
import wikidesk.ui.app.AppRoot
import wikidesk.ui.app.AppState

/**
 * Ponto de entrada do aplicativo desktop (macOS, Windows e Linux).
 *
 * Os atalhos globais (⌘K / Ctrl+K para busca, Esc para fechar a busca) são
 * tratados aqui, no nível da janela, porque é o único lugar com acesso a
 * todos os eventos de teclado antes de chegarem aos componentes internos.
 *
 * `addTempDirectoryRemovalHook()` e `MermaidRuntime.dispose()` cuidam do
 * ciclo de vida do Chromium embutido (KCEF, usado só para renderizar
 * diagramas Mermaid — ver `wikidesk.mermaid`).
 *
 * A camada de persistência local (ver `wikidesk.persistence`) é aberta uma
 * única vez aqui — `PersistenceContainer.createOrFallback()` nunca lança:
 * se o banco na localização padrão não puder ser aberto/migrado, a sessão
 * cai para um banco temporário em vez de recusar abrir o app (o arquivo
 * original, se existir, não é tocado nesse caso — ver `DatabaseFactory`).
 */
fun main() = application {
    addTempDirectoryRemovalHook()

    val persistence = remember { PersistenceContainer.createOrFallback() }

    // Leitura síncrona e única, antes da primeira composição da janela: o
    // tamanho/posição inicial de `WindowState` precisa ser conhecido no
    // instante em que ela é criada, não dá para "recriar" a janela depois de
    // uma leitura assíncrona sem um salto visual perceptível. É uma exceção
    // deliberada e pontual à regra geral de não fazer IO síncrono na UI —
    // mesmo espírito da leitura síncrona já feita em
    // `AppState.openScannedLocalSource` para pastas locais comuns.
    val initialSettings = remember { runBlocking { persistence.settingsRepository.loadSettings() } }

    val appState = remember { AppState(persistence) }
    val windowState = rememberWindowState(
        width = initialSettings.windowWidth.dp,
        height = initialSettings.windowHeight.dp,
        position = if (initialSettings.windowPositionX != null && initialSettings.windowPositionY != null) {
            WindowPosition(initialSettings.windowPositionX.dp, initialSettings.windowPositionY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
        placement = if (initialSettings.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
    )

    Window(
        onCloseRequest = {
            // Escrita síncrona e única no encerramento: garante que o
            // tamanho/posição final da janela seja persistido antes do
            // processo sair — uma escrita em segundo plano poderia ser
            // cancelada pelo `exitApplication()` logo em seguida.
            runCatching {
                runBlocking {
                    persistence.settingsRepository.updateWindowBounds(
                        width = windowState.size.width.value.toInt(),
                        height = windowState.size.height.value.toInt(),
                        x = windowState.position.takeIf { it.isSpecified }?.x?.value?.toInt(),
                        y = windowState.position.takeIf { it.isSpecified }?.y?.value?.toInt(),
                        maximized = windowState.placement == WindowPlacement.Maximized
                    )
                }
            }.onFailure { e -> PersistenceLog.warn("Falha ao salvar tamanho/posição da janela ao fechar", e) }

            MermaidRuntime.dispose()
            appState.dispose()
            exitApplication()
        },
        title = "WikiDesk",
        state = windowState,
        onKeyEvent = { event -> handleGlobalShortcut(event, appState) }
    ) {
        AppRoot(state = appState)
    }
}

private fun handleGlobalShortcut(event: KeyEvent, state: AppState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val isShortcutModifier = event.isMetaPressed || event.isCtrlPressed

    return when {
        isShortcutModifier && event.key == Key.K -> {
            state.openSearch()
            true
        }
        event.key == Key.Escape && state.searchOpen -> {
            state.closeSearch()
            true
        }
        isShortcutModifier && event.key == Key.B -> {
            state.toggleSidebar()
            true
        }
        else -> false
    }
}
