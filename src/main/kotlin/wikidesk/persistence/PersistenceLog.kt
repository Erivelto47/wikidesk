package wikidesk.persistence

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Fachada mínima de log para a camada de persistência, usando
 * `java.util.logging` (já embutido na JVM, sem dependência nova). O restante
 * do projeto hoje reporta erros só por valor de retorno (ex.: `GitResult.
 * Failure`) sem logar de fato — ver `wikidesk.git.GitClient` — então este é
 * o primeiro ponto de log do app; centralizado aqui para não espalhar
 * `Logger.getLogger(...)` por vários arquivos.
 *
 * Deliberadamente nunca recebe conteúdo de documento ou caminho completo de
 * arquivo como parâmetro nos call sites — apenas ids, contagens e nomes de
 * wiki — para não vazar dados do usuário no console/arquivo de log da JVM
 * (ver seção de observabilidade da spec de persistência).
 */
object PersistenceLog {
    private val logger = Logger.getLogger("WikiDesk.Persistence")

    fun info(message: String) {
        logger.log(Level.INFO, message)
    }

    fun warn(message: String, error: Throwable? = null) {
        if (error != null) logger.log(Level.WARNING, message, error) else logger.warning(message)
    }

    fun error(message: String, error: Throwable? = null) {
        if (error != null) logger.log(Level.SEVERE, message, error) else logger.severe(message)
    }
}
