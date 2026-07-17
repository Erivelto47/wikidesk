package wikidesk.document

import wikidesk.domain.CalloutKind
import wikidesk.domain.DocumentHeading
import wikidesk.domain.MarkdownBlock

/**
 * Parser Markdown propositalmente simples e sem dependências externas.
 *
 * Cobre o que a especificação pede para o MVP (headings, parágrafos, listas,
 * checklists, citações, blocos de código, tabelas, imagens em bloco e
 * divisores). Formatação inline (negrito, itálico, código, links) permanece
 * no texto bruto do parágrafo — quem decora isso visualmente é
 * `wikidesk.document.InlineMarkdown`, usado na camada de UI. Isso mantém o
 * parser de blocos e o parser inline independentes um do outro.
 *
 * Não é um parser CommonMark completo. Tabelas não suportam alinhamento por
 * coluna (`:--`, `--:`, `:-:` são aceitos na linha separadora mas ignorados),
 * e imagens só são reconhecidas quando ocupam a linha inteira sozinhas.
 * Quando essa cobertura for necessária (HTML embutido, referências de link,
 * etc.), este arquivo é o lugar natural para trocar por uma biblioteca real
 * sem afetar o restante do app — todo o resto do código depende apenas de
 * [MarkdownBlock] e [DocumentHeading].
 */
object MarkdownParser {

    data class Result(
        val title: String?,
        val blocks: List<MarkdownBlock>,
        val headings: List<DocumentHeading>
    )

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRegex = Regex("^\\s*[-*+]\\s+(.*)$")
    private val orderedRegex = Regex("^\\s*\\d+[.)]\\s+(.*)$")
    private val quoteRegex = Regex("^>\\s?(.*)$")
    private val fenceRegex = Regex("^```(\\S*)\\s*$")
    private val dividerRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val tableSeparatorRegex = Regex("^\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?$")
    private val imageOnlyLineRegex = Regex("^!\\[(.*?)]\\((\\S+?)(?:\\s+\"[^\"]*\")?\\)$")

    // Callouts/admonitions — duas sintaxes de origem diferentes, normalizadas
    // para as mesmas 4 categorias visuais (ver [CalloutKind]):
    // GitHub: "> [!NOTE]" seguido de mais linhas de blockquote.
    private val calloutGithubMarkerRegex = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*$", RegexOption.IGNORE_CASE)
    // MkDocs/Python-Markdown: "!!! type \"título opcional\"" seguido de um bloco indentado.
    private val calloutMkdocsRegex = Regex("^!!!\\s*(\\S+)\\s*(?:\"([^\"]*)\")?\\s*$")

    fun parse(rawText: String): Result {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        val headings = mutableListOf<DocumentHeading>()
        var title: String? = null

        val paragraphBuffer = StringBuilder()
        val listBuffer = mutableListOf<String>()
        var listOrdered = false

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.toString().trim()))
                paragraphBuffer.clear()
            }
        }

        fun flushList() {
            if (listBuffer.isNotEmpty()) {
                blocks.add(MarkdownBlock.BulletList(listBuffer.toList(), listOrdered))
                listBuffer.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val fenceMatch = fenceRegex.find(line)
            if (fenceMatch != null) {
                flushParagraph()
                flushList()
                val language = fenceMatch.groupValues[1].ifBlank { "text" }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trim() != "```") {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // consome a cerca de fechamento
                val code = codeLines.joinToString("\n")
                if (language.equals("mermaid", ignoreCase = true)) {
                    blocks.add(MarkdownBlock.Mermaid(code))
                } else {
                    blocks.add(MarkdownBlock.CodeBlock(language, code))
                }
                continue
            }

            val headingMatch = headingRegex.find(line)
            if (headingMatch != null) {
                flushParagraph()
                flushList()
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()
                if (level == 1 && title == null) {
                    title = text
                } else {
                    val anchor = slugify(text)
                    blocks.add(MarkdownBlock.Heading(level, text, anchor))
                    headings.add(DocumentHeading(level, text, anchor, blocks.size - 1))
                }
                i++
                continue
            }

            if (dividerRegex.matches(line.trim())) {
                flushParagraph()
                flushList()
                blocks.add(MarkdownBlock.Divider)
                i++
                continue
            }

            val trimmedLine = line.trim()

            val mkdocsMatch = calloutMkdocsRegex.find(trimmedLine)
            if (mkdocsMatch != null) {
                flushParagraph()
                flushList()
                val kind = calloutKindFor(mkdocsMatch.groupValues[1])
                val explicitTitle = mkdocsMatch.groupValues[2].takeIf { it.isNotBlank() }
                i++
                val bodyLines = mutableListOf<String>()
                while (i < lines.size && (lines[i].isBlank() || lines[i].startsWith("    ") || lines[i].startsWith("\t"))) {
                    bodyLines.add(lines[i].removePrefix("\t").let { if (it.startsWith("    ")) it.substring(4) else it })
                    i++
                }
                while (bodyLines.isNotEmpty() && bodyLines.last().isBlank()) bodyLines.removeAt(bodyLines.lastIndex)
                blocks.add(
                    MarkdownBlock.Callout(
                        kind = kind,
                        title = explicitTitle ?: defaultCalloutTitle(kind),
                        text = bodyLines.joinToString(" ") { it.trim() }.trim()
                    )
                )
                continue
            }

            if (trimmedLine.contains("|") && i + 1 < lines.size && tableSeparatorRegex.matches(lines[i + 1].trim())) {
                flushParagraph()
                flushList()
                val headers = splitTableRow(trimmedLine)
                i += 2 // pula linha de cabeçalho e linha separadora (---|---)
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().contains("|") && lines[i].isNotBlank()) {
                    rows.add(splitTableRow(lines[i]))
                    i++
                }
                blocks.add(MarkdownBlock.Table(headers, rows))
                continue
            }

            val imageMatch = imageOnlyLineRegex.find(trimmedLine)
            if (imageMatch != null) {
                flushParagraph()
                flushList()
                blocks.add(MarkdownBlock.Image(alt = imageMatch.groupValues[1], target = imageMatch.groupValues[2]))
                i++
                continue
            }

            val quoteMatch = quoteRegex.find(line)
            if (quoteMatch != null) {
                flushParagraph()
                flushList()
                val quoteLines = mutableListOf(quoteMatch.groupValues[1])
                i++
                while (i < lines.size && quoteRegex.matches(lines[i])) {
                    quoteLines.add(quoteRegex.find(lines[i])!!.groupValues[1])
                    i++
                }

                // Callout no formato GitHub: a primeira linha do blockquote é
                // um marcador "[!NOTE]" (etc.) — o restante das linhas forma o
                // corpo. Uma citação comum não tem esse marcador na 1ª linha.
                val githubMarkerMatch = calloutGithubMarkerRegex.find(quoteLines.first().trim())
                if (githubMarkerMatch != null) {
                    val kind = calloutKindFor(githubMarkerMatch.groupValues[1])
                    blocks.add(
                        MarkdownBlock.Callout(
                            kind = kind,
                            title = defaultCalloutTitle(kind),
                            text = quoteLines.drop(1).joinToString(" ").trim()
                        )
                    )
                } else {
                    blocks.add(MarkdownBlock.Quote(quoteLines.joinToString(" ").trim()))
                }
                continue
            }

            val bulletMatch = bulletRegex.find(line)
            val orderedMatch = if (bulletMatch == null) orderedRegex.find(line) else null
            if (bulletMatch != null || orderedMatch != null) {
                flushParagraph()
                val isOrdered = orderedMatch != null
                if (listBuffer.isNotEmpty() && listOrdered != isOrdered) {
                    flushList()
                }
                listOrdered = isOrdered
                listBuffer.add((bulletMatch ?: orderedMatch)!!.groupValues[1].trim())
                i++
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                flushList()
                i++
                continue
            }

            if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(" ")
            paragraphBuffer.append(line.trim())
            i++
        }

        flushParagraph()
        flushList()

        return Result(title = title, blocks = blocks, headings = headings)
    }

    private fun splitTableRow(line: String): List<String> {
        var content = line.trim()
        if (content.startsWith("|")) content = content.substring(1)
        if (content.endsWith("|")) content = content.substring(0, content.length - 1)
        return content.split("|").map { it.trim() }
    }

    /**
     * Normaliza as dezenas de rótulos usados pelo GitHub (NOTE/TIP/IMPORTANT/
     * WARNING/CAUTION) e pelo MkDocs/Python-Markdown (note, abstract, info,
     * tip, success, warning, danger, bug, etc.) para as 4 categorias visuais
     * suportadas. O mapeamento é necessariamente aproximado — os dois
     * ecossistemas têm semânticas de severidade um pouco diferentes entre si.
     */
    private fun calloutKindFor(rawType: String): CalloutKind = when (rawType.trim().lowercase()) {
        "warning", "caution", "attention" -> CalloutKind.WARNING
        "tip", "hint", "success", "check", "done", "important" -> CalloutKind.SUCCESS
        "danger", "error", "failure", "fail", "missing", "bug" -> CalloutKind.ERROR
        else -> CalloutKind.NOTE // note, info, abstract, summary, tldr, question, help, faq, example, quote, cite, todo...
    }

    private fun defaultCalloutTitle(kind: CalloutKind): String = when (kind) {
        CalloutKind.NOTE -> "Nota"
        CalloutKind.WARNING -> "Atenção"
        CalloutKind.SUCCESS -> "Sucesso"
        CalloutKind.ERROR -> "Erro"
    }

    private fun slugify(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
    }
}
