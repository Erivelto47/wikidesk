package wikidesk.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Hierarquia tipográfica do aplicativo.
 *
 * Usa as famílias padrão do sistema (sans-serif e monoespaçada) para não
 * depender de distribuição de fontes comerciais. Quando os arquivos de fonte
 * Inter / JetBrains Mono forem adicionados em `src/main/resources/fonts`,
 * basta trocar `FontFamily.Default` / `FontFamily.Monospace` pelas
 * `FontFamily` carregadas via `Font(resource = ...)` — o restante da
 * hierarquia (tamanhos, pesos, espaçamento) não muda.
 */
object AppTypography {

    private val sans = FontFamily.Default
    private val mono = FontFamily.Monospace

    /** H1 — título do documento. */
    val documentTitle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.4).sp
    )

    /** H2 — seções do documento. */
    val heading2 = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = (-0.1).sp
    )

    /** H3 — subseções do documento. */
    val heading3 = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )

    /** Parágrafo padrão de leitura. */
    val body = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 26.sp
    )

    /** Citações. */
    val quote = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp
    )

    /** Labels em caixa alta (ex.: nome do workspace, "Nesta página"). */
    val overline = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.06.em
    )

    /** Itens da árvore de navegação lateral. */
    val treeItem = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )

    /** Itens do sumário (TOC). */
    val tocItem = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    /** Rótulo das abas de documentos abertos. */
    val tabLabel = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp
    )

    /** Texto secundário / breadcrumbs / metadados discretos. */
    val caption = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )

    /** Texto de dica (atalhos, hints). */
    val hint = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    )

    /** Campo de busca (command palette). */
    val searchInput = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    )

    /** Título de um resultado de busca. */
    val searchResultTitle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp
    )

    /** Subtítulo de um resultado de busca (caminho/pasta). */
    val searchResultSubtitle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp
    )

    /** Código inline dentro de parágrafos. */
    val inlineCode = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp
    )

    /** Blocos de código (multi-linha). */
    val codeBlock = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 20.sp
    )

    /** Cabeçalho do bloco de código (linguagem). */
    val codeBlockLabel = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        letterSpacing = 0.06.em
    )
}
