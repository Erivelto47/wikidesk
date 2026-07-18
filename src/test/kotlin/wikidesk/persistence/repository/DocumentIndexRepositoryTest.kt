package wikidesk.persistence.repository

import kotlinx.coroutines.runBlocking
import wikidesk.persistence.PersistenceContainer
import wikidesk.persistence.createTempDirectory
import wikidesk.persistence.model.DocumentChunkInput
import wikidesk.persistence.model.WikiSourceType
import wikidesk.persistence.withTempPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentIndexRepositoryTest {

    @Test
    fun `upsertDocument insere e depois atualiza em vez de duplicar`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Docs", createTempDirectory("wikidesk-doc-").absolutePath, WikiSourceType.LOCAL_FOLDER
            )

            val inserted = persistence.documentIndexRepository.upsertDocument(
                wikiId = wiki.id, relativePath = "readme.md", title = "Readme",
                contentHash = "hash-1", fileSize = 100, modifiedAt = 1_000L
            )
            val updated = persistence.documentIndexRepository.upsertDocument(
                wikiId = wiki.id, relativePath = "readme.md", title = "Readme (editado)",
                contentHash = "hash-2", fileSize = 120, modifiedAt = 2_000L
            )

            assertEquals(inserted.id, updated.id, "Mesmo wikiId + relativePath deveria atualizar, não duplicar")
            assertEquals("hash-2", updated.contentHash)
            assertEquals(1, countActiveDocuments(persistence, wiki.id))
        }
    }

    @Test
    fun `findChangedPaths identifica documentos novos e alterados por hash`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Docs", createTempDirectory("wikidesk-hash-").absolutePath, WikiSourceType.LOCAL_FOLDER
            )
            persistence.documentIndexRepository.upsertDocument(
                wiki.id, "a.md", "A", contentHash = "hash-a1", fileSize = 10, modifiedAt = 1L
            )

            val changed = persistence.documentIndexRepository.findChangedPaths(
                wiki.id,
                mapOf("a.md" to "hash-a1", "b.md" to "hash-b1")
            )
            assertEquals(setOf("b.md"), changed, "a.md tem o mesmo hash — só b.md (novo) deveria ser reportado")

            // findChangedPaths compara o hash já persistido (ainda "hash-a1")
            // contra um novo hash "encontrado no disco" — precisa ser chamado
            // ANTES de upsertDocument persistir esse novo hash, senão a
            // comparação vira "hash-a2 contra hash-a2" e nunca acusa mudança
            // (essa é literalmente a checagem que decide o que reindexar).
            val changedAfterEdit = persistence.documentIndexRepository.findChangedPaths(wiki.id, mapOf("a.md" to "hash-a2"))
            assertEquals(setOf("a.md"), changedAfterEdit, "a.md mudou de hash — deveria ser reportado como alterado")

            persistence.documentIndexRepository.upsertDocument(
                wiki.id, "a.md", "A", contentHash = "hash-a2", fileSize = 11, modifiedAt = 2L
            )
        }
    }

    @Test
    fun `replaceChunks substitui os chunks em uma unica transacao`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Docs", createTempDirectory("wikidesk-chunks-").absolutePath, WikiSourceType.LOCAL_FOLDER
            )
            val document = persistence.documentIndexRepository.upsertDocument(
                wiki.id, "guia.md", "Guia", contentHash = "hash-1", fileSize = 500, modifiedAt = 1L
            )

            persistence.documentIndexRepository.replaceChunks(
                document.id,
                listOf(
                    DocumentChunkInput(chunkIndex = 0, headingPath = "Intro", content = "primeiro pedaço", contentHash = "c1"),
                    DocumentChunkInput(chunkIndex = 1, headingPath = "Uso", content = "segundo pedaço", contentHash = "c2")
                )
            )
            assertEquals(2, chunkContents(persistence, document.id).size)

            // Substituição: menos chunks e conteúdo diferente — não deveria
            // "somar" aos anteriores, e sim trocar por completo.
            persistence.documentIndexRepository.replaceChunks(
                document.id,
                listOf(DocumentChunkInput(chunkIndex = 0, headingPath = null, content = "conteúdo totalmente novo", contentHash = "c3"))
            )

            val remaining = chunkContents(persistence, document.id)
            assertEquals(listOf("conteúdo totalmente novo"), remaining)
        }
    }

    @Test
    fun `documentos de wikis diferentes ficam isolados`() = withTempPersistence { persistence ->
        runBlocking {
            val wikiA = persistence.wikiRepository.register(
                "Wiki A", createTempDirectory("wikidesk-iso-a-").absolutePath, WikiSourceType.LOCAL_FOLDER
            )
            val wikiB = persistence.wikiRepository.register(
                "Wiki B", createTempDirectory("wikidesk-iso-b-").absolutePath, WikiSourceType.LOCAL_FOLDER
            )

            // Mesmo relativePath nas duas wikis — permitido, já que o índice
            // único é (wikiId, relativePath), não só relativePath.
            persistence.documentIndexRepository.upsertDocument(wikiA.id, "readme.md", "A", "h1", 10, 1L)
            persistence.documentIndexRepository.upsertDocument(wikiB.id, "readme.md", "B", "h1", 10, 1L)

            assertEquals(1, countActiveDocuments(persistence, wikiA.id))
            assertEquals(1, countActiveDocuments(persistence, wikiB.id))

            persistence.documentIndexRepository.markDeleted(wikiA.id, stillPresentRelativePaths = emptySet())

            assertEquals(0, countActiveDocuments(persistence, wikiA.id), "Marcar removido em A não deveria afetar A")
            assertEquals(1, countActiveDocuments(persistence, wikiB.id), "...nem deveria afetar B")
        }
    }

    @Test
    fun `searchDocuments encontra por texto do conteudo indexado`() = withTempPersistence { persistence ->
        runBlocking {
            val wiki = persistence.wikiRepository.register(
                "Docs", createTempDirectory("wikidesk-search-").absolutePath, WikiSourceType.LOCAL_FOLDER
            )
            val document = persistence.documentIndexRepository.upsertDocument(
                wiki.id, "onboarding.md", "Onboarding", "h1", 200, 1L
            )
            persistence.documentIndexRepository.replaceChunks(
                document.id,
                listOf(DocumentChunkInput(0, "Primeiros passos", "Configure seu ambiente de desenvolvimento local aqui", "c1"))
            )

            val hits = persistence.documentIndexRepository.searchDocuments(wiki.id, "ambiente", limit = 10)

            assertTrue(hits.any { it.documentId == document.id }, "Busca deveria encontrar o documento pelo termo indexado")
        }
    }

    /** Conta direto pela query dedicada exposta pelo SQLDelight (`countActiveByWiki` é uma query de coluna única, devolve o `Long` direto). */
    private fun countActiveDocuments(persistence: PersistenceContainer, wikiId: Long): Int =
        persistence.database.documentQueries.countActiveByWiki(wikiId).executeAsOne().toInt()

    /** 11 colunas em `document_chunk` (ver DocumentChunk.sq): id, documentId, chunkIndex, headingPath, content, contentHash, tokenCount, embeddingModel, embeddingDimension, embeddingBlob, embeddedAt. */
    private fun chunkContents(persistence: PersistenceContainer, documentId: Long): List<String> =
        persistence.database.documentChunkQueries.selectByDocument(
            documentId,
            mapper = { _, _, _, _, content, _, _, _, _, _, _ -> content }
        ).executeAsList()
}
