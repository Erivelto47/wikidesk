package wikidesk.mermaid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.datlag.kcef.KCEF
import wikidesk.platform.kcefBundleDirectory
import wikidesk.platform.kcefCacheDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Estado observável do ciclo de vida do mecanismo de renderização Mermaid (Chromium embutido via KCEF). */
sealed class MermaidRuntimeState {
    data object Idle : MermaidRuntimeState()
    data class Downloading(val percent: Float) : MermaidRuntimeState()
    data object Ready : MermaidRuntimeState()
    data class Error(val message: String) : MermaidRuntimeState()
    data object RestartRequired : MermaidRuntimeState()
}

/**
 * Envolve o ciclo de vida do KCEF (Chromium real embutido), usado só para
 * renderizar diagramas Mermaid com o mermaid.js de verdade (necessário para
 * ter zoom, exportação SVG/PNG e números de linha de erro reais — coisas que
 * dependem do parser/rendererdo próprio Mermaid, impossíveis de replicar
 * fielmente sem executar o JS original).
 *
 * A inicialização baixa/instala um bundle nativo do Chromium na primeira
 * execução (dezenas a centenas de MB), então só é disparada sob demanda —
 * quando o primeiro bloco Mermaid de um documento é exibido — em vez de
 * sempre na abertura do app. [ensureInitialized] é seguro para chamar de
 * múltiplos blocos Mermaid simultâneos: só a primeira chamada de fato inicia
 * o KCEF, as demais apenas observam o mesmo [state].
 */
object MermaidRuntime {

    /**
     * Release do JetBrainsRuntime de onde o pacote nativo do JCEF é baixado,
     * **pinado de propósito** em vez de usar o padrão do KCEF (que busca o
     * `releases/latest` do GitHub). Sem o pin, o binário nativo baixado
     * (ex.: CEF 147, de 2026) fica anos à frente das classes Java
     * (`org.cef.*`) embutidas no KCEF 2024.04.20.3 usado pela
     * compose-webview-multiplatform 2.0.1 — o descompasso de JNI gera
     * `NoSuchMethodError`/`ClassNotFoundException` nos callbacks e páginas
     * que nunca carregam (`ERR_ABORTED`), podendo até derrubar a JVM.
     * Esta tag é da mesma época dos bindings (é a mesma documentada no
     * README.desktop.md da própria lib), garantindo compatibilidade.
     */
    private const val PINNED_JBR_RELEASE = "jbr-release-17.0.10b1087.23"

    private val initStarted = AtomicBoolean(false)

    var state: MermaidRuntimeState by mutableStateOf(MermaidRuntimeState.Idle)
        private set

    suspend fun ensureInitialized() {
        if (!initStarted.compareAndSet(false, true)) return

        state = MermaidRuntimeState.Downloading(0f)
        withContext(Dispatchers.IO) {
            // Saneamento do estado em disco antes de inicializar o motor
            // nativo (nada disso é recuperável depois que o CEF começa a
            // subir — falhas viram crash nativo da JVM inteira, sem
            // stacktrace Kotlin):
            // 1. bundle de uma release diferente da pinada (ex.: baixado
            //    antes do pin existir) → apagado para forçar download da
            //    versão compatível com os bindings Java;
            // 2. bundle incompleto (download interrompido) → apagado;
            // 3. layout "cef_server" aninhado sem o layout achatado que o
            //    loader nativo procura → reparado com links simbólicos;
            // 4. locks órfãos do Chromium no cache (processo anterior morto
            //    abruptamente) → removidos, senão o navegador recusa abrir
            //    ("profile appears to be in use").
            runCatching { repairOrCleanUpBundle(kcefBundleDirectory()) }
            runCatching { cleanUpStaleCacheLocks(kcefCacheDirectory()) }

            runCatching {
                KCEF.init(
                    builder = {
                        installDir(kcefBundleDirectory())
                        download {
                            github {
                                release(PINNED_JBR_RELEASE)
                            }
                        }
                        progress {
                            onDownloading { percent ->
                                state = MermaidRuntimeState.Downloading(percent.coerceAtLeast(0f))
                            }
                            onInitialized {
                                writeReleaseMarker(kcefBundleDirectory())
                                state = MermaidRuntimeState.Ready
                            }
                        }
                        settings {
                            cachePath = kcefCacheDirectory().absolutePath
                        }
                    },
                    onError = { throwable ->
                        state = MermaidRuntimeState.Error(
                            throwable?.message ?: "Falha ao iniciar o mecanismo de diagramas."
                        )
                    },
                    onRestartRequired = {
                        state = MermaidRuntimeState.RestartRequired
                    }
                )
            }.onFailure { throwable ->
                state = MermaidRuntimeState.Error(throwable.message ?: "Falha ao iniciar o mecanismo de diagramas.")
            }
        }
    }

    /** Deve ser chamado uma vez ao fechar o app (ver `Main.kt`); seguro mesmo se o KCEF nunca foi inicializado. */
    fun dispose() {
        if (initStarted.get()) {
            runCatching { KCEF.disposeBlocking() }
        }
    }

    /**
     * Repara o layout de pastas do bundle nativo do Chromium quando o KCEF
     * baixou o formato "cef_server" (aninhado) da JetBrains em vez do layout
     * clássico "achatado" que o loader nativo desta versão espera — ver
     * comentário em [ensureInitialized]. Se nem o layout achatado nem o
     * aninhado tiverem o binário principal, apaga a pasta para forçar um
     * download limpo (provável instalação anterior incompleta/corrompida).
     */
    private fun repairOrCleanUpBundle(installDir: File) {
        if (!installDir.exists()) return
        val hasAnyContent = installDir.listFiles()?.isNotEmpty() == true
        if (!hasAnyContent) return

        // Instalação COMPLETA (tem o install.lock do KCEF) de uma release
        // diferente da pinada — ex.: baixada antes do pin existir. Os
        // bindings Java não batem com esse binário nativo, então apaga tudo
        // para forçar o download da release certa. (O marcador é gravado só
        // após uma inicialização bem-sucedida, em [writeReleaseMarker];
        // instalações incompletas nem têm install.lock e o próprio KCEF já
        // as re-baixa.)
        val markerFile = File(installDir, RELEASE_MARKER_FILE)
        if (File(installDir, "install.lock").exists() &&
            (!markerFile.exists() || markerFile.readText().trim() != PINNED_JBR_RELEASE)
        ) {
            installDir.deleteRecursively()
            return
        }

        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (!osName.contains("mac")) {
            // O problema do layout "cef_server" é específico do bundle macOS
            // da JetBrains; em Linux/Windows só valida se o binário existe.
            val coreBinaryExists = if (osName.contains("win")) {
                File(installDir, "libcef.dll").exists()
            } else {
                File(installDir, "libcef.so").exists()
            }
            if (!coreBinaryExists) installDir.deleteRecursively()
            return
        }

        val flatFrameworks = File(installDir, "Frameworks")
        val flatFramework = File(flatFrameworks, "Chromium Embedded Framework.framework")
        val flatHelperApp = File(flatFrameworks, "jcef Helper.app")

        if (flatFramework.exists()) return // já no layout esperado (ou já reparado antes)

        val nestedFrameworks = File(flatFrameworks, "cef_server.app/Contents/Frameworks")
        val nestedFramework = File(nestedFrameworks, "Chromium Embedded Framework.framework")
        val nestedHelperApp = File(nestedFrameworks, "jcef Helper.app")

        if (nestedFramework.exists()) {
            linkIfMissing(nestedFramework, flatFramework)
            linkIfMissing(nestedHelperApp, flatHelperApp)
            return
        }

        // Nem o layout achatado nem o aninhado têm o binário principal —
        // download anterior claramente incompleto/corrompido.
        installDir.deleteRecursively()
    }

    private fun linkIfMissing(target: File, linkPath: File) {
        if (!target.exists() || linkPath.exists()) return
        runCatching {
            java.nio.file.Files.createSymbolicLink(linkPath.toPath(), target.toPath())
        }
    }

    private const val RELEASE_MARKER_FILE = ".wikidesk-jcef-release"

    /** Grava qual release do JCEF este bundle contém, para invalidação automática quando o pin mudar. */
    private fun writeReleaseMarker(installDir: File) {
        runCatching {
            installDir.mkdirs()
            File(installDir, RELEASE_MARKER_FILE).writeText(PINNED_JBR_RELEASE)
        }
    }

    /**
     * Remove locks de "instância única" deixados pelo Chromium quando o
     * processo anterior morreu sem encerrar direito — com eles presentes, o
     * navegador embutido recusa usar o profile ("appears to be in use") e
     * nenhuma página carrega.
     */
    private fun cleanUpStaleCacheLocks(cacheDir: File) {
        if (!cacheDir.exists()) return
        listOf("SingletonLock", "SingletonSocket", "SingletonCookie").forEach { name ->
            val lock = File(cacheDir, name)
            if (lock.exists()) {
                runCatching { lock.delete() }
            }
        }
    }
}

/** Escapa uma string Kotlin para uso segura como literal de string dentro de um script JS gerado dinamicamente. */
fun encodeJsStringLiteral(value: String): String {
    val sb = StringBuilder(value.length + 16)
    sb.append('"')
    for (c in value) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            ' ' -> sb.append("\\u2028")
            ' ' -> sb.append("\\u2029")
            else -> sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}

/**
 * Alguns backends de WebView devolvem o valor de `evaluateJavaScript` já como
 * uma string JS "impressa" (ou seja, uma string JSON entre aspas e com
 * escapes), em vez do texto cru. Esta função tenta detectar e desfazer esse
 * empacotamento antes do [MiniJson] processar o resultado.
 */
fun unwrapJsEvalResult(raw: String): String {
    var s = raw.trim()
    if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
        s = s.substring(1, s.length - 1)
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
    return s
}
