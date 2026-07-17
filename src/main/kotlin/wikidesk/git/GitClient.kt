package wikidesk.git

import wikidesk.platform.appDataDirectory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import java.io.File

/**
 * Credenciais HTTPS fornecidas pelo usuário ao conectar um repositório
 * privado. Para GitHub/GitLab/Bitbucket, o campo [tokenOrPassword] é
 * normalmente um token de acesso pessoal; [username] pode ficar em branco
 * (nesse caso, o próprio token é reenviado como usuário, convenção aceita
 * pela maioria desses provedores).
 *
 * Não é persistida em disco — vive apenas em memória durante a sessão do
 * app (ver `AppState.gitRemotes`), para permitir o botão "Atualizar" sem
 * pedir a credencial de novo, mas sem introduzir um requisito de
 * armazenamento seguro no MVP.
 */
data class GitCredentials(val username: String, val tokenOrPassword: String)

/** Resultado de uma operação de clone/pull. */
sealed class GitResult {
    data class Success(val localPath: String) : GitResult()
    data class Failure(val message: String) : GitResult()
}

/** Estado observável de uma operação de clone em andamento (ver `AppState.gitCloneState`). */
sealed class GitCloneState {
    data object Idle : GitCloneState()
    data object InProgress : GitCloneState()
    data class Error(val message: String) : GitCloneState()
}

/** Tipo de alteração local de um arquivo em relação ao commit atual (HEAD). */
enum class GitFileChangeKind { MODIFIED, NEW, REMOVED }

/** Um arquivo com alteração local pendente, com caminho relativo à raiz do repositório. */
data class GitFileStatus(val relativePath: String, val kind: GitFileChangeKind)

/**
 * Retrato do estado Git de uma fonte no momento em que foi lido: documentação
 * técnica costuma ficar desatualizada silenciosamente, então esses metadados
 * ajudam o usuário a decidir se pode confiar no que está lendo antes de agir
 * sobre ela.
 */
data class GitSourceStatus(
    val branch: String,
    val commitHash: String,
    val commitAuthor: String,
    val commitEpochSeconds: Long,
    /** Commits locais ainda não enviados para a origem (`git push` pendente). */
    val aheadCount: Int,
    /** Commits na origem que ainda não foram trazidos (`git pull` traria). */
    val behindCount: Int,
    val changedFiles: List<GitFileStatus>
)

/**
 * Cliente Git baseado em JGit (implementação Java pura) em vez do binário
 * `git` do sistema. Duas razões principais para essa escolha, discutidas com
 * o usuário: (1) o app é lançado como binário nativo GUI sem terminal — um
 * `git clone` via subprocesso trava esperando prompt de credencial quando não
 * há TTY; com JGit as credenciais são fornecidas programaticamente; (2)
 * remove a dependência de o usuário ter `git` instalado no PATH.
 */
object GitClient {

    private val sshSessionFactory by lazy {
        val home = File(System.getProperty("user.home"))
        SshdSessionFactoryBuilder()
            .setHomeDirectory(home)
            .setSshDirectory(File(home, ".ssh"))
            .build(null)
    }

    private fun transportConfigCallback() = TransportConfigCallback { transport ->
        if (transport is SshTransport) {
            transport.sshSessionFactory = sshSessionFactory
        }
    }

    /**
     * Clona [remoteUrl] em [destination]. A pasta de destino precisa não
     * existir ou estar vazia (mesma regra do `git clone` de linha de comando).
     */
    fun clone(remoteUrl: String, branch: String?, destination: File, credentials: GitCredentials?): GitResult {
        return runCatching {
            check(!destination.exists() || destination.listFiles().isNullOrEmpty()) {
                "A pasta de destino já existe e não está vazia: ${destination.absolutePath}"
            }
            destination.mkdirs()

            val command = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(destination)
                .setTransportConfigCallback(transportConfigCallback())

            if (!branch.isNullOrBlank()) {
                command.setBranch(branch)
            }
            if (credentials != null) {
                command.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(
                        credentials.username.ifBlank { credentials.tokenOrPassword },
                        credentials.tokenOrPassword
                    )
                )
            }

            command.call().close()
            GitResult.Success(destination.absolutePath)
        }.getOrElse { e -> GitResult.Failure(describeError(e)) }
    }

    /** Atualiza um repositório já clonado em [localPath] com `git pull`. */
    fun pull(localPath: File, credentials: GitCredentials?): GitResult {
        return runCatching {
            Git.open(localPath).use { git ->
                val command = git.pull().setTransportConfigCallback(transportConfigCallback())
                if (credentials != null) {
                    command.setCredentialsProvider(
                        UsernamePasswordCredentialsProvider(
                            credentials.username.ifBlank { credentials.tokenOrPassword },
                            credentials.tokenOrPassword
                        )
                    )
                }
                command.call()
            }
            GitResult.Success(localPath.absolutePath)
        }.getOrElse { e -> GitResult.Failure(describeError(e)) }
    }

    /**
     * Lê o estado atual do repositório em [path]: branch, último commit,
     * autor, quantos commits à frente/atrás da origem, e quais arquivos têm
     * alterações locais pendentes (modificados, novos ou removidos, ainda
     * não commitados). Retorna `null` se [path] não for um repositório Git
     * válido ou não tiver nenhum commit ainda.
     */
    fun readStatus(path: File): GitSourceStatus? = runCatching {
        Git.open(path).use { git ->
            val repository = git.repository
            val branch = repository.branch ?: return@use null
            val headId = repository.resolve("HEAD") ?: return@use null

            val commit = RevWalk(repository).use { walk -> walk.parseCommit(headId) }
            val tracking = runCatching { BranchTrackingStatus.of(repository, branch) }.getOrNull()

            val status = git.status().call()
            val changedByPath = linkedMapOf<String, GitFileChangeKind>()
            (status.modified + status.changed).forEach { changedByPath[it] = GitFileChangeKind.MODIFIED }
            (status.added + status.untracked).forEach { changedByPath[it] = GitFileChangeKind.NEW }
            (status.removed + status.missing).forEach { changedByPath[it] = GitFileChangeKind.REMOVED }

            GitSourceStatus(
                branch = branch,
                commitHash = commit.name.take(7),
                commitAuthor = commit.authorIdent?.name ?: "Desconhecido",
                commitEpochSeconds = commit.commitTime.toLong(),
                aheadCount = tracking?.aheadCount ?: 0,
                behindCount = tracking?.behindCount ?: 0,
                changedFiles = changedByPath.map { (path, kind) -> GitFileStatus(path, kind) }
            )
        }
    }.getOrNull()

    private fun describeError(e: Throwable): String {
        val message = e.message ?: e::class.simpleName ?: "Erro desconhecido"
        return when {
            message.contains("not authorized", ignoreCase = true) ||
                message.contains("authentication", ignoreCase = true) ||
                message.contains("Auth fail", ignoreCase = true) ->
                "Falha de autenticação. Verifique o usuário/token informado ou se sua chave SSH está configurada."

            message.contains("UnknownHostException", ignoreCase = true) ->
                "Não foi possível resolver o endereço do repositório. Verifique a URL e sua conexão."

            message.contains("not found", ignoreCase = true) ->
                "Repositório não encontrado. Verifique a URL e se você tem acesso a ele."

            else -> message
        }
    }
}

/**
 * Detecta se [path] já é um checkout Git (tem uma pasta/arquivo `.git`),
 * independentemente de como foi parar ali — usado para que uma pasta local
 * adicionada como fonte "comum" (`Pasta local`) que por acaso já é um clone
 * Git também ganhe o botão de atualizar (↻), igual a uma fonte adicionada
 * pela aba "Repositório Git".
 */
fun isGitRepository(path: File): Boolean = File(path, ".git").exists()

/**
 * Lê a URL do remoto `origin` configurado em um repositório já clonado, se
 * houver. Usada apenas para fins informativos — a atualização (`git pull`)
 * em si não depende deste valor, já que o JGit usa a configuração de
 * tracking branch já existente no repositório.
 */
fun readOriginUrl(path: File): String? = runCatching {
    Git.open(path).use { git -> git.repository.config.getString("remote", "origin", "url") }
}.getOrNull()

/** Deriva um nome de exibição a partir da URL do repositório (ex.: ".../org/repo.git" → "repo"). */
fun repoNameFromUrl(url: String): String {
    val trimmed = url.trim().removeSuffix("/").removeSuffix(".git")
    val lastSegment = trimmed.substringAfterLast('/').substringAfterLast(':')
    return lastSegment.ifBlank { "repositorio" }
}

/** Nome de pasta seguro (sem caracteres especiais) derivado do nome do repositório. */
private fun sanitizedRepoFolderName(remoteUrl: String): String =
    repoNameFromUrl(remoteUrl).replace(Regex("[^A-Za-z0-9._-]"), "-")

/**
 * Pasta padrão para clonar um repositório quando o usuário não escolhe um
 * destino específico — dentro do diretório de dados do app, nomeada a
 * partir do repositório mais um sufixo curto derivado da URL para evitar
 * colisões entre repositórios com o mesmo nome.
 */
fun defaultCloneDestination(remoteUrl: String): File {
    val suffix = remoteUrl.hashCode().toUInt().toString(16).padStart(8, '0').take(8)
    return File(appDataDirectory(), "${sanitizedRepoFolderName(remoteUrl)}-$suffix")
}

/**
 * Calcula o destino final de um clone dentro de uma pasta-base escolhida
 * pelo usuário: sempre cria uma nova subpasta nomeada a partir do
 * repositório, em vez de exigir que a pasta escolhida já esteja vazia (o
 * usuário aponta apenas "onde" quer o clone, não a pasta final em si).
 */
fun cloneDestinationInFolder(baseDir: File, remoteUrl: String): File =
    File(baseDir, sanitizedRepoFolderName(remoteUrl))

/**
 * Formata um instante (epoch em segundos) como tempo relativo em português,
 * ex.: "há 23 dias" — usado para o commit mais recente de uma fonte Git, já
 * que documentação desatualizada costuma passar despercebida sem esse tipo
 * de pista visual.
 */
fun formatRelativeTime(epochSeconds: Long): String {
    val diffSeconds = (System.currentTimeMillis() / 1000 - epochSeconds).coerceAtLeast(0)
    val minutes = diffSeconds / 60
    val hours = diffSeconds / 3600
    val days = diffSeconds / 86400

    return when {
        diffSeconds < 60 -> "agora mesmo"
        minutes < 60 -> "há ${minutes} minuto${if (minutes != 1L) "s" else ""}"
        hours < 24 -> "há ${hours} hora${if (hours != 1L) "s" else ""}"
        days < 30 -> "há ${days} dia${if (days != 1L) "s" else ""}"
        days < 365 -> (days / 30).let { months -> "há ${months} ${if (months == 1L) "mês" else "meses"}" }
        else -> (days / 365).let { years -> "há ${years} ano${if (years != 1L) "s" else ""}" }
    }
}
