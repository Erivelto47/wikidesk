package wikidesk.platform

import java.io.File
import javax.swing.JFileChooser

/**
 * Abre o seletor de pastas nativo do sistema operacional para escolher um
 * novo workspace. Fica isolado em `wikidesk.platform` porque é a única
 * parte do app que depende diretamente de AWT/Swing em vez de Compose.
 *
 * @return o caminho absoluto escolhido, ou `null` se o usuário cancelou.
 */
fun pickWorkspaceDirectory(initialPath: String? = null): String? {
    val chooser = JFileChooser(initialPath)
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "Selecionar pasta de documentação"
    chooser.isAcceptAllFileFilterUsed = false

    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

/**
 * Abre o seletor nativo de "salvar arquivo", usado para exportar um diagrama
 * Mermaid como SVG ou PNG.
 *
 * @param suggestedName nome de arquivo pré-preenchido (com extensão), ex.: "diagrama.svg".
 * @return o caminho absoluto escolhido, ou `null` se o usuário cancelou.
 */
fun pickSaveFile(suggestedName: String): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Exportar diagrama"
    chooser.selectedFile = File(suggestedName)

    val result = chooser.showSaveDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

/** Abre uma URL externa no navegador padrão do sistema, se suportado. */
fun openExternalUrl(url: String) {
    runCatching {
        if (java.awt.Desktop.isDesktopSupported()) {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
            }
        }
    }
}
