package wikidesk.persistence.mapper

import wikidesk.persistence.model.IndexedDocument

/**
 * Converte os campos "crus" de uma linha `document` para [IndexedDocument].
 * Ver `wikidesk.persistence.mapper.mapWikiRow` para o porquê de receber
 * campos individuais em vez do tipo de linha gerado.
 */
fun mapDocumentRow(
    id: Long,
    wikiId: Long,
    relativePath: String,
    title: String,
    contentHash: String,
    fileSize: Long,
    modifiedAt: Long,
    indexedAt: Long,
    deletedAt: Long?
): IndexedDocument = IndexedDocument(
    id = id,
    wikiId = wikiId,
    relativePath = relativePath,
    title = title,
    contentHash = contentHash,
    fileSize = fileSize,
    modifiedAt = modifiedAt,
    indexedAt = indexedAt,
    deletedAt = deletedAt
)
