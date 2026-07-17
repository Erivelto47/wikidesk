package wikidesk.mermaid

/** Resultado de uma tentativa de renderização de um diagrama Mermaid pelo mermaid.js real. */
sealed class MermaidRenderResult {
    data class Success(val svg: String, val width: Double, val height: Double) : MermaidRenderResult()
    data class Failure(val message: String, val line: Int?) : MermaidRenderResult()
}

/** Decodifica o payload JSON (ver `startRender`/`getResult` em [buildMermaidHarnessHtml]) num [MermaidRenderResult]. */
fun parseMermaidRenderResult(json: String): MermaidRenderResult? {
    val fields = MiniJson.parseObject(unwrapJsEvalResult(json))
    if (fields.isEmpty()) return null
    val success = fields.boolOrNull("success") ?: return null
    return if (success) {
        MermaidRenderResult.Success(
            svg = fields.stringOrNull("svg") ?: "",
            width = fields.doubleOrNull("width") ?: 0.0,
            height = fields.doubleOrNull("height") ?: 0.0
        )
    } else {
        MermaidRenderResult.Failure(
            message = fields.stringOrNull("error") ?: "Erro desconhecido ao renderizar o diagrama.",
            line = fields.intOrNull("line")
        )
    }
}
