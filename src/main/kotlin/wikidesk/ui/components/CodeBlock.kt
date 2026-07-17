package wikidesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import wikidesk.ui.theme.AppColors
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

private val KOTLIN_KEYWORDS = setOf(
    "class", "private", "val", "var", "fun", "return", "import", "override", "object",
    "companion", "if", "else", "null", "true", "false", "when", "for", "while", "package",
    "interface", "is", "in", "try", "catch", "finally", "throw", "this", "super", "enum",
    "sealed", "data", "suspend", "public", "internal", "const"
)

private val TOKEN_REGEX = Regex("\"[^\"]*\"|\\b\\w+\\b")

/**
 * Bloco de código com destaque de linguagem, numeração de linhas e botão de
 * copiar — conforme a especificação da área central de leitura.
 *
 * O destaque de sintaxe é intencionalmente simples (palavras-chave e
 * strings), suficiente para leitura confortável sem introduzir uma
 * dependência de highlighter completo no MVP.
 */
@Composable
fun CodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    /** Linha (1-based) a destacar com um fundo de erro — usada pelo fallback de diagramas Mermaid inválidos. */
    highlightLine: Int? = null,
    /**
     * Quando `true`, não desenha o cabeçalho próprio (badge + Copiar) nem a
     * borda/cantos arredondados — só as linhas de código. Usado quando o
     * chamador já tem seu próprio cabeçalho/toolbar (ex.: [wikidesk.mermaid.MermaidBlockView]
     * ao alternar para "Ver código"), para não empilhar duas caixas com
     * cabeçalho uma dentro da outra.
     */
    embedded: Boolean = false
) {
    val colors = LocalAppColors.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val lines = remember(code) { code.split("\n") }
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    Column(
        modifier = if (embedded) {
            modifier.fillMaxWidth()
        } else {
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = colors.codeBorder, shape = RoundedCornerShape(8.dp))
        }
    ) {
        if (!embedded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeHeaderBackground)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.langBadgeBackground)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = language.uppercase(),
                        style = AppTypography.codeBlockLabel,
                        color = colors.langBadgeText
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(5.dp))
                        .background(colors.codeButtonBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(code))
                                copied = true
                            }
                        )
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (copied) "Copiado" else "Copiar",
                        style = AppTypography.hint,
                        color = colors.textMuted
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeBackground)
                .horizontalScroll(horizontalScrollState)
                .padding(vertical = 10.dp)
        ) {
            lines.forEachIndexed { index, line ->
                val lineNumber = index + 1
                val isHighlighted = highlightLine == lineNumber
                Row(
                    modifier = Modifier
                        .background(if (isHighlighted) colors.calloutErrorBackground else Color.Transparent)
                ) {
                    Text(
                        text = lineNumber.toString(),
                        style = AppTypography.codeBlock,
                        color = if (isHighlighted) colors.calloutErrorAccent else colors.textFaint,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                    )
                    Text(
                        text = tokenizeLine(line, language, colors),
                        style = AppTypography.codeBlock,
                        softWrap = false,
                        modifier = Modifier.padding(end = 20.dp)
                    )
                }
            }
        }
    }
}

private fun tokenizeLine(line: String, language: String, colors: AppColors): AnnotatedString {
    val commentMarker = if (language == "bash" || language == "shell") "#" else "//"
    val commentIndex = line.indexOf(commentMarker)
    val codePart = if (commentIndex >= 0) line.substring(0, commentIndex) else line
    val commentPart = if (commentIndex >= 0) line.substring(commentIndex) else ""

    return buildAnnotatedString {
        var lastEnd = 0
        for (match in TOKEN_REGEX.findAll(codePart)) {
            if (match.range.first > lastEnd) {
                append(codePart.substring(lastEnd, match.range.first))
            }
            val token = match.value
            val color = when {
                token.startsWith("\"") -> colors.syntaxString
                language == "kotlin" && token in KOTLIN_KEYWORDS -> colors.syntaxKeyword
                else -> colors.textBody
            }
            withStyle(SpanStyle(color = color)) { append(token) }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < codePart.length) {
            append(codePart.substring(lastEnd))
        }
        if (commentPart.isNotEmpty()) {
            withStyle(SpanStyle(color = colors.syntaxComment)) { append(commentPart) }
        }
        if (length == 0) {
            append(" ")
        }
    }
}
