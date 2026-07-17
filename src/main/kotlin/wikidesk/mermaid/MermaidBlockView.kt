package wikidesk.mermaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import wikidesk.domain.MarkdownBlock
import wikidesk.platform.pickSaveFile
import wikidesk.ui.components.CodeBlock
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Base64

private const val POLL_INTERVAL_MS = 150L
private const val POLL_TIMEOUT_MS = 8000L
private val ZOOM_LEVELS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Altura fixa da área de visualização (diagrama OU código-fonte), para que
 * alternar entre "Ver código" e "Ver diagrama" nunca mude o tamanho geral do
 * bloco — evitando o "salto" de layout no restante do documento a cada troca.
 * Conteúdo maior que essa altura rola dentro da própria área, em vez de
 * esticá-la.
 */
private val DIAGRAM_AREA_HEIGHT = 360.dp

/**
 * Renderiza um bloco de diagrama Mermaid usando um WebView embutido (Chromium
 * real via KCEF) rodando o mermaid.js bundled localmente/offline.
 *
 * Ver [MermaidRuntime] para o ciclo de vida do motor (inicializado sob
 * demanda) e [buildMermaidHarnessHtml] para o protocolo de comunicação
 * Kotlin<->JS (polling sobre `evaluateJavaScript`).
 *
 * Nota de layout: o WebView é um componente pesado (AWT/Swing) que a
 * plataforma Desktop do Compose sempre desenha por cima do conteúdo Compose,
 * então o fallback de erro nunca é sobreposto ao WebView — em vez disso, o
 * WebView é reduzido a altura zero (mas continua "montado", preservando seu
 * estado/canal de JS) e o fallback ocupa o espaço abaixo dele, em sequência.
 */
@Composable
fun MermaidBlockView(block: MarkdownBlock.Mermaid, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        MermaidRuntime.ensureInitialized()
    }

    var showSource by remember { mutableStateOf(false) }
    var zoomIndex by remember { mutableStateOf(ZOOM_LEVELS.indexOf(1f)) }
    var renderResult by remember(block.code) { mutableStateOf<MermaidRenderResult?>(null) }
    var isRendering by remember(block.code) { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    val runtimeState = MermaidRuntime.state
    val html = remember { buildMermaidHarnessHtml() }
    val webViewState = rememberWebViewStateWithHTMLData(data = html)
    val navigator = rememberWebViewNavigator()

    // Dispara a renderização assim que o motor estiver pronto e a página
    // (harness) tiver terminado de carregar; refaz quando o código do bloco
    // muda (o harness em si não recarrega — só chamamos startRender de novo).
    LaunchedEffect(runtimeState, block.code) {
        if (runtimeState !is MermaidRuntimeState.Ready) return@LaunchedEffect
        snapshotFlow { webViewState.loadingState }.first { it is LoadingState.Finished }

        isRendering = true
        renderResult = null

        // O `loadHtml` do backend desktop tem um atraso interno (~500ms)
        // entre o estado "Finished" (que vem da página em branco inicial) e
        // o carregamento do HTML de verdade — que, ao carregar, zera o
        // contexto JS. Disparar startRender cedo demais o faria rodar na
        // página errada e se perder. Então primeiro esperamos o harness
        // existir de fato na página atual, e só então renderizamos.
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var harnessReady = false
        while (System.currentTimeMillis() < deadline && !harnessReady) {
            var typeOf: String? = null
            navigator.evaluateJavaScript("typeof window.startRender") { typeOf = it }
            delay(POLL_INTERVAL_MS)
            harnessReady = unwrapJsEvalResult(typeOf.orEmpty()) == "function"
        }

        var result: MermaidRenderResult? = null
        if (harnessReady) {
            navigator.evaluateJavaScript("startRender(${encodeJsStringLiteral(block.code)})")

            while (System.currentTimeMillis() < deadline && result == null) {
                delay(POLL_INTERVAL_MS)
                var raw: String? = null
                navigator.evaluateJavaScript("getResult()") { raw = it }
                delay(30)
                val text = raw
                if (!text.isNullOrEmpty() && text != "\"\"" && text != "null") {
                    result = parseMermaidRenderResult(text)
                }
            }
        }

        renderResult = result ?: MermaidRenderResult.Failure(
            message = if (harnessReady) {
                "Tempo esgotado ao renderizar o diagrama."
            } else {
                "O navegador embutido não carregou a página de renderização."
            },
            line = null
        )
        isRendering = false
    }

    LaunchedEffect(zoomIndex, renderResult) {
        // Só depois de um render bem-sucedido: antes disso a página (e as
        // funções do harness) podem nem existir ainda — chamar setZoom em
        // about:blank gera erros de console inúteis no navegador embutido.
        if (renderResult is MermaidRenderResult.Success) {
            navigator.evaluateJavaScript("setZoom(${ZOOM_LEVELS[zoomIndex]})")
        }
    }

    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            delay(2600)
            exportMessage = null
        }
    }

    val successResult = renderResult as? MermaidRenderResult.Success
    val failureResult = renderResult as? MermaidRenderResult.Failure

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = colors.codeBorder, shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeaderBackground)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.langBadgeBackground)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(text = "MERMAID", style = AppTypography.codeBlockLabel, color = colors.langBadgeText)
            }

            Spacer(modifier = Modifier.weight(1f))

            exportMessage?.let {
                Text(text = it, style = AppTypography.hint, color = colors.textMuted)
            }

            if (showSource) {
                ToolbarTextButton(text = if (copied) "Copiado" else "Copiar") {
                    clipboardManager.setText(AnnotatedString(block.code))
                    copied = true
                }
            } else if (successResult != null) {
                ToolbarTextButton(text = "−", enabled = zoomIndex > 0) { if (zoomIndex > 0) zoomIndex-- }
                Text(
                    text = "${(ZOOM_LEVELS[zoomIndex] * 100).toInt()}%",
                    style = AppTypography.hint,
                    color = colors.textMuted
                )
                ToolbarTextButton(text = "+", enabled = zoomIndex < ZOOM_LEVELS.lastIndex) {
                    if (zoomIndex < ZOOM_LEVELS.lastIndex) zoomIndex++
                }
                ToolbarTextButton(text = "SVG") {
                    scope.launch { exportSvg(navigator) { exportMessage = it } }
                }
                ToolbarTextButton(text = "PNG") {
                    scope.launch { exportPng(navigator) { exportMessage = it } }
                }
            }

            ToolbarTextButton(text = if (showSource) "Ver diagrama" else "Ver código") {
                showSource = !showSource
            }
        }

        // Área de visualização (diagrama OU código): altura fixa e borda
        // própria, separando visualmente o conteúdo da barra de ferramentas
        // acima — e mantendo o tamanho do bloco estável nas duas visões.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DIAGRAM_AREA_HEIGHT)
                .border(width = 1.dp, color = colors.codeBorder)
        ) {
            // O WebView só passa a existir a partir do estado Ready (montá-lo
            // antes arriscaria o motor KCEF ainda não estar pronto), mas a
            // partir daí este é o ÚNICO ponto de chamada, sempre presente
            // enquanto o estado permanecer Ready — nunca é removido da
            // composição condicionalmente (só sua altura alterna entre 0 e o
            // tamanho total). Se ele saísse da composição, o navegador
            // embutido (e o diagrama já renderizado nele) seria destruído, e
            // ao voltar para "Ver diagrama" sobraria uma página em branco (o
            // render não re-executa sozinho, pois o LaunchedEffect é
            // chaveado pelo código do bloco, que não mudou).
            if (runtimeState is MermaidRuntimeState.Ready) {
                WebView(
                    state = webViewState,
                    navigator = navigator,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!showSource && successResult != null) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.height(0.dp)
                            }
                        )
                )
            }

            when {
                showSource -> {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        CodeBlock(language = "mermaid", code = block.code, embedded = true)
                    }
                }

                runtimeState is MermaidRuntimeState.Idle || runtimeState is MermaidRuntimeState.Downloading -> {
                    MermaidStatusMessage(
                        text = if (runtimeState is MermaidRuntimeState.Downloading) {
                            "Preparando mecanismo de diagramas… ${runtimeState.percent.toInt()}%"
                        } else {
                            "Preparando mecanismo de diagramas…"
                        }
                    )
                }

                runtimeState is MermaidRuntimeState.RestartRequired -> {
                    MermaidStatusMessage(text = "É necessário reiniciar o aplicativo para habilitar diagramas Mermaid.")
                }

                runtimeState is MermaidRuntimeState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        MermaidErrorFallback(message = runtimeState.message, line = null, code = block.code)
                    }
                }

                runtimeState is MermaidRuntimeState.Ready && failureResult != null -> {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        MermaidErrorFallback(message = failureResult.message, line = failureResult.line, code = block.code)
                    }
                }

                runtimeState is MermaidRuntimeState.Ready && successResult == null -> {
                    MermaidStatusMessage(text = if (isRendering) "Renderizando diagrama…" else "Preparando diagrama…")
                }
            }
        }
    }
}

@Composable
private fun ToolbarTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(5.dp))
            .background(colors.codeButtonBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = AppTypography.hint, color = if (enabled) colors.textMuted else colors.textFaint)
    }
}

@Composable
private fun MermaidStatusMessage(text: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = AppTypography.caption, color = colors.textMuted)
    }
}

@Composable
private fun MermaidErrorFallback(message: String, line: Int?, code: String) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", style = AppTypography.heading3, color = colors.calloutErrorAccent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (line != null) "Erro na linha $line: $message" else "Erro ao renderizar diagrama: $message",
                style = AppTypography.caption,
                color = colors.calloutErrorAccent
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        CodeBlock(language = "mermaid", code = code, highlightLine = line, embedded = true)
    }
}

private suspend fun exportSvg(navigator: WebViewNavigator, onMessage: (String) -> Unit) {
    var raw: String? = null
    navigator.evaluateJavaScript("getSvgSource()") { raw = it }
    val deadline = System.currentTimeMillis() + 3000
    while (raw == null && System.currentTimeMillis() < deadline) {
        delay(80)
    }
    val svg = unwrapJsEvalResult(raw.orEmpty())
    if (svg.isBlank()) {
        onMessage("Nada para exportar ainda.")
        return
    }
    val path = pickSaveFile("diagrama.svg") ?: return
    runCatching { File(path).writeText(svg, Charsets.UTF_8) }
        .onSuccess { onMessage("SVG exportado.") }
        .onFailure { onMessage("Falha ao exportar SVG.") }
}

private suspend fun exportPng(navigator: WebViewNavigator, onMessage: (String) -> Unit) {
    navigator.evaluateJavaScript("exportPng(2)")
    var raw: String? = null
    val deadline = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadline) {
        delay(150)
        navigator.evaluateJavaScript("getPngResult()") { raw = it }
        delay(60)
        val text = raw
        if (!text.isNullOrEmpty() && text != "null") break
    }
    val dataUrl = unwrapJsEvalResult(raw.orEmpty())
    val base64 = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
    if (base64.isBlank()) {
        onMessage("Falha ao gerar PNG.")
        return
    }
    val path = pickSaveFile("diagrama.png") ?: return
    runCatching {
        val bytes = Base64.getDecoder().decode(base64)
        File(path).writeBytes(bytes)
    }.onSuccess { onMessage("PNG exportado.") }
        .onFailure { onMessage("Falha ao exportar PNG.") }
}
