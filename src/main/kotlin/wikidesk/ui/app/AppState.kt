package wikidesk.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import wikidesk.domain.MarkdownDocument
import wikidesk.domain.Source
import wikidesk.domain.SourceType
import wikidesk.git.GitCloneState
import wikidesk.git.GitClient
import wikidesk.git.GitCredentials
import wikidesk.git.GitResult
import wikidesk.git.GitSourceStatus
import wikidesk.git.cloneDestinationInFolder
import wikidesk.git.defaultCloneDestination
import wikidesk.git.isGitRepository
import wikidesk.git.readOriginUrl
import wikidesk.persistence.PersistenceContainer
import wikidesk.persistence.PersistenceLog
import wikidesk.persistence.model.AppTheme
import wikidesk.persistence.model.WikiGitState
import wikidesk.persistence.model.WikiSourceType
import wikidesk.platform.openExternalUrl
import wikidesk.workspace.WorkspaceScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.URLDecoder
import java.nio.file.Paths
import kotlin.math.roundToInt

/** Uma aba de documento aberta no shell principal. */
data class TabState(val id: String, val label: String)

/**
 * Dados necessários para atualizar (`git pull`) uma fonte Git já clonada,
 * mantidos apenas em memória durante a sessão do app — nada aqui é
 * persistido em disco (ver [GitCredentials]).
 */
private data class GitRemoteInfo(
    val remoteUrl: String,
    val branch: String?,
    val localPath: String,
    val credentials: GitCredentials?
)

/**
 * Estado observável do shell principal do aplicativo: fontes abertas (multi-
 * workspace) e seus documentos carregados, tema, dimensões e visibilidade
 * dos painéis laterais, abas abertas, histórico de navegação, filtro da
 * árvore e busca global.
 *
 * Esta classe concentra toda a lógica de interação descrita nas seções
 * "Barra superior", "Barra lateral esquerda", "Abas", "Painel direito" e
 * "Busca" da especificação, para que os componentes de UI permaneçam
 * "burros" (recebem estado e emitem eventos).
 */
class AppState(private val persistence: PersistenceContainer = PersistenceContainer.createOrFallback()) {

    /**
     * Escopo próprio para escritas de persistência que são efeito colateral
     * de uma ação síncrona da UI (ex.: registrar a wiki depois de
     * `addLocalSource` já ter atualizado a sidebar) e não têm um
     * `CoroutineScope` de composable para se atrelar. Cancelado em [dispose].
     */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Id da wiki no banco para cada fonte aberta nesta sessão — populado pelo registro/restauração (ver [bootstrap]). */
    private val wikiIdsBySourceId = mutableMapOf<String, Long>()

    /** Wikis registradas cujo caminho salvo não existe mais no disco (ver [bootstrap]) — metadados preservados, só não reabertas automaticamente. */
    val unavailableWikiNames = mutableStateListOf<String>()

    /**
     * Libera os recursos da camada de persistência. Chamado uma única vez, ao
     * fechar a janela (ver `Main.kt`).
     *
     * Antes de cancelar [persistenceScope] e fechar o banco, espera (com um
     * timeout de segurança) qualquer escrita em segundo plano já em andamento
     * — como o registro de uma wiki lançado por [persistWikiRegistration] logo
     * após `addLocalSource`/`addGitSource`, ou a preferência de tema/sidebar.
     * Sem isso, fechar a janela pouco depois de uma dessas ações
     * (ex.: selecionar uma pasta e fechar o app em seguida, exatamente o
     * cenário de "escolhi uma pasta e ao reabrir ela não estava lá") cancela o
     * job e fecha a conexão antes do `INSERT`/`UPDATE` chegar a acontecer —
     * a wiki nunca é gravada, mas nada na UI indica isso, porque o registro é
     * deliberadamente "fire-and-forget" para não travar a sidebar.
     */
    fun dispose() {
        runCatching {
            runBlocking {
                withTimeoutOrNull(5_000) {
                    persistenceScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
                }
            }
        }.onFailure { e -> PersistenceLog.warn("Falha ao aguardar escritas pendentes ao fechar", e) }

        persistenceScope.cancel()
        persistence.close()
    }

    // --- Fontes (multi-workspace) e documentos -----------------------------
    val sources = mutableStateListOf<Source>()

    var sourceError by mutableStateOf<String?>(null)
        private set

    private val documentsMap = mutableStateMapOf<String, MarkdownDocument>()
    val documents: Map<String, MarkdownDocument> get() = documentsMap

    /**
     * Adiciona uma nova fonte local a partir de uma pasta do disco. Se a
     * mesma pasta já estiver aberta como fonte, não faz nada (evita
     * duplicatas). A primeira fonte adicionada define o documento inicial.
     *
     * Se a pasta escolhida já for um checkout Git (tem uma pasta `.git`),
     * a fonte é marcada como GIT em vez de LOCAL — isso faz o badge mostrar
     * "GIT" e o botão de atualizar (↻) aparecer na sidebar, exatamente como
     * uma fonte clonada pelo próprio app pela aba "Repositório Git".
     */
    fun addLocalSource(path: String) {
        val absolutePath = File(path).absolutePath
        val sourceId = "local:$absolutePath"

        if (sources.any { it.id == sourceId }) {
            sourceError = null
            return
        }

        val source = openScannedLocalSource(sourceId, absolutePath)
        if (source == null) {
            sourceError = "Não foi possível abrir esta pasta:\n$path"
            return
        }

        sourceError = null

        if (activeTabId.isEmpty()) {
            source.initialDocumentId?.let { activateTab(it) }
        }

        persistWikiRegistration(
            sourceId = sourceId,
            name = source.name,
            rootPath = absolutePath,
            // `LOCAL_FOLDER`, não `GIT_REPOSITORY`, mesmo quando a pasta já é
            // um checkout Git: o que este campo registra é como a wiki foi
            // adicionada ao WikiDesk (seletor de pasta vs. clone pela aba
            // "Repositório Git" — ver `addGitSource`), que é exatamente o que
            // decide o prefixo do id da fonte ("local:"/"git:") usado por
            // toda a sessão — não se a pasta tem um `.git` dentro (isso já é
            // capturado por `Source.type`/`gitStatuses`, algo ortogonal).
            sourceType = WikiSourceType.LOCAL_FOLDER,
            remoteUrl = gitRemotes[sourceId]?.remoteUrl?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Faz o scan de [absolutePath] como fonte local (mesma lógica para uma
     * pasta escolhida agora pelo usuário ou uma wiki persistida sendo
     * reaberta no startup — ver [bootstrap]) e a adiciona a [sources]/
     * [documentsMap]/[gitRemotes]/[gitStatuses]. Não mexe em persistência —
     * quem chama decide o que fazer com o banco depois.
     */
    private fun openScannedLocalSource(sourceId: String, absolutePath: String): Source? {
        val loaded = WorkspaceScanner.scanLocal(sourceId, absolutePath) ?: return null

        val isExistingGitCheckout = isGitRepository(File(absolutePath))
        val source = if (isExistingGitCheckout) loaded.source.copy(type = SourceType.GIT) else loaded.source

        sources.add(source)
        documentsMap.putAll(loaded.documents)
        source.tree.forEach { expandedFolders[it.id] = true }

        if (isExistingGitCheckout) {
            gitRemotes[sourceId] = GitRemoteInfo(
                remoteUrl = readOriginUrl(File(absolutePath)).orEmpty(),
                branch = null,
                localPath = absolutePath,
                credentials = null
            )
            // Leitura síncrona, na mesma linha do resto da função: em pastas
            // locais o próprio scan da árvore já é síncrono, então isso
            // apenas mantém a mesma característica em vez de introduzir uma
            // corrotina só para este caso.
            GitClient.readStatus(File(absolutePath))?.let { gitStatuses[sourceId] = it }
        }

        return source
    }

    /**
     * Registra/atualiza a wiki no banco em segundo plano ([persistenceScope])
     * — a fonte já está visível na sidebar antes disso terminar, então o
     * registro em si nunca atrasa a UI. Guarda o id resultante em
     * [wikiIdsBySourceId] para uso por [removeSource]/snapshots de Git.
     */
    private fun persistWikiRegistration(
        sourceId: String,
        name: String,
        rootPath: String,
        sourceType: WikiSourceType,
        remoteUrl: String?
    ) {
        persistenceScope.launch {
            runCatching {
                val wiki = persistence.wikiRepository.register(
                    name = name,
                    rootPath = rootPath,
                    sourceType = sourceType,
                    remoteUrl = remoteUrl
                )
                wikiIdsBySourceId[sourceId] = wiki.id
                persistence.wikiRepository.markOpened(wiki.id)
                persistence.settingsRepository.updateLastOpenedWiki(wiki.id)

                if (isGitRepository(File(rootPath))) {
                    persistGitSnapshot(sourceId, wiki.id)
                }
            }.onFailure { e -> PersistenceLog.warn("Falha ao registrar a wiki '$name' no banco", e) }
        }
    }

    /** Salva o `GitSourceStatus` em memória de [sourceId] (já lido/atualizado) como snapshot persistido da wiki [wikiId]. Não faz nada se não houver status conhecido ainda. */
    private suspend fun persistGitSnapshot(sourceId: String, wikiId: Long) {
        val status = gitStatuses[sourceId] ?: return
        runCatching {
            persistence.gitStateRepository.saveSnapshot(
                WikiGitState(
                    wikiId = wikiId,
                    currentBranch = status.branch,
                    headCommit = status.commitHash,
                    remoteHeadCommit = null,
                    hasLocalChanges = status.changedFiles.isNotEmpty(),
                    aheadCount = status.aheadCount,
                    behindCount = status.behindCount,
                    lastFetchAt = System.currentTimeMillis(),
                    lastScanAt = System.currentTimeMillis()
                )
            )
        }.onFailure { e -> PersistenceLog.warn("Falha ao salvar snapshot Git da wiki id=$wikiId", e) }
    }

    /**
     * Chamada uma única vez, pelo `LaunchedEffect(Unit)` de `AppRoot`, ao
     * iniciar o app: carrega preferências, aplica tema/largura da sidebar,
     * restaura as wikis ativas verificando se cada caminho ainda existe, e
     * foca a última wiki aberta quando possível. Se nenhuma wiki persistida
     * puder ser reaberta, cai no mesmo atalho de desenvolvimento que já
     * existia antes da persistência (abrir `test-wiki/` quando presente no
     * diretório de trabalho).
     *
     * Nunca lança: uma falha em qualquer parte do carregamento é logada e a
     * tela inicial (seleção de pasta) continua acessível normalmente — ver
     * `sourceError`.
     */
    suspend fun bootstrap() {
        runCatching { bootstrapInternal() }
            .onFailure { e ->
                PersistenceLog.error("Falha ao restaurar wikis/preferências salvas — iniciando com estado vazio.", e)
                sourceError = "Não foi possível carregar as preferências salvas. Selecione uma pasta para começar."
            }
    }

    private suspend fun bootstrapInternal() {
        val settings = persistence.settingsRepository.loadSettings()
        currentTheme = settings.theme
        isDarkTheme = settings.theme == AppTheme.DARK
        sidebarWidthDp = settings.sidebarWidth.toFloat()

        val activeWikis = persistence.wikiRepository.listActiveWikis()
        var lastOpenedSourceId: String? = null

        for (wiki in activeWikis) {
            if (!File(wiki.rootPath).isDirectory) {
                unavailableWikiNames.add(wiki.name)
                PersistenceLog.warn("Wiki '${wiki.name}' (id=${wiki.id}) está registrada, mas o caminho salvo não existe mais.")
                continue
            }

            val sourceId = when (wiki.sourceType) {
                WikiSourceType.GIT_REPOSITORY -> "git:${wiki.rootPath}"
                WikiSourceType.LOCAL_FOLDER -> "local:${wiki.rootPath}"
            }
            if (sources.any { it.id == sourceId }) continue

            openScannedLocalSource(sourceId, wiki.rootPath) ?: continue
            wikiIdsBySourceId[sourceId] = wiki.id

            if (wiki.id == settings.lastOpenedWikiId) {
                lastOpenedSourceId = sourceId
            }
        }

        if (activeTabId.isEmpty()) {
            val focusSource = lastOpenedSourceId?.let { id -> sources.firstOrNull { it.id == id } } ?: sources.firstOrNull()
            focusSource?.initialDocumentId?.let { activateTab(it) }
        }

        // Nenhuma wiki persistida pôde ser reaberta — mesma conveniência de
        // desenvolvimento que existia antes da persistência local.
        if (sources.isEmpty() && sourceError == null) {
            val devWiki = File("test-wiki")
            if (devWiki.isDirectory) {
                addLocalSource(devWiki.absolutePath)
            }
        }

        if (sources.isEmpty() && unavailableWikiNames.isNotEmpty()) {
            sourceError = "As seguintes wikis não foram encontradas nos caminhos salvos: " +
                unavailableWikiNames.joinToString(", ") +
                ". Selecione uma pasta para adicioná-la novamente."
        }
    }

    /**
     * Remove uma fonte da sidebar. Nunca remove a última fonte restante (a
     * sidebar não pode ficar vazia). Abas de documentos dessa fonte que já
     * estavam abertas permanecem na barra de abas — apenas deixam de
     * resolver conteúdo, já que os documentos da fonte são descartados.
     */
    fun removeSource(sourceId: String) {
        if (sources.size <= 1) return
        val index = sources.indexOfFirst { it.id == sourceId }
        if (index == -1) return

        sources.removeAt(index)

        val prefix = "$sourceId::"
        documentsMap.keys.filter { it.startsWith(prefix) }.forEach { documentsMap.remove(it) }
        expandedFolders.keys.filter { it.startsWith(prefix) }.forEach { expandedFolders.remove(it) }
        gitRemotes.remove(sourceId)
        gitStatuses.remove(sourceId)
        collapsedSources.remove(sourceId)

        // Remoção lógica no banco: os arquivos no disco e os metadados da
        // wiki continuam intactos — só deixa de ser reaberta automaticamente
        // no próximo startup (ver `bootstrap`/`WikiRepository.softDelete`).
        wikiIdsBySourceId.remove(sourceId)?.let { wikiId ->
            persistenceScope.launch {
                runCatching { persistence.wikiRepository.softDelete(wikiId) }
                    .onFailure { e -> PersistenceLog.warn("Falha ao remover logicamente a wiki id=$wikiId", e) }
            }
        }
    }

    /**
     * Fontes (blocos inteiros na sidebar) recolhidas pelo usuário — cada nova
     * fonte adicionada começa expandida por padrão; o problema que isso
     * resolve é a sidebar ficando poluída conforme mais "wikis" são abertas,
     * sem uma forma de esconder as que não estão sendo usadas no momento.
     */
    val collapsedSources = mutableStateMapOf<String, Boolean>()

    fun toggleSourceCollapsed(sourceId: String) {
        collapsedSources[sourceId] = !(collapsedSources[sourceId] ?: false)
    }

    // --- Fontes Git (clone/atualização via JGit) ---------------------------
    private val gitRemotes = mutableMapOf<String, GitRemoteInfo>()

    var gitCloneState by mutableStateOf<GitCloneState>(GitCloneState.Idle)
        private set

    /** Id da fonte Git com um `git pull` em andamento, ou `null` se nenhuma. */
    var gitRefreshingSourceId by mutableStateOf<String?>(null)
        private set

    /**
     * Metadados de rastreio Git por fonte (branch, commit, autor, distância
     * da origem, arquivos alterados localmente) — ver `wikidesk.git.GitSourceStatus`.
     * Populado ao adicionar uma fonte Git (clonada ou pasta local que já era
     * um checkout) e recalculado a cada "Atualizar".
     */
    val gitStatuses = mutableStateMapOf<String, GitSourceStatus>()

    private suspend fun refreshGitStatus(sourceId: String, localPath: String) {
        val status = withContext(Dispatchers.IO) { GitClient.readStatus(File(localPath)) }
        if (status != null) gitStatuses[sourceId] = status else gitStatuses.remove(sourceId)
    }

    /**
     * Clona [remoteUrl] e adiciona o resultado como uma nova fonte GIT. Roda
     * em background ([scope]) para não travar a UI durante a operação de
     * rede — [gitCloneState] reflete o progresso para que o modal
     * "Adicionar fonte" possa mostrar um indicador e fechar sozinho quando
     * terminar com sucesso.
     *
     * [destinationFolder] é a pasta-base onde clonar: se `null`, usa a pasta
     * padrão de dados do app; se informada (pasta escolhida pelo usuário),
     * uma nova subpasta é criada dentro dela com o nome do repositório — o
     * usuário nunca precisa ter uma pasta vazia pronta de antemão.
     */
    fun addGitSource(
        remoteUrl: String,
        branch: String?,
        destinationFolder: File?,
        credentials: GitCredentials?,
        scope: CoroutineScope
    ) {
        scope.launch {
            gitCloneState = GitCloneState.InProgress
            val destination = if (destinationFolder != null) {
                cloneDestinationInFolder(destinationFolder, remoteUrl)
            } else {
                defaultCloneDestination(remoteUrl)
            }

            val result = withContext(Dispatchers.IO) {
                GitClient.clone(remoteUrl, branch, destination, credentials)
            }

            when (result) {
                is GitResult.Success -> {
                    val sourceId = "git:${destination.absolutePath}"
                    val loaded = withContext(Dispatchers.IO) {
                        WorkspaceScanner.scanLocal(sourceId, result.localPath)
                    }

                    if (loaded == null) {
                        gitCloneState = GitCloneState.Error(
                            "Repositório clonado, mas a pasta não pôde ser lida."
                        )
                        return@launch
                    }

                    val gitSource = loaded.source.copy(type = SourceType.GIT)
                    sources.add(gitSource)
                    documentsMap.putAll(loaded.documents)
                    gitSource.tree.forEach { expandedFolders[it.id] = true }
                    gitRemotes[sourceId] = GitRemoteInfo(remoteUrl, branch, result.localPath, credentials)
                    refreshGitStatus(sourceId, result.localPath)

                    // Em persistenceScope (não no `scope` recebido, que é o
                    // CoroutineScope da composição do modal "Adicionar fonte")
                    // pelo mesmo motivo de `persistWikiRegistration`: `scope`
                    // é cancelado quando a composição sai (ex.: janela
                    // fechada logo após o clone terminar), o que cortaria
                    // esta escrita antes de `AppState.dispose()` ter chance
                    // de esperar por ela.
                    persistenceScope.launch {
                        runCatching {
                            val wiki = persistence.wikiRepository.register(
                                name = gitSource.name,
                                rootPath = result.localPath,
                                sourceType = WikiSourceType.GIT_REPOSITORY,
                                remoteUrl = remoteUrl,
                                defaultBranch = branch
                            )
                            wikiIdsBySourceId[sourceId] = wiki.id
                            persistence.wikiRepository.markOpened(wiki.id)
                            persistence.settingsRepository.updateLastOpenedWiki(wiki.id)
                            persistGitSnapshot(sourceId, wiki.id)
                        }.onFailure { e -> PersistenceLog.warn("Falha ao registrar a wiki Git '${gitSource.name}' no banco", e) }
                    }

                    if (activeTabId.isEmpty()) {
                        gitSource.initialDocumentId?.let { activateTab(it) }
                    }

                    gitCloneState = GitCloneState.Idle
                }

                is GitResult.Failure -> {
                    gitCloneState = GitCloneState.Error(result.message)
                }
            }
        }
    }

    /** Executa `git pull` em uma fonte Git já aberta e reescaneia seus documentos. */
    fun refreshGitSource(sourceId: String, scope: CoroutineScope) {
        val info = gitRemotes[sourceId] ?: return

        scope.launch {
            gitRefreshingSourceId = sourceId

            val pullResult = withContext(Dispatchers.IO) {
                GitClient.pull(File(info.localPath), info.credentials)
            }

            when (pullResult) {
                is GitResult.Success -> {
                    val loaded = withContext(Dispatchers.IO) {
                        WorkspaceScanner.scanLocal(sourceId, info.localPath)
                    }
                    if (loaded != null) {
                        val index = sources.indexOfFirst { it.id == sourceId }
                        if (index != -1) {
                            sources[index] = loaded.source.copy(type = SourceType.GIT)
                        }
                        val prefix = "$sourceId::"
                        documentsMap.keys.filter { it.startsWith(prefix) }.forEach { documentsMap.remove(it) }
                        documentsMap.putAll(loaded.documents)
                    }
                }

                is GitResult.Failure -> {
                    gitCloneState = GitCloneState.Error(pullResult.message)
                }
            }

            // Atualiza os metadados de status (branch/commit/arquivos alterados)
            // mesmo se o pull falhou (ex.: sem rede) — eles refletem o estado da
            // working tree local, que independe do pull ter tido sucesso.
            refreshGitStatus(sourceId, info.localPath)

            // persistenceScope, não `scope` — mesmo motivo de addGitSource:
            // não fica preso ao ciclo de vida da composição/janela.
            wikiIdsBySourceId[sourceId]?.let { wikiId ->
                persistenceScope.launch { persistGitSnapshot(sourceId, wikiId) }
            }

            gitRefreshingSourceId = null
        }
    }

    fun currentDocument(): MarkdownDocument? = documents[activeTabId]

    /** Fonte à qual um id de documento/pasta pertence (usa o prefixo `"sourceId::"`). */
    fun sourceIdOf(compositeId: String): String? = compositeId.substringBefore("::", missingDelimiterValue = "")
        .takeIf { compositeId.contains("::") }

    // --- Tema -------------------------------------------------------------
    /**
     * Preferência persistida (`SYSTEM`, `LIGHT` ou `DARK`). [toggleTheme]
     * continua sendo um botão de dois estados na UI (mesmo comportamento
     * visual de sempre) — alterna entre `LIGHT`/`DARK` e persiste o
     * resultado; a UI não tem hoje um seletor de três posições.
     *
     * `SYSTEM` resolve para claro (`isDarkTheme = false`) ao carregar: a JVM
     * não tem uma API multiplataforma confiável para detectar o tema atual
     * do SO sem bindings nativos adicionais — ver limitações no relatório de
     * entrega da persistência.
     */
    private var currentTheme: AppTheme = AppTheme.LIGHT

    var isDarkTheme by mutableStateOf(false)
        private set

    fun toggleTheme() {
        currentTheme = if (currentTheme == AppTheme.DARK) AppTheme.LIGHT else AppTheme.DARK
        isDarkTheme = currentTheme == AppTheme.DARK
        persistenceScope.launch {
            runCatching { persistence.settingsRepository.updateTheme(currentTheme) }
                .onFailure { e -> PersistenceLog.warn("Falha ao salvar preferência de tema", e) }
        }
    }

    // --- Sidebar ------------------------------------------------------------
    var sidebarWidthDp by mutableStateOf(272f)
        private set
    var sidebarCollapsed by mutableStateOf(false)
        private set

    private var sidebarPersistJob: Job? = null

    fun toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed
    }

    fun resizeSidebar(deltaPx: Float) {
        sidebarWidthDp = (sidebarWidthDp + deltaPx).coerceIn(220f, 420f)

        // Arrastar a borda da sidebar chama isto várias vezes por segundo —
        // debounce simples para não gravar no banco a cada pixel, só depois
        // que o usuário parar de arrastar por um instante.
        sidebarPersistJob?.cancel()
        sidebarPersistJob = persistenceScope.launch {
            delay(300)
            runCatching { persistence.settingsRepository.updateSidebarWidth(sidebarWidthDp.roundToInt()) }
                .onFailure { e -> PersistenceLog.warn("Falha ao salvar largura da sidebar", e) }
        }
    }

    val expandedFolders = mutableStateMapOf<String, Boolean>()

    fun toggleFolder(id: String) {
        expandedFolders[id] = !(expandedFolders[id] ?: false)
    }

    var treeFilter by mutableStateOf("")

    fun updateTreeFilter(value: String) {
        treeFilter = value
    }

    // --- Sumário (TOC) --------------------------------------------------
    var tocWidthDp by mutableStateOf(240f)
        private set
    var tocCollapsed by mutableStateOf(false)
        private set

    fun toggleToc() {
        tocCollapsed = !tocCollapsed
    }

    fun resizeToc(deltaPx: Float) {
        // O painel fica à direita, então arrastar para a esquerda (delta negativo) o alarga.
        tocWidthDp = (tocWidthDp - deltaPx).coerceIn(180f, 340f)
    }

    // --- Abas e histórico de navegação --------------------------------------
    val tabs = mutableStateListOf<TabState>()

    var activeTabId by mutableStateOf("")
        private set

    private val backStack = mutableStateListOf<String>()
    private val forwardStack = mutableStateListOf<String>()

    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()

    fun openFile(id: String) {
        if (id == activeTabId || !documents.containsKey(id)) return
        if (activeTabId.isNotEmpty()) backStack.add(activeTabId)
        forwardStack.clear()
        activateTab(id)
        closeSearch()
    }

    fun goBack() {
        if (backStack.isEmpty()) return
        forwardStack.add(activeTabId)
        val previous = backStack.removeAt(backStack.lastIndex)
        activateTab(previous)
    }

    fun goForward() {
        if (forwardStack.isEmpty()) return
        backStack.add(activeTabId)
        val next = forwardStack.removeAt(forwardStack.lastIndex)
        activateTab(next)
    }

    fun selectTab(id: String) {
        if (id == activeTabId) return
        if (activeTabId.isNotEmpty()) backStack.add(activeTabId)
        forwardStack.clear()
        activateTab(id)
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return
        tabs.removeAt(index)
        if (activeTabId == id) {
            val fallback = tabs.getOrNull(index) ?: tabs.getOrNull(index - 1)
            if (fallback != null) {
                activateTab(fallback.id)
            } else {
                activeTabId = ""
            }
        }
    }

    /** Fecha todas as abas abertas. */
    fun closeAllTabs() {
        tabs.clear()
        activeTabId = ""
    }

    /** Fecha todas as abas à direita da aba [id] (exclusive), mantendo a clicada. */
    fun closeTabsToRight(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return
        val keepIds = tabs.subList(0, index + 1).map { it.id }.toSet()
        val wasActiveRemoved = activeTabId !in keepIds
        tabs.removeAll { it.id !in keepIds }
        if (wasActiveRemoved) activateTab(id)
    }

    /** Fecha todas as abas à esquerda da aba [id] (exclusive), mantendo a clicada. */
    fun closeTabsToLeft(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return
        val keepIds = tabs.subList(index, tabs.size).map { it.id }.toSet()
        val wasActiveRemoved = activeTabId !in keepIds
        tabs.removeAll { it.id !in keepIds }
        if (wasActiveRemoved) activateTab(id)
    }

    private fun activateTab(id: String) {
        if (tabs.none { it.id == id }) {
            val label = documents[id]?.title ?: id
            tabs.add(TabState(id, label))
        }
        activeTabId = id
    }

    // --- Links internos/externos ---------------------------------------
    /**
     * Chamado ao clicar em um link dentro do texto renderizado. Links
     * externos (http/https/mailto) abrem no navegador padrão; links
     * relativos são resolvidos contra o documento atual (dentro da mesma
     * fonte) e, se apontarem para um documento conhecido, abrem uma nova aba.
     *
     * Links relativos que não resolvem para nenhum documento carregado são
     * silenciosamente ignorados por enquanto — o estado "documento não
     * encontrado" (seção 12 do escopo) ainda não foi implementado.
     */
    fun onLinkClicked(rawTarget: String) {
        if (rawTarget.startsWith("http://") || rawTarget.startsWith("https://") || rawTarget.startsWith("mailto:")) {
            openExternalUrl(rawTarget)
            return
        }

        val withoutAnchor = rawTarget.substringBefore("#")
        if (withoutAnchor.isBlank()) return

        val decoded = runCatching { URLDecoder.decode(withoutAnchor, "UTF-8") }.getOrDefault(withoutAnchor)
        val resolvedId = resolveRelativeId(activeTabId, decoded)
        if (documents.containsKey(resolvedId)) {
            openFile(resolvedId)
        }
    }

    private fun resolveRelativeId(currentId: String, rawTarget: String): String {
        val separatorIndex = currentId.indexOf("::")
        if (separatorIndex == -1) return rawTarget

        val sourceId = currentId.substring(0, separatorIndex)
        val relativePath = currentId.substring(separatorIndex + 2)
        val base = Paths.get(relativePath).parent ?: Paths.get("")

        val resolvedRelative = runCatching {
            base.resolve(rawTarget).normalize().toString().replace(File.separatorChar, '/')
        }.getOrDefault(rawTarget)

        return "$sourceId::$resolvedRelative"
    }

    // --- Busca global -------------------------------------------------------
    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")

    fun openSearch() {
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
    }

    fun selectSearchResult(id: String) {
        openFile(id)
    }

    // --- Modal "Adicionar fonte" -----------------------------------------
    var addSourceOpen by mutableStateOf(false)
        private set

    fun openAddSource() {
        addSourceOpen = true
        gitCloneState = GitCloneState.Idle
    }

    fun closeAddSource() {
        addSourceOpen = false
    }
}
