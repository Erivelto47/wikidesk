package wikidesk.persistence.repository

import kotlinx.coroutines.runBlocking
import wikidesk.persistence.createTempDirectory
import wikidesk.persistence.model.WikiSourceType
import wikidesk.persistence.withTempPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiRepositoryTest {

    @Test
    fun `cadastra wiki local`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-local-")
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                name = "Docs",
                rootPath = folder.absolutePath,
                sourceType = WikiSourceType.LOCAL_FOLDER
            )

            assertTrue(wiki.id > 0)
            assertEquals(WikiSourceType.LOCAL_FOLDER, wiki.sourceType)
            assertTrue(wiki.isActive)
            assertNull(wiki.remoteUrl)
        }
    }

    @Test
    fun `cadastra repositorio git`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-git-")
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                name = "Repo Clonado",
                rootPath = folder.absolutePath,
                sourceType = WikiSourceType.GIT_REPOSITORY,
                remoteUrl = "https://example.com/org/repo.git",
                defaultBranch = "main"
            )

            assertEquals(WikiSourceType.GIT_REPOSITORY, wiki.sourceType)
            assertEquals("https://example.com/org/repo.git", wiki.remoteUrl)
            assertEquals("main", wiki.defaultBranch)
        }
    }

    @Test
    fun `registrar o mesmo caminho duas vezes nao duplica`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-dup-")
        runBlocking {
            val first = persistence.wikiRepository.register("Docs", folder.absolutePath, WikiSourceType.LOCAL_FOLDER)
            // Caminho com um segmento "./" redundante — textualmente diferente,
            // mas deveria normalizar para o mesmo caminho canônico.
            val second = persistence.wikiRepository.register(
                "Docs (reaberta)",
                folder.resolve(".").path,
                WikiSourceType.LOCAL_FOLDER
            )

            assertEquals(first.id, second.id, "Não deveria criar uma segunda linha para o mesmo diretório")
            assertEquals(1, persistence.wikiRepository.listActiveWikis().size)
        }
    }

    @Test
    fun `markOpened atualiza o instante da ultima abertura`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-opened-")
        runBlocking {
            val wiki = persistence.wikiRepository.register("Docs", folder.absolutePath, WikiSourceType.LOCAL_FOLDER)
            assertNull(wiki.lastOpenedAt)

            persistence.wikiRepository.markOpened(wiki.id)

            val reloaded = persistence.wikiRepository.findById(wiki.id)
            assertNotNull(reloaded)
            assertNotNull(reloaded.lastOpenedAt)
        }
    }

    @Test
    fun `softDelete remove da listagem ativa mas preserva metadados`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-soft-delete-")
        runBlocking {
            val wiki = persistence.wikiRepository.register("Docs", folder.absolutePath, WikiSourceType.LOCAL_FOLDER)

            persistence.wikiRepository.softDelete(wiki.id)

            assertTrue(persistence.wikiRepository.listActiveWikis().isEmpty())
            val reloaded = persistence.wikiRepository.findById(wiki.id)
            assertNotNull(reloaded)
            assertFalse(reloaded.isActive)
            assertEquals("Docs", reloaded.name, "Metadados devem continuar intactos após remoção lógica")
        }
    }

    @Test
    fun `reactivate volta a aparecer na listagem ativa`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-reactivate-")
        runBlocking {
            val wiki = persistence.wikiRepository.register("Docs", folder.absolutePath, WikiSourceType.LOCAL_FOLDER)
            persistence.wikiRepository.softDelete(wiki.id)

            persistence.wikiRepository.reactivate(wiki.id)

            assertEquals(1, persistence.wikiRepository.listActiveWikis().size)
        }
    }

    @Test
    fun `caminho indisponivel no disco nao afeta os metadados persistidos`() = withTempPersistence { persistence ->
        val folder = createTempDirectory("wikidesk-unavailable-")
        runBlocking {
            val wiki = persistence.wikiRepository.register("Docs", folder.absolutePath, WikiSourceType.LOCAL_FOLDER)

            folder.deleteRecursively()
            assertFalse(folder.exists())

            // A camada de persistência não sabe (nem deveria saber) se o
            // caminho ainda existe no disco — essa checagem é feita por quem
            // consome (ver `wikidesk.ui.app.AppState.bootstrap`), não aqui.
            val reloaded = persistence.wikiRepository.findById(wiki.id)
            assertNotNull(reloaded)
            assertEquals(folder.absolutePath, reloaded.rootPath)
            assertTrue(persistence.wikiRepository.listActiveWikis().any { it.id == wiki.id })
        }
    }
}
