package wikidesk.ui.app

import kotlinx.coroutines.runBlocking
import wikidesk.persistence.PersistenceContainer
import wikidesk.persistence.createTempDatabaseFile
import wikidesk.persistence.createTempDirectory
import wikidesk.persistence.deleteDatabaseFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateDisposeRaceTest {

    /**
     * Reproduz, com as classes reais (não um mock), o bug relatado pelo
     * usuário no app empacotado (.dmg): selecionar uma pasta e fechar a
     * janela logo em seguida fazia o registro da wiki nunca ser gravado.
     *
     * Causa: `AppState.addLocalSource` grava no banco em segundo plano
     * (`persistWikiRegistration`, em `persistenceScope`) para não travar a
     * sidebar — mas `AppState.dispose()` (chamado por `onCloseRequest` no
     * fechamento da janela, ver Main.kt) cancelava esse escopo e fechava a
     * conexão imediatamente, sem esperar esse job assíncrono terminar. Se o
     * usuário fechasse rápido o suficiente (exatamente o cenário natural de
     * "selecionar uma pasta para testar se persiste"), o `INSERT` nunca
     * chegava a acontecer. `dispose()` agora espera (com timeout de
     * segurança) os jobs pendentes antes de cancelar/fechar.
     */
    @Test
    fun `fechar o app logo apos selecionar uma pasta nao perde o registro da wiki`() {
        val dbFile = createTempDatabaseFile()
        val wikiFolder = createTempDirectory("wikidesk-race-")
        File(wikiFolder, "index.md").writeText("# Wiki de teste")

        try {
            val persistence = PersistenceContainer.createAt(dbFile)
            val appState = AppState(persistence)

            appState.addLocalSource(wikiFolder.absolutePath)
            // Sem esperar nada de propósito — simula o usuário fechando a
            // janela imediatamente após selecionar a pasta.
            appState.dispose()

            val reopened = PersistenceContainer.createAt(dbFile)
            try {
                val wikis = runBlocking { reopened.wikiRepository.listActiveWikis() }
                assertEquals(1, wikis.size, "A wiki selecionada deveria ter sido gravada antes do banco fechar")
                assertEquals(wikiFolder.absolutePath, wikis.single().rootPath)
            } finally {
                reopened.close()
            }
        } finally {
            deleteDatabaseFiles(dbFile)
            wikiFolder.deleteRecursively()
        }
    }
}
