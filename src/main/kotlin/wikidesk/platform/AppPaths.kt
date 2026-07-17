package wikidesk.platform

import java.io.File

/**
 * Diretório raiz de dados do aplicativo. Segue a convenção de cada SO:
 * `~/Library/Application Support/WikiDesk` no macOS,
 * `$XDG_DATA_HOME/WikiDesk` (ou `~/.local/share/WikiDesk`) no Linux.
 */
private fun appSupportDirectory(): File {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").orEmpty().lowercase()

    return if (os.contains("mac")) {
        File(home, "Library/Application Support/WikiDesk")
    } else {
        File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "WikiDesk")
    }
}

/**
 * Destino padrão para clones de repositórios Git quando o usuário não
 * escolhe uma pasta específica (ver `wikidesk.git.defaultCloneDestination`).
 */
fun appDataDirectory(): File = File(appSupportDirectory(), "sources")

/**
 * Pasta onde o bundle nativo do Chromium embutido (KCEF) é instalado na
 * primeira execução — usado só para renderizar diagramas Mermaid
 * (ver `wikidesk.mermaid.MermaidRuntime`).
 */
fun kcefBundleDirectory(): File = File(appSupportDirectory(), "kcef-bundle")

/** Cache do Chromium embutido (KCEF) — cookies, cache de rede, etc. */
fun kcefCacheDirectory(): File = File(appSupportDirectory(), "kcef-cache")
