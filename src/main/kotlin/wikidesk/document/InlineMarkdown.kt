package wikidesk.document

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import wikidesk.ui.theme.AppColors

/** Tag usada para anotar spans de link dentro do texto, lida pelo `ClickableText`. */
const val LINK_ANNOTATION_TAG = "wikidesk.link"

/**
 * Regras de formatação inline (dentro de um parágrafo/citação/item de lista):
 * `**negrito**`, `_itálico_`/`*itálico*`, `` `código` `` e `[texto](destino)`.
 *
 * Mantido separado do parser de blocos ([MarkdownParser]) porque produz um
 * [AnnotatedString] (um tipo de UI), enquanto o parser de blocos deve
 * permanecer livre de dependências de Compose.
 */
private val INLINE_REGEX = Regex(
    "\\*\\*(.+?)\\*\\*" + // **negrito**
        "|__(.+?)__" + // __negrito__
        "|`([^`]+?)`" + // `código`
        "|\\[(.+?)]\\((\\S+?)\\)" + // [texto](destino)
        "|\\*(.+?)\\*" + // *itálico*
        "|_(.+?)_" // _itálico_
)

fun renderInlineMarkdown(text: String, colors: AppColors): AnnotatedString = buildAnnotatedString {
    var lastEnd = 0
    for (match in INLINE_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) {
            append(text.substring(lastEnd, match.range.first))
        }
        val g = match.groupValues
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(g[1].ifEmpty { g[2] })
                }
            }
            g[3].isNotEmpty() -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = colors.codeBackground,
                        color = colors.textBody
                    )
                ) {
                    append(g[3])
                }
            }
            g[4].isNotEmpty() -> {
                pushStringAnnotation(tag = LINK_ANNOTATION_TAG, annotation = g[5])
                withStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)) {
                    append(g[4])
                }
                pop()
            }
            g[6].isNotEmpty() || g[7].isNotEmpty() -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(g[6].ifEmpty { g[7] })
                }
            }
            else -> append(match.value)
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}
