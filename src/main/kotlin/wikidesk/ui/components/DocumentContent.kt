package wikidesk.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wikidesk.document.LINK_ANNOTATION_TAG
import wikidesk.document.renderInlineMarkdown
import wikidesk.domain.CalloutKind
import wikidesk.domain.MarkdownBlock
import wikidesk.mermaid.MermaidBlockView
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Área central de leitura. Renderiza o título (H1) e os blocos do documento
 * dentro de uma coluna de largura limitada, para manter uma medida de
 * leitura confortável mesmo em janelas largas.
 *
 * Links dentro do texto (`[texto](destino)`) disparam [onLinkClick] com o
 * destino bruto do link (caminho relativo ou URL) — a resolução (documento
 * interno vs. link externo) é responsabilidade de quem chama este composable.
 *
 * [scrollState] é passado de fora (em vez de criado aqui) para que o sumário
 * (TOC) possa ler a posição de rolagem e comandar rolagem programática até
 * uma seção. [onHeadingPositioned] reporta, para cada heading renderizado, sua
 * posição vertical estável dentro do conteúdo — usada pelo TOC para destacar
 * a seção atualmente visível.
 */
@Composable
fun DocumentContent(
    title: String,
    blocks: List<MarkdownBlock>,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    breadcrumb: String? = null,
    /**
     * Linha de frescor ("Última modificação: há 23 dias · main · a3f92d1")
     * exibida logo abaixo do breadcrumb — é o lugar que o usuário realmente
     * olha antes de confiar no conteúdo, já que documentação técnica costuma
     * ficar desatualizada sem nenhum aviso visual. `null` para documentos
     * fora de uma fonte Git.
     */
    freshness: String? = null,
    documentDirectory: String? = null,
    onLinkClick: (String) -> Unit = {},
    onHeadingPositioned: (headingIndex: Int, yOffsetPx: Float) -> Unit = { _, _ -> }
) {
    val colors = LocalAppColors.current
    var scrollContainerTop by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { scrollContainerTop = it.positionInRoot().y }
                .verticalScroll(scrollState)
                .padding(top = 36.dp, bottom = 60.dp, start = 48.dp, end = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                if (!breadcrumb.isNullOrBlank()) {
                    Text(
                        text = breadcrumb,
                        style = AppTypography.caption,
                        color = colors.textFaint,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (!freshness.isNullOrBlank()) {
                    Text(
                        text = freshness,
                        style = AppTypography.caption,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                Text(
                    text = title,
                    style = AppTypography.documentTitle,
                    color = colors.text,
                    modifier = Modifier.padding(bottom = 22.dp)
                )

                var headingIndex = 0
                blocks.forEach { block ->
                    if (block is MarkdownBlock.Heading) {
                        val currentIndex = headingIndex
                        headingIndex++
                        val headingModifier = Modifier.onGloballyPositioned { coordinates ->
                            val stableOffset = coordinates.positionInRoot().y - scrollContainerTop + scrollState.value
                            onHeadingPositioned(currentIndex, stableOffset)
                        }
                        MarkdownBlockView(block, onLinkClick, documentDirectory, headingModifier)
                    } else {
                        MarkdownBlockView(block, onLinkClick, documentDirectory)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    onLinkClick: (String) -> Unit,
    documentDirectory: String?,
    headingModifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    when (block) {
        is MarkdownBlock.Heading -> {
            val style = if (block.level <= 2) AppTypography.heading2 else AppTypography.heading3
            Text(
                text = block.text,
                style = style,
                color = colors.text,
                modifier = headingModifier.padding(top = 32.dp, bottom = 12.dp)
            )
        }

        is MarkdownBlock.Paragraph -> {
            InlineMarkdownText(
                text = block.text,
                style = AppTypography.body,
                color = colors.textBody,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        is MarkdownBlock.BulletList -> {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (block.ordered) "${index + 1}." else "•",
                            style = AppTypography.body,
                            color = colors.textBody
                        )
                        InlineMarkdownText(
                            text = item,
                            style = AppTypography.body,
                            color = colors.textBody,
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
        }

        is MarkdownBlock.Quote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .background(colors.quoteBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(colors.accent)
                )
                InlineMarkdownText(
                    text = block.text,
                    style = AppTypography.quote,
                    color = colors.textMuted,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                )
            }
        }

        is MarkdownBlock.CodeBlock -> {
            CodeBlock(
                language = block.language,
                code = block.code,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }

        is MarkdownBlock.Table -> {
            TableBlockView(block)
        }

        is MarkdownBlock.Image -> {
            ImageBlockView(block, documentDirectory)
        }

        is MarkdownBlock.Mermaid -> {
            MermaidBlockView(block, modifier = Modifier.padding(bottom = 20.dp))
        }

        is MarkdownBlock.Callout -> {
            CalloutBlockView(block, onLinkClick)
        }

        MarkdownBlock.Divider -> {
            HorizontalDivider(
                color = colors.border,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun TableBlockView(table: MarkdownBlock.Table) {
    val colors = LocalAppColors.current
    val columnCount = table.headers.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = colors.codeBorder, shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeaderBackground)
        ) {
            table.headers.forEach { header ->
                Text(
                    text = header,
                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        table.rows.forEachIndexed { rowIndex, row ->
            HorizontalDivider(color = colors.codeBorder)
            Row(modifier = Modifier.fillMaxWidth()) {
                for (columnIndex in 0 until columnCount) {
                    Text(
                        text = row.getOrElse(columnIndex) { "" },
                        style = TextStyle(fontSize = 13.5.sp),
                        color = colors.textBody,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Callout/admonition (nota, atenção, sucesso, erro) — reconhecido tanto no
 * formato GitHub (`> [!NOTE]`) quanto no formato MkDocs (`!!! note "título"`),
 * normalizado para uma destas 4 aparências visuais.
 */
@Composable
private fun CalloutBlockView(callout: MarkdownBlock.Callout, onLinkClick: (String) -> Unit) {
    val colors = LocalAppColors.current
    val (background, accent, icon) = when (callout.kind) {
        CalloutKind.NOTE -> Triple(colors.calloutNoteBackground, colors.calloutNoteAccent, "ℹ")
        CalloutKind.WARNING -> Triple(colors.calloutWarningBackground, colors.calloutWarningAccent, "⚠")
        CalloutKind.SUCCESS -> Triple(colors.calloutSuccessBackground, colors.calloutSuccessAccent, "✓")
        CalloutKind.ERROR -> Triple(colors.calloutErrorBackground, colors.calloutErrorAccent, "✕")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(accent)
        )
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = icon, color = accent, style = AppTypography.body.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = callout.title,
                    style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = accent
                )
            }
            if (callout.text.isNotBlank()) {
                InlineMarkdownText(
                    text = callout.text,
                    style = AppTypography.body,
                    color = colors.textBody,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageBlockView(image: MarkdownBlock.Image, documentDirectory: String?) {
    val colors = LocalAppColors.current
    var bitmap by remember(image.target, documentDirectory) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(image.target, documentDirectory) { mutableStateOf(false) }

    LaunchedEffect(image.target, documentDirectory) {
        bitmap = null
        failed = false

        val isRemote = image.target.startsWith("http://") || image.target.startsWith("https://")
        if (documentDirectory == null || isRemote) {
            failed = true
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                File(documentDirectory, image.target).inputStream().use { loadImageBitmap(it) }
            }.getOrNull()
        }

        if (loaded != null) bitmap = loaded else failed = true
    }

    val currentBitmap = bitmap
    when {
        currentBitmap != null -> {
            Image(
                bitmap = currentBitmap,
                contentDescription = image.alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        failed -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.codeBackground)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Imagem não encontrada: ${image.target}",
                    style = AppTypography.caption,
                    color = colors.textFaint
                )
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.codeBackground)
            )
        }
    }
}

/**
 * Texto com suporte a negrito/itálico/código inline e links clicáveis.
 *
 * Usa `ClickableText` (em vez de `Text` + `LinkAnnotation`) por simplicidade;
 * é uma API estável no Compose 1.7+ usada amplamente para este exato caso.
 */
@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    color: Color,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val annotated = remember(text, colors) { renderInlineMarkdown(text, colors) }

    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_ANNOTATION_TAG, offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}
