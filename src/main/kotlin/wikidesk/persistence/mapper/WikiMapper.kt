package wikidesk.persistence.mapper

import wikidesk.persistence.model.Wiki
import wikidesk.persistence.model.WikiSourceType

/**
 * Converte os campos "crus" de uma linha `wiki` gerada pelo SQLDelight
 * (`sourceType` como `String`, `isActive` como `Long` 0/1 — ver comentário em
 * `Wiki.sq`) para o modelo de domínio tipado [Wiki].
 *
 * Recebe os campos individualmente em vez do tipo de linha gerado
 * (`wikidesk.persistence.database.Wiki`) só para não precisar declarar esse
 * tipo por nome nesta função — evita qualquer ambiguidade entre o nome da
 * tabela SQL e o nome exato da classe que o SQLDelight gera para ela; quem
 * chama (`SqlDelightWikiRepository`) já tem a linha tipada via inferência,
 * então passar os campos aqui é só uma chamada de função comum.
 *
 * Um `sourceType` desconhecido (ex.: banco escrito por uma versão futura com
 * um novo tipo de origem) cai em [WikiSourceType.LOCAL_FOLDER] em vez de
 * lançar exceção — mesma filosofia de tolerância a dados futuros aplicada em
 * `SettingsRepository`.
 */
fun mapWikiRow(
    id: Long,
    name: String,
    rootPath: String,
    normalizedPath: String,
    sourceType: String,
    remoteUrl: String?,
    defaultBranch: String?,
    createdAt: Long,
    updatedAt: Long,
    lastOpenedAt: Long?,
    isActive: Long
): Wiki = Wiki(
    id = id,
    name = name,
    rootPath = rootPath,
    normalizedPath = normalizedPath,
    sourceType = runCatching { WikiSourceType.valueOf(sourceType) }.getOrDefault(WikiSourceType.LOCAL_FOLDER),
    remoteUrl = remoteUrl,
    defaultBranch = defaultBranch,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    isActive = isActive != 0L
)
