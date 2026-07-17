package wikidesk.document

/**
 * Normaliza HTML embutido em documentos Markdown ANTES do parse de blocos.
 *
 * Documentação real (MkDocs, READMEs do GitHub, wikis exportadas) mistura
 * HTML no Markdown com frequência — botões, cards, `<br>`, `<img>`. O
 * [MarkdownParser] não renderiza HTML, então sem este passo as tags
 * apareceriam cruas no texto. A estratégia aqui é pragmática, no espírito do
 * resto do parser: converter as tags com equivalente Markdown direto
 * (headings, links, imagens, ênfase, código, listas, hr/br) e descartar as
 * demais (`div`, `span`, atributos de estilo...), mantendo apenas o texto
 * interno — o conteúdo continua legível, sem pedaços de HTML no meio.
 *
 * Blocos de código cercados (```) e spans de código inline (`...`) são
 * preservados intactos: HTML dentro deles é conteúdo, não marcação.
 */
object HtmlMarkdown {

    private val commentRegex = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val headingRegex = Regex("<h([1-6])[^>]*>(.*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val anchorRegex = Regex("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val imgRegex = Regex("<img\\s[^>]*/?>", RegexOption.IGNORE_CASE)
    private val srcAttrRegex = Regex("src\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val altAttrRegex = Regex("alt\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val boldRegex = Regex("<(strong|b)>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val italicRegex = Regex("<(em|i)>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val codeTagRegex = Regex("<code[^>]*>(.*?)</code>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val listItemRegex = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val hrRegex = Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE)
    private val paragraphBoundaryRegex = Regex("</(p|div|section|article|blockquote|ul|ol|table|h[1-6])>|<(p|div|section|article|blockquote)[^>]*>", RegexOption.IGNORE_CASE)
    private val anyTagRegex = Regex("</?[a-zA-Z][^>]*>")
    private val inlineCodeSpanRegex = Regex("`[^`\n]+`")

    /** Placeholders improváveis de colidir com conteúdo real (chars de controle). */
    private const val SPAN_PLACEHOLDER_PREFIX = "\u0001WK"
    private const val SPAN_PLACEHOLDER_SUFFIX = "\u0001"

    fun normalize(rawText: String): String {
        if ('<' !in rawText) return rawText

        // Preserva blocos cercados: alterna entre segmentos "texto" e "código"
        // quebrando nas linhas de cerca (```...), e só transforma os de texto.
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val output = StringBuilder(rawText.length)
        val textBuffer = StringBuilder()
        var insideFence = false

        fun flushTextBuffer() {
            if (textBuffer.isNotEmpty()) {
                output.append(normalizeTextSegment(textBuffer.toString()))
                textBuffer.clear()
            }
        }

        for ((index, line) in lines.withIndex()) {
            val isFenceLine = line.trimStart().startsWith("```")
            if (isFenceLine) {
                if (!insideFence) flushTextBuffer()
                insideFence = !insideFence
            }

            if (isFenceLine || insideFence) {
                output.append(line)
            } else {
                textBuffer.append(line)
            }

            val target = if (isFenceLine || insideFence) output else textBuffer
            if (index < lines.lastIndex) target.append('\n')
        }
        flushTextBuffer()

        return output.toString()
    }

    private fun normalizeTextSegment(text: String): String {
        if ('<' !in text) return text

        // Protege spans de código inline: `<div>` dentro de crases é conteúdo
        // que o autor QUER mostrar como texto, não marcação a converter.
        val protectedSpans = mutableListOf<String>()
        var result = inlineCodeSpanRegex.replace(text) { match ->
            protectedSpans.add(match.value)
            "$SPAN_PLACEHOLDER_PREFIX${protectedSpans.lastIndex}$SPAN_PLACEHOLDER_SUFFIX"
        }

        result = commentRegex.replace(result, "")
        result = headingRegex.replace(result) { m ->
            val level = m.groupValues[1].toInt()
            "\n\n${"#".repeat(level)} ${m.groupValues[2].trim()}\n\n"
        }
        result = imgRegex.replace(result) { m ->
            val src = srcAttrRegex.find(m.value)?.groupValues?.get(1)
            val alt = altAttrRegex.find(m.value)?.groupValues?.get(1).orEmpty()
            if (src.isNullOrBlank()) "" else "\n\n![$alt]($src)\n\n"
        }
        result = anchorRegex.replace(result) { m ->
            val href = m.groupValues[1]
            val label = m.groupValues[2].trim()
            if (label.isBlank()) "" else "[$label]($href)"
        }
        result = boldRegex.replace(result) { m -> "**${m.groupValues[2]}**" }
        result = italicRegex.replace(result) { m -> "*${m.groupValues[2]}*" }
        result = codeTagRegex.replace(result) { m -> "`${m.groupValues[1]}`" }
        result = listItemRegex.replace(result) { m -> "\n- ${m.groupValues[1].trim()}" }
        result = brRegex.replace(result, "\n")
        result = hrRegex.replace(result, "\n\n---\n\n")
        result = paragraphBoundaryRegex.replace(result, "\n\n")
        result = anyTagRegex.replace(result, "")
        result = decodeEntities(result)

        // Colapsa o excesso de linhas em branco que as substituições geram.
        result = result.replace(Regex("\n{3,}"), "\n\n")

        protectedSpans.forEachIndexed { index, span ->
            result = result.replace("$SPAN_PLACEHOLDER_PREFIX$index$SPAN_PLACEHOLDER_SUFFIX", span)
        }
        return result
    }

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&") // por último, para não "criar" novas entidades
}
