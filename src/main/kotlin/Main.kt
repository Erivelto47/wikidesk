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
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.multiplatform.webview.util.addTempDirectoryRemovalHook
import wikidesk.mermaid.MermaidRuntime
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
 */
fun main() = application {
    addTempDirectoryRemovalHook()

    val appState = remember { AppState() }
    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)

    Window(
        onCloseRequest = {
            MermaidRuntime.dispose()
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
