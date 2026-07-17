package wikidesk.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wikidesk.domain.FileEntry
import wikidesk.domain.FolderNode
import wikidesk.domain.MarkdownBlock
import wikidesk.domain.MarkdownDocument
import wikidesk.domain.SourceType
import wikidesk.git.GitFileChangeKind
import wikidesk.git.GitSourceStatus
import wikidesk.git.formatRelativeTime
import wikidesk.platform.pickWorkspaceDirectory
import wikidesk.ui.LocalOverlayVisible
import wikidesk.ui.components.AddSourceModal
import wikidesk.ui.components.DocumentContent
import wikidesk.ui.components.FileUiItem
import wikidesk.ui.components.FolderUiItem
import wikidesk.ui.components.GitStatusUiItem
import wikidesk.ui.components.SearchPalette
import wikidesk.ui.components.SearchResultUiItem
import wikidesk.ui.components.Sidebar
import wikidesk.ui.components.SourceUiItem
import wikidesk.ui.components.TableOfContents
import wikidesk.ui.components.TabUiItem
import wikidesk.ui.components.TabsBar
import wikidesk.ui.components.TocUiItem
import wikidesk.ui.components.TopBar
import wikidesk.ui.theme.AppTheme
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Shell principal do aplicativo: barra superior, sidebar (multi-fonte), área
 * de leitura (com abas) e sumário, além dos overlays de busca global e de
 * "adicionar fonte". É o ponto onde o [AppState] observável é traduzido para
 * os modelos "burros" que cada componente de UI espera.
 *
 * Ao iniciar, se existir uma pasta `test-wiki/` no diretório de trabalho
 * atual (o caso ao rodar via `./gradlew run` na raiz do projeto), ela é
 * aberta automaticamente como primeira fonte — conveniência de
 * desenvolvimento. Fora desse cenário (app instalado), o usuário vê a tela
 * de boas-vindas e escolhe a primeira pasta pelo botão "Selecionar pasta"
 * ou "+ Adicionar fonte".
 */
@Composable
fun AppRoot(state: AppState = remember { AppState() }) {
    var startupScanDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (state.sources.isEmpty() && state.sourceError == null) {
            val devWiki = File("test-wiki")
            if (devWiki.isDirectory) {
                state.addLocalSource(devWiki.absolutePath)
            }
        }
        startupScanDone = true
    }

    AppTheme(darkTheme = state.isDarkTheme) {
        val colors = LocalAppColors.current

        // Sinaliza aos WebViews de diagramas Mermaid que um overlay está
        // aberto — eles precisam se recolher para não serem desenhados por
        // cima do modal/busca (ver [wikidesk.ui.LocalOverlayVisible]).
        CompositionLocalProvider(
            LocalOverlayVisible provides (state.searchOpen || state.addSourceOpen)
        ) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            when {
                state.sources.isEmpty() && !startupScanDone -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Abrindo pasta…", style = AppTypography.body, color = colors.textMuted)
                    }
                }

                state.sources.isEmpty() -> {
                    EmptySourcesState(
                        message = state.sourceError.orEmpty(),
                        onPickFolder = {
                            pickWorkspaceDirectory()?.let(state::addLocalSource)
                        }
                    )
                }

                else -> {
                    MainShell(state = state)
                }
            }
        }
        }
    }
}

@Composable
private fun MainShell(state: AppState) {
    val colors = LocalAppColors.current
    val document = state.currentDocument()

    // Rolagem e posições dos headings são reiniciadas a cada troca de documento,
    // para que o sumário (TOC) sempre reflita o documento atualmente aberto.
    val scrollState = key(document?.id) { rememberScrollState() }
    val headingOffsets = remember(document?.id) { mutableStateMapOf<Int, Float>() }
    val coroutineScope = rememberCoroutineScope()
    val documentDirectory = document?.path?.let { File(it).parent }

    // Quando várias seções curtas ficam "espremidas" no fim do documento
    // (não sobra conteúdo suficiente abaixo delas para a rolagem chegar
    // exatamente em cada uma), rolar até qualquer uma delas empaca na mesma
    // posição máxima de rolagem — tornando impossível distinguir, só pela
    // posição de rolagem, qual delas o usuário quis ver. Por isso lembramos
    // explicitamente qual item do sumário foi clicado por último, para usá-lo
    // como desempate nesse cenário (ver `buildTocItems`). É esquecido assim
    // que o usuário rola para longe do fim do documento, para não ficar
    // "grudado" numa seção antiga se ele voltar ao fim por conta própria.
    var lastClickedHeadingIndex by remember(document?.id) { mutableStateOf<Int?>(null) }
    val atDocumentBottom = scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue
    LaunchedEffect(atDocumentBottom) {
        if (!atDocumentBottom) lastClickedHeadingIndex = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            canGoBack = state.canGoBack,
            canGoForward = state.canGoForward,
            isDarkTheme = state.isDarkTheme,
            onAddSource = state::openAddSource,
            onGoBack = state::goBack,
            onGoForward = state::goForward,
            onOpenSearch = state::openSearch,
            onToggleSidebar = state::toggleSidebar,
            onToggleToc = state::toggleToc,
            onToggleTheme = state::toggleTheme,
            onOpenSettings = { /* TODO: abrir tela de configurações */ }
        )

        // Divisor discreto entre a barra superior e o conteúdo (borda "soft":
        // baixo contraste, apenas separação estrutural entre painéis).
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.borderSoft))

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (!state.sidebarCollapsed) {
                val filter = state.treeFilter.trim().lowercase()
                Sidebar(
                    sources = buildSourceUiItems(state, filter),
                    filterText = state.treeFilter,
                    width = state.sidebarWidthDp.dp,
                    onFilterChange = state::updateTreeFilter,
                    onToggleFolder = state::toggleFolder,
                    onOpenFile = state::openFile,
                    onRemoveSource = state::removeSource,
                    onRefreshSource = { sourceId -> state.refreshGitSource(sourceId, coroutineScope) },
                    onToggleSourceCollapsed = state::toggleSourceCollapsed,
                    onResize = state::resizeSidebar
                )

                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(colors.borderSoft))
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TabsBar(
                    tabs = state.tabs.map { tab ->
                        TabUiItem(id = tab.id, label = tab.label, isActive = tab.id == state.activeTabId)
                    },
                    onSelectTab = state::selectTab,
                    onCloseTab = state::closeTab,
                    onCloseAllTabs = state::closeAllTabs,
                    onCloseTabsToRight = state::closeTabsToRight,
                    onCloseTabsToLeft = state::closeTabsToLeft
                )

                if (document != null) {
                    DocumentContent(
                        title = document.title,
                        blocks = document.blocks,
                        scrollState = scrollState,
                        breadcrumb = buildBreadcrumb(state, document),
                        freshness = buildFreshness(state, document),
                        documentDirectory = documentDirectory,
                        onLinkClick = state::onLinkClicked,
                        onHeadingPositioned = { index, offset -> headingOffsets[index] = offset },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    EmptyDocumentState(onOpenSearch = state::openSearch, modifier = Modifier.weight(1f))
                }
            }

            if (!state.tocCollapsed) {
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(colors.borderSoft))

                TableOfContents(
                    tocItems = document?.let {
                        buildTocItems(it, scrollState.value, scrollState.maxValue, headingOffsets, lastClickedHeadingIndex)
                    } ?: emptyList(),
                    width = state.tocWidthDp.dp,
                    onSelectItem = { itemId ->
                        val headingIndex = itemId.removePrefix("toc-").toIntOrNull()
                        lastClickedHeadingIndex = headingIndex
                        val target = headingIndex?.let { headingOffsets[it] }
                        if (target != null) {
                            // Seções perto do fim do documento podem ter um
                            // offset bruto maior que `maxValue` (não sobra
                            // conteúdo suficiente abaixo delas para preencher
                            // a viewport) — o clamp explícito garante que a
                            // rolagem vá o mais longe possível em vez de não
                            // fazer nada; a seção some do topo mesmo assim
                            // apenas quando for realmente o fim do documento.
                            val clampedTarget = target.roundToInt().coerceIn(0, scrollState.maxValue)
                            coroutineScope.launch { scrollState.animateScrollTo(clampedTarget) }
                        }
                    },
                    onResize = state::resizeToc
                )
            }
        }
    }

    if (state.searchOpen) {
        SearchPalette(
            query = state.searchQuery,
            results = buildSearchResults(state, state.searchQuery),
            sectionLabel = if (state.searchQuery.isBlank()) "Recentes" else "Resultados",
            onQueryChange = { state.searchQuery = it },
            onSelectResult = state::selectSearchResult,
            onDismiss = state::closeSearch
        )
    }

    if (state.addSourceOpen) {
        AddSourceModal(
            cloneState = state.gitCloneState,
            onDismiss = state::closeAddSource,
            onPickLocalFolder = {
                pickWorkspaceDirectory()?.let(state::addLocalSource)
                state.closeAddSource()
            },
            onSubmitGit = { remoteUrl, branch, destination, credentials ->
                state.addGitSource(remoteUrl, branch, destination, credentials, coroutineScope)
            }
        )
    }
}

@Composable
private fun EmptySourcesState(message: String, onPickFolder: () -> Unit) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WikiDesk", style = AppTypography.documentTitle, color = colors.text)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.ifBlank { "Selecione uma pasta local com documentação Markdown para começar." },
                style = AppTypography.body,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPickFolder
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Selecionar pasta", color = Color.White, style = AppTypography.body)
            }
        }
    }
}

/** Estado vazio do painel central quando nenhuma aba está aberta (spec §5). */
@Composable
private fun EmptyDocumentState(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Nenhum documento aberto",
                style = AppTypography.body,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenSearch
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Abrir busca (⌘K)", style = AppTypography.caption, color = colors.textMuted)
            }
        }
    }
}

private fun buildSourceUiItems(state: AppState, filter: String): List<SourceUiItem> {
    val removable = state.sources.size > 1
    return state.sources.map { source ->
        val isGit = source.type == SourceType.GIT
        val status = state.gitStatuses[source.id]
        val changeKindByPath = status?.changedFiles?.associate { it.relativePath to it.kind }.orEmpty()

        SourceUiItem(
            id = source.id,
            name = source.name,
            typeLabel = if (isGit) "GIT" else "LOCAL",
            removable = removable,
            rootFiles = buildFileItems(source.rootFiles, state, filter, changeKindByPath),
            folders = source.tree.map { buildFolderItem(it, state, filter, changeKindByPath) },
            isGit = isGit,
            isRefreshing = isGit && state.gitRefreshingSourceId == source.id,
            collapsed = state.collapsedSources[source.id] ?: false,
            gitStatus = status?.let { buildGitStatusUiItem(it) }
        )
    }
}

private fun buildGitStatusUiItem(status: GitSourceStatus): GitStatusUiItem {
    val counts = status.changedFiles.groupingBy { it.kind }.eachCount()
    return GitStatusUiItem(
        branch = status.branch,
        commitHash = status.commitHash,
        relativeTime = formatRelativeTime(status.commitEpochSeconds),
        author = status.commitAuthor,
        aheadCount = status.aheadCount,
        behindCount = status.behindCount,
        modifiedCount = counts[GitFileChangeKind.MODIFIED] ?: 0,
        newCount = counts[GitFileChangeKind.NEW] ?: 0,
        removedCount = counts[GitFileChangeKind.REMOVED] ?: 0
    )
}

private fun buildFileItems(
    files: List<FileEntry>,
    state: AppState,
    filter: String,
    changeKindByPath: Map<String, GitFileChangeKind> = emptyMap()
): List<FileUiItem> {
    return files.map { file ->
        FileUiItem(
            id = file.id,
            name = file.name,
            visible = filter.isEmpty() || file.name.lowercase().contains(filter),
            isActive = file.id == state.activeTabId,
            changeKind = changeKindByPath[file.path]
        )
    }
}

private fun buildFolderItem(
    node: FolderNode,
    state: AppState,
    filter: String,
    changeKindByPath: Map<String, GitFileChangeKind> = emptyMap()
): FolderUiItem {
    return FolderUiItem(
        id = node.id,
        name = node.name,
        expanded = state.expandedFolders[node.id] ?: false,
        files = buildFileItems(node.files, state, filter, changeKindByPath),
        children = node.children.map { buildFolderItem(it, state, filter, changeKindByPath) }
    )
}

/**
 * Monta a linha de contexto (breadcrumb) exibida acima do H1: nome da fonte,
 * pasta imediata (se houver) e nome do arquivo — apenas contexto de
 * localização, não é clicável (spec §7).
 */
private fun buildBreadcrumb(state: AppState, document: MarkdownDocument): String? {
    val sourceId = state.sourceIdOf(document.id) ?: return null
    val source = state.sources.firstOrNull { it.id == sourceId } ?: return null
    val relativePath = document.id.removePrefix("$sourceId::")
    val parentPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
    val folderName = parentPath.substringAfterLast('/', missingDelimiterValue = parentPath).takeIf { it.isNotBlank() }
    val fileName = File(document.path).name

    return listOfNotNull(source.name, folderName, fileName).joinToString("  /  ")
}

/**
 * Monta a linha de frescor exibida logo abaixo do breadcrumb, para fontes
 * Git: quando o commit atual foi feito, em qual branch, e se o arquivo aberto
 * tem alteração local pendente ainda não commitada. `null` para documentos
 * fora de uma fonte Git.
 */
private fun buildFreshness(state: AppState, document: MarkdownDocument): String? {
    val sourceId = state.sourceIdOf(document.id) ?: return null
    val status = state.gitStatuses[sourceId] ?: return null
    val relativePath = document.id.removePrefix("$sourceId::")

    val fileStatusSuffix = when (status.changedFiles.firstOrNull { it.relativePath == relativePath }?.kind) {
        GitFileChangeKind.MODIFIED -> "  ·  modificado localmente"
        GitFileChangeKind.NEW -> "  ·  novo (não commitado)"
        GitFileChangeKind.REMOVED -> "  ·  removido localmente"
        null -> ""
    }

    val relativeTime = formatRelativeTime(status.commitEpochSeconds)
    return "Última modificação: $relativeTime  ·  ${status.branch}  ·  ${status.commitHash}$fileStatusSuffix"
}

/**
 * Monta os itens do sumário, destacando a seção cuja posição de rolagem
 * (armazenada em [headingOffsets], preenchida pelo [wikidesk.ui.components.DocumentContent]
 * via `onHeadingPositioned`) é a mais próxima — mas não além — da posição
 * atual de rolagem ([scrollValuePx]). Enquanto as posições ainda não foram
 * medidas (primeiro frame após abrir um documento), a primeira seção é
 * destacada por padrão.
 *
 * Caso especial — fim do documento ([scrollMaxValuePx]): quando as últimas
 * seções são curtas, não sobra conteúdo suficiente abaixo delas para
 * preencher a viewport, então a rolagem trava em `scrollMaxValuePx` antes de
 * alcançar o offset bruto de qualquer uma delas. Isso tem dois efeitos que
 * precisam ser corrigidos:
 *
 * 1. Sem tratamento especial, o destaque fica preso numa seção anterior para
 *    sempre, mesmo com o documento todo rolado até o fim — dando a impressão
 *    de que clicar no sumário "não funciona". Por isso, ao detectar que a
 *    rolagem chegou ao máximo possível, a última seção passa a ser
 *    considerada ativa por padrão.
 * 2. Mas várias seções curtas podem estar "espremidas" nesse mesmo trecho
 *    final — todas travam na mesma posição de rolagem, então não dá para
 *    saber só pela posição qual delas o usuário quis ver ao clicar em uma
 *    que não é a última. Por isso [lastClickedHeadingIndex] tem prioridade
 *    sobre a última seção nesse cenário: se o clique mais recente foi numa
 *    dessas seções espremidas, é ela que fica em destaque, não a última.
 */
private fun buildTocItems(
    document: MarkdownDocument,
    scrollValuePx: Int,
    scrollMaxValuePx: Int,
    headingOffsets: Map<Int, Float>,
    lastClickedHeadingIndex: Int?
): List<TocUiItem> {
    val leadPx = 80f
    val atBottom = scrollMaxValuePx > 0 && scrollValuePx >= scrollMaxValuePx
    val activeIndex = if (atBottom) {
        val clickedIsSqueezedAtBottom = lastClickedHeadingIndex != null &&
            (headingOffsets[lastClickedHeadingIndex] ?: 0f) >= scrollMaxValuePx - leadPx
        if (clickedIsSqueezedAtBottom) {
            lastClickedHeadingIndex
        } else {
            document.headings.indices.lastOrNull() ?: 0
        }
    } else {
        headingOffsets.entries
            .filter { it.value <= scrollValuePx + leadPx }
            .maxByOrNull { it.value }
            ?.key ?: 0
    }

    return document.headings.mapIndexed { index, heading ->
        TocUiItem(
            id = "toc-$index",
            text = heading.text,
            level = heading.level,
            isActive = index == activeIndex
        )
    }
}

private fun buildSearchResults(state: AppState, query: String): List<SearchResultUiItem> {
    val q = query.trim().lowercase()
    val allDocs = state.documents.values.sortedBy { it.path }

    val candidates = if (q.isEmpty()) {
        allDocs.take(8)
    } else {
        allDocs.filter { doc ->
            doc.title.lowercase().contains(q) ||
                doc.id.lowercase().contains(q) ||
                doc.blocks.any { block -> blockMatchesQuery(block, q) }
        }.take(50)
    }

    return candidates.map { doc ->
        val sourceId = state.sourceIdOf(doc.id)
        val sourceName = state.sources.firstOrNull { it.id == sourceId }?.name.orEmpty()
        val relativePath = sourceId?.let { doc.id.removePrefix("$it::") } ?: doc.id
        val folderPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val folderName = listOf(sourceName, folderPath).filter { it.isNotBlank() }.joinToString(" / ")
        SearchResultUiItem(id = doc.id, title = doc.title, folderName = folderName)
    }
}

private fun blockMatchesQuery(block: MarkdownBlock, query: String): Boolean {
    return when (block) {
        is MarkdownBlock.Heading -> block.text.lowercase().contains(query)
        is MarkdownBlock.Paragraph -> block.text.lowercase().contains(query)
        is MarkdownBlock.Quote -> block.text.lowercase().contains(query)
        is MarkdownBlock.BulletList -> block.items.any { it.lowercase().contains(query) }
        is MarkdownBlock.CodeBlock -> block.code.lowercase().contains(query)
        is MarkdownBlock.Table -> block.headers.any { it.lowercase().contains(query) } ||
            block.rows.any { row -> row.any { cell -> cell.lowercase().contains(query) } }
        is MarkdownBlock.Image -> block.alt.lowercase().contains(query)
        is MarkdownBlock.Mermaid -> block.code.lowercase().contains(query)
        is MarkdownBlock.Callout -> block.title.lowercase().contains(query) || block.text.lowercase().contains(query)
        MarkdownBlock.Divider -> false
    }
}
