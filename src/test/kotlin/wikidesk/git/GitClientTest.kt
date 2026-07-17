package wikidesk.git

import org.eclipse.jgit.api.Git
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * Teste de integração real (usa a rede) para validar que o clone via JGit
 * funciona de ponta a ponta: URL pública, sem credenciais, sem depender do
 * binário `git` do sistema. Usa um repositório minúsculo e estável do GitHub
 * só para esse fim.
 */
class GitClientTest {

    @Test
    fun `clone de repositorio publico funciona sem credenciais`() {
        val destination = File(System.getProperty("java.io.tmpdir"), "wikidesk-jgit-test-${System.nanoTime()}")
        try {
            val result = GitClient.clone(
                remoteUrl = "https://github.com/octocat/Hello-World.git",
                branch = null,
                destination = destination,
                credentials = null
            )

            assertIs<GitResult.Success>(result, "Clone deveria ter sucesso, resultado: $result")
            assertTrue(destination.resolve(".git").isDirectory, "Pasta clonada deveria conter .git")
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun `repoNameFromUrl deriva nome a partir da URL`() {
        assertTrue(repoNameFromUrl("https://github.com/org/repo.git") == "repo")
        assertTrue(repoNameFromUrl("git@github.com:org/repo.git") == "repo")
    }

    @Test
    fun `clone em pasta escolhida pelo usuario cria subpasta com nome do repositorio`() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "wikidesk-jgit-basedir-${System.nanoTime()}")
        baseDir.mkdirs()
        try {
            // Simula o usuário escolhendo uma pasta-base já existente (e não vazia,
            // pois já tem outra coisa dentro) — o clone deve criar uma subpasta nova
            // com o nome do repositório em vez de exigir que baseDir esteja vazia.
            File(baseDir, "arquivo-existente.txt").writeText("já tinha algo aqui")

            val destination = cloneDestinationInFolder(baseDir, "https://github.com/octocat/Hello-World.git")
            val result = GitClient.clone(
                remoteUrl = "https://github.com/octocat/Hello-World.git",
                branch = null,
                destination = destination,
                credentials = null
            )

            assertIs<GitResult.Success>(result, "Clone deveria ter sucesso, resultado: $result")
            assertTrue(destination.resolve(".git").isDirectory, "Subpasta clonada deveria conter .git")
            assertTrue(destination.parentFile.absolutePath == baseDir.absolutePath, "Subpasta deveria estar dentro da pasta-base")
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun `readStatus reporta branch, commit e arquivos alterados localmente`() {
        val repoDir = File(System.getProperty("java.io.tmpdir"), "wikidesk-jgit-status-${System.nanoTime()}")
        repoDir.mkdirs()
        try {
            Git.init().setDirectory(repoDir).call().use { git ->
                File(repoDir, "README.md").writeText("conteúdo inicial")
                git.add().addFilepattern(".").call()
                git.commit()
                    .setMessage("commit inicial")
                    .setAuthor("Equipe de Teste", "teste@example.com")
                    .call()

                // Simula alterações locais ainda não commitadas: um arquivo
                // modificado e um arquivo novo (untracked).
                File(repoDir, "README.md").appendText("\nlinha extra")
                File(repoDir, "novo.md").writeText("arquivo novo, ainda não commitado")
            }

            val status = GitClient.readStatus(repoDir)
            assertNotNull(status, "readStatus não deveria retornar null para um repositório Git válido")

            assertTrue(status.branch.isNotBlank(), "branch deveria ser reportada")
            assertEquals(7, status.commitHash.length, "hash curto deveria ter 7 caracteres")
            assertEquals("Equipe de Teste", status.commitAuthor)
            assertTrue(
                status.changedFiles.any { it.relativePath == "README.md" && it.kind == GitFileChangeKind.MODIFIED },
                "README.md deveria aparecer como modificado"
            )
            assertTrue(
                status.changedFiles.any { it.relativePath == "novo.md" && it.kind == GitFileChangeKind.NEW },
                "novo.md deveria aparecer como novo"
            )
        } finally {
            repoDir.deleteRecursively()
        }
    }
}
