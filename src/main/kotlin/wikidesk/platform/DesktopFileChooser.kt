package wikidesk.platform

import com.formdev.flatlaf.util.SystemFileChooser
import java.io.File

/**
 * Abre o seletor de pastas nativo do sistema operacional para escolher um
 * novo workspace. Fica isolado em `wikidesk.platform` porque é a única
 * parte do app que depende diretamente de AWT/Swing em vez de Compose.
 *
 * EXPERIMENTAL (branch `experiment/native-file-chooser`): usa
 * [SystemFileChooser] (FlatLaf) em vez de `javax.swing.JFileChooser` puro —
 * chama o diálogo nativo de verdade (NSOpenPanel/Win32/GTK 3 real) em vez do
 * Swing repintando um tema, evitando um bug antigo do JFileChooser sob o
 * look-and-feel GTK do Linux que duplicava o último segmento do caminho
 * selecionado (JDK-5073778). Cai sozinho de volta para o comportamento do
 * JFileChooser se o GTK 3 não estiver presente no sistema (ver doc do
 * FlatLaf) — API é a mesma, então esta função não precisou mudar de forma.
 *
 * @return o caminho absoluto escolhido, ou `null` se o usuário cancelou.
 */
fun pickWorkspaceDirectory(initialPath: String? = null): String? {
    val chooser = SystemFileChooser(initialPath)
    chooser.fileSelectionMode = SystemFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "Selecionar pasta de documentação"
    chooser.isAcceptAllFileFilterUsed = false

    val result = chooser.showOpenDialog(null)
    return if (result == SystemFileChooser.APPROVE_OPTION) {
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
    val chooser = SystemFileChooser()
    chooser.dialogTitle = "Exportar diagrama"
    chooser.selectedFile = File(suggestedName)

    val result = chooser.showSaveDialog(null)
    return if (result == SystemFileChooser.APPROVE_OPTION) {
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
