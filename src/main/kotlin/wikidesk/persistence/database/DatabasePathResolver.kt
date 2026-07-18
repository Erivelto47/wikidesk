package wikidesk.persistence.database

import wikidesk.platform.appSupportDirectory
import java.io.File

/**
 * Resolve onde o arquivo SQLite do WikiDesk deve morar em cada sistema
 * operacional. Reaproveita `wikidesk.platform.appSupportDirectory()` — a
 * mesma pasta-base já usada para clones Git padrão e o cache do KCEF — para
 * que o app tenha um único diretório de dados por instalação, não um por
 * subsistema.
 *
 * Deliberadamente nunca resolve para o diretório de instalação do app nem
 * para o diretório de trabalho atual (`user.dir`): ambos podem ser somente-
 * leitura (instalação via .dmg/.msi/.deb) ou variar conforme de onde o
 * processo foi iniciado, o que corromperia a premissa de "uma instalação =
 * um ambiente local" descrita no restante da camada de persistência.
 */
object DatabasePathResolver {

    private const val DATABASE_FILE_NAME = "wikidesk.db"

    /**
     * Caminho do arquivo do banco. Não garante que o arquivo já exista —
     * apenas que o diretório que o conterá exista (ver [ensureDataDirectory]),
     * já que o próprio driver JDBC cria o arquivo `.db` na primeira conexão.
     */
    fun databaseFile(): File = File(ensureDataDirectory(), DATABASE_FILE_NAME)

    /**
     * Diretório de dados da instalação (pasta-mãe do banco). Criado se ainda
     * não existir — inclusive pastas intermediárias, no caso de uma instalação
     * nova em uma máquina onde `Application Support`/`.local/share`/`AppData`
     * do usuário ainda não tenha nenhuma entrada do WikiDesk.
     */
    fun ensureDataDirectory(): File {
        val dir = appSupportDirectory()
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw IllegalStateException(
                "Não foi possível criar o diretório de dados do WikiDesk em ${dir.absolutePath}"
            )
        }
        return dir
    }
}
