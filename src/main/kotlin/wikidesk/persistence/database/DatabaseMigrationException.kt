package wikidesk.persistence.database

import java.io.File

/**
 * Lançada quando uma migração de schema falha no meio do caminho.
 *
 * O banco original em [databaseFile] nunca chega a ser alterado quando isso
 * acontece — a migração roda inteira em uma cópia de trabalho descartável
 * antes de substituir o arquivo original (ver
 * `DatabaseFactory.migrateWithBackup`) — e [backupFile] é uma cópia adicional
 * de segurança do estado pré-migração, mantida no disco mesmo em caso de
 * sucesso, como segunda rede de proteção.
 */
class DatabaseMigrationException(
    val databaseFile: File,
    val backupFile: File,
    val oldVersion: Long,
    val newVersion: Long,
    cause: Throwable
) : Exception(
    "Falha ao migrar o banco de dados de v$oldVersion para v$newVersion " +
        "(${databaseFile.absolutePath}). O banco original foi preservado sem alterações; " +
        "uma cópia de segurança do estado anterior está em ${backupFile.absolutePath}.",
    cause
)
