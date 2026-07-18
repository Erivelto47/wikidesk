package wikidesk.persistence.model

/**
 * Metadados de um documento Markdown indexado — nunca o conteúdo completo do
 * arquivo (ver `document` no schema). `contentHash` é o que permite ao
 * `DocumentIndexRepository` decidir se um arquivo precisa ser reprocessado
 * sem reler/reinterpretar o Markdown inteiro.
 */
data class IndexedDocument(
    val id: Long,
    val wikiId: Long,
    val relativePath: String,
    val title: String,
    val contentHash: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val indexedAt: Long,
    val deletedAt: Long?
)

/**
 * Um pedaço de documento pronto para ser persistido — entrada de
 * `DocumentIndexRepository.replaceChunks`. Os campos de embedding não
 * aparecem aqui de propósito: geração de embeddings ainda não existe (ver
 * schema `document_chunk`); quando existir, os métodos que a preencherem
 * devem operar sobre chunks já persistidos (por id), não neste tipo de
 * entrada "cru".
 */
data class DocumentChunkInput(
    val chunkIndex: Int,
    val headingPath: String?,
    val content: String,
    val contentHash: String,
    val tokenCount: Int? = null
)
