package wikidesk.persistence.repository

import kotlinx.coroutines.runBlocking
import wikidesk.persistence.createTempDirectory
import wikidesk.persistence.model.WikiGitState
import wikidesk.persistence.model.WikiSourceType
import wikidesk.persistence.withTempPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitStateRepositoryTest {

    @Test
    fun `getSnapshot retorna null quando nunca houve leitura`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Repo", createTempDirectory("wikidesk-gitstate-").absolutePath, WikiSourceType.GIT_REPOSITORY
            )

            assertNull(persistence.gitStateRepository.getSnapshot(wiki.id))
        }
    }

    @Test
    fun `salva e le um snapshot de estado limpo`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Repo", createTempDirectory("wikidesk-gitstate-clean-").absolutePath, WikiSourceType.GIT_REPOSITORY
            )

            persistence.gitStateRepository.saveSnapshot(
                WikiGitState(
                    wikiId = wiki.id,
                    currentBranch = "main",
                    headCommit = "abc1234",
                    remoteHeadCommit = "abc1234",
                    hasLocalChanges = false,
                    aheadCount = 0,
                    behindCount = 0,
                    lastFetchAt = 1_000L,
                    lastScanAt = 1_000L
                )
            )

            val snapshot = persistence.gitStateRepository.getSnapshot(wiki.id)
            assertNotNull(snapshot)
            assertEquals("main", snapshot.currentBranch)
            assertEquals("abc1234", snapshot.headCommit)
            assertEquals(false, snapshot.hasLocalChanges, "Estado limpo deveria ser distinguível de desconhecido")
        }
    }

    @Test
    fun `invalidate zera os campos mas mantem a linha, distinguindo de nunca lido`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Repo", createTempDirectory("wikidesk-gitstate-invalidate-").absolutePath, WikiSourceType.GIT_REPOSITORY
            )
            persistence.gitStateRepository.saveSnapshot(
                WikiGitState(
                    wikiId = wiki.id,
                    currentBranch = "main",
                    headCommit = "abc1234",
                    remoteHeadCommit = null,
                    hasLocalChanges = true,
                    aheadCount = 1,
                    behindCount = 2,
                    lastFetchAt = null,
                    lastScanAt = 1_000L
                )
            )

            persistence.gitStateRepository.invalidate(wiki.id)

            val snapshot = persistence.gitStateRepository.getSnapshot(wiki.id)
            assertNotNull(snapshot, "Invalidar não deveria apagar a linha — só torná-la desconhecida")
            assertNull(snapshot.currentBranch)
            assertNull(snapshot.headCommit)
            assertNull(snapshot.hasLocalChanges)
            assertTrue(snapshot.lastScanAt >= 1_000L)
        }
    }
}
