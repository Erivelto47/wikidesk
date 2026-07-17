package wikidesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import wikidesk.git.GitFileChangeKind
import wikidesk.ui.components.icons.FileGlyph
import wikidesk.ui.components.icons.FolderChevron
import wikidesk.ui.components.icons.FolderGlyph
import wikidesk.ui.theme.AppColors
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors

/** Item de arquivo já preparado para renderização na sidebar. */
data class FileUiItem(
    val id: String,
    val name: String,
    val visible: Boolean,
    val isActive: Boolean,
    /** Alteração local pendente deste arquivo (fontes Git), ou `null` fora de uma fonte Git ou sem alteração. */
    val changeKind: GitFileChangeKind? = null
)

/**
 * Pasta (com seus arquivos e subpastas) já preparada para renderização na
 * sidebar. É recursiva para refletir fielmente a estrutura de diretórios da
 * fonte, em vez de um nível único fixo.
 */
data class FolderUiItem(
    val id: String,
    val name: String,
    val expanded: Boolean,
    val files: List<FileUiItem>,
    val children: List<FolderUiItem> = emptyList()
)

/**
 * Uma fonte (workspace) exibida como um bloco independente na sidebar, com
 * sua própria árvore de pastas/arquivos — suporta o modo multi-fonte
 * (múltiplas pastas locais e, futuramente, repositórios Git abertos ao
 * mesmo tempo).
 */
data class SourceUiItem(
    val id: String,
    val name: String,
    val typeLabel: String,
    val removable: Boolean,
    val rootFiles: List<FileUiItem>,
    val folders: List<FolderUiItem>,
    val isGit: Boolean = false,
    val isRefreshing: Boolean = false,
    val collapsed: Boolean = false,
    val gitStatus: GitStatusUiItem? = null
)

/**
 * Metadados de rastreio Git de uma fonte, já formatados para exibição —
 * documentação técnica costuma ficar desatualizada silenciosamente, então
 * esses dados ajudam a decidir se dá para confiar no conteúdo antes de agir
 * sobre ele. Ver `wikidesk.git.GitSourceStatus` para a versão "crua".
 */
data class GitStatusUiItem(
    val branch: String,
    val commitHash: String,
    val relativeTime: String,
    val author: String,
    val aheadCount: Int,
    val behindCount: Int,
    val modifiedCount: Int,
    val newCount: Int,
    val removedCount: Int
)

private sealed class SidebarRow {
    abstract val depth: Int
    data class FolderRowData(val folder: FolderUiItem, override val depth: Int) : SidebarRow()
    data class FileRowData(val file: FileUiItem, override val depth: Int) : SidebarRow()
    data class EmptyRowData(override val depth: Int, val key: String) : SidebarRow()
}

private fun folderHasVisibleContent(folder: FolderUiItem): Boolean =
    folder.files.any { it.visible } || folder.children.any { folderHasVisibleContent(it) }

private fun flattenRootFiles(rootFiles: List<FileUiItem>): List<SidebarRow> =
    rootFiles.filter { it.visible }.map { SidebarRow.FileRowData(it, depth = 0) }

private fun flattenFolders(folders: List<FolderUiItem>, depth: Int): List<SidebarRow> {
    val result = mutableListOf<SidebarRow>()
    for (folder in folders) {
        result += SidebarRow.FolderRowData(folder, depth)
        if (folder.expanded) {
            if (!folderHasVisibleContent(folder)) {
                result += SidebarRow.EmptyRowData(depth + 1, key = "empty:${folder.id}")
            } else {
                folder.files.filter { it.visible }.forEach { result += SidebarRow.FileRowData(it, depth + 1) }
                result += flattenFolders(folder.children, depth + 1)
            }
        }
    }
    return result
}

/**
 * Barra lateral esquerda: lista de fontes abertas (multi-workspace), cada
 * uma com um cabeçalho (nome, badge de tipo, botão de remover quando
 * aplicável) seguido de sua própria árvore de pastas/arquivos. A navegação é
 * pensada como uma árvore de documentação (não um gerenciador de arquivos
 * genérico): apenas pastas e arquivos Markdown aparecem.
 */
@Composable
fun Sidebar(
    sources: List<SourceUiItem>,
    filterText: String,
    width: Dp,
    modifier: Modifier = Modifier,
    onFilterChange: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onRefreshSource: (String) -> Unit = {},
    onToggleSourceCollapsed: (String) -> Unit = {},
    onResize: (deltaPx: Float) -> Unit
) {
    val colors = LocalAppColors.current

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(colors.sidebarBackground)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = "Fontes",
                style = AppTypography.overline,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
            )

            FilterField(
                value = filterText,
                onValueChange = onFilterChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                sources.forEach { source ->
                    item(key = "source-header:${source.id}") {
                        Column {
                            SourceHeaderRow(
                                source = source,
                                onToggleCollapse = { onToggleSourceCollapsed(source.id) },
                                onRemove = { onRemoveSource(source.id) },
                                onRefresh = { onRefreshSource(source.id) }
                            )
                            // Linha compacta sempre visível (mesmo com a fonte recolhida) —
                            // informação secundária que não deve poluir a árvore, mas precisa
                            // estar disponível de relance para avaliar se o conteúdo é confiável.
                            source.gitStatus?.let { status -> GitStatusLine(status) }
                        }
                    }

                    if (!source.collapsed) {
                        val rows = flattenRootFiles(source.rootFiles) + flattenFolders(source.folders, depth = 0)

                        if (rows.isEmpty()) {
                            item(key = "source-empty:${source.id}") {
                                Text(
                                    text = "Nenhum documento Markdown encontrado nesta pasta.",
                                    style = AppTypography.caption,
                                    color = colors.textFaint,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        items(rows, key = { row ->
                            when (row) {
                                is SidebarRow.FolderRowData -> "folder:${row.folder.id}"
                                is SidebarRow.FileRowData -> "file:${row.file.id}"
                                is SidebarRow.EmptyRowData -> row.key
                            }
                        }) { row ->
                            when (row) {
                                is SidebarRow.FolderRowData -> FolderRow(
                                    folder = row.folder,
                                    depth = row.depth,
                                    onToggleFolder = onToggleFolder
                                )

                                is SidebarRow.FileRowData -> FileRow(
                                    file = row.file,
                                    depth = row.depth,
                                    onOpenFile = onOpenFile
                                )

                                is SidebarRow.EmptyRowData -> Text(
                                    text = "Nenhum resultado",
                                    style = AppTypography.caption,
                                    color = colors.textFaint,
                                    modifier = Modifier.padding(
                                        start = indentFor(row.depth) + 16.dp,
                                        top = 4.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Alça de redimensionamento na borda direita da sidebar.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(5.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x)
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(colors.borderSoft)
        )
    }
}

@Composable
private fun SourceHeaderRow(
    source: SourceUiItem,
    onToggleCollapse: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleCollapse
            )
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FolderChevron(expanded = !source.collapsed, tint = colors.textMuted)
        // `weight(1f)` (fill = true, o padrão) faz este ser o único filho
        // flexível da Row: ele sempre ocupa exatamente o espaço restante
        // depois dos filhos de largura fixa (badge, ↻, ×), então esses
        // botões ficam sempre ancorados na mesma posição (borda direita),
        // independente do tamanho do nome. `maxLines = 1` + `Ellipsis`
        // truncam nomes longos em vez de quebrar linha.
        Text(
            text = source.name,
            style = AppTypography.treeItem.copy(fontWeight = FontWeight.SemiBold),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(source.typeLabel, style = AppTypography.hint, color = colors.textFaint)
        }

        if (source.isGit) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !source.isRefreshing,
                        onClick = onRefresh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    color = if (source.isRefreshing) colors.textFaint else colors.textMuted,
                    style = AppTypography.tabLabel
                )
            }
        }

        if (source.removable) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = colors.textFaint, style = AppTypography.tabLabel)
            }
        }
    }
}

/**
 * Linha compacta "branch · commit · há X dias" abaixo do cabeçalho da fonte.
 * Passar o mouse sobre ela abre um popover com detalhes completos (autor,
 * distância da origem, contagem de arquivos alterados); o popover só some
 * quando o mouse sai tanto da linha quanto do próprio popover — dá tempo de
 * mover o cursor para dentro dele sem que feche no meio do caminho.
 */
@Composable
private fun GitStatusLine(status: GitStatusUiItem) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current

    val lineInteractionSource = remember { MutableInteractionSource() }
    val lineHovered by lineInteractionSource.collectIsHoveredAsState()
    val popoverInteractionSource = remember { MutableInteractionSource() }
    val popoverHovered by popoverInteractionSource.collectIsHoveredAsState()
    val showPopover = lineHovered || popoverHovered

    Box {
        Text(
            text = "${status.branch} · ${status.commitHash} · ${status.relativeTime}",
            style = AppTypography.hint,
            color = colors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .hoverable(lineInteractionSource)
                .padding(start = 8.dp + 6.dp, end = 8.dp, bottom = 6.dp)
        )

        if (showPopover) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { IntOffset(14.dp.roundToPx(), 22.dp.roundToPx()) }
            ) {
                Column(
                    modifier = Modifier
                        .hoverable(popoverInteractionSource)
                        .widthIn(min = 220.dp, max = 260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.modalBackground)
                        .border(width = 1.dp, color = colors.borderStrong, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    GitStatusPopoverRow("Branch", status.branch, colors)
                    GitStatusPopoverRow("Commit", status.commitHash, colors)
                    GitStatusPopoverRow("Autor", status.author, colors)
                    GitStatusPopoverRow("Última alteração", status.relativeTime, colors)
                    GitStatusPopoverRow("Origem", originText(status), colors)
                    GitStatusPopoverRow("Arquivos", filesText(status), colors)
                }
            }
        }
    }
}

@Composable
private fun GitStatusPopoverRow(label: String, value: String, colors: AppColors) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "$label: ", style = AppTypography.caption, color = colors.textMuted)
        Text(text = value, style = AppTypography.caption, color = colors.text)
    }
}

private fun originText(status: GitStatusUiItem): String = when {
    status.behindCount > 0 && status.aheadCount > 0 ->
        "${status.aheadCount} à frente, ${status.behindCount} atrás da origem"
    status.behindCount > 0 -> "${status.behindCount} commit(s) atrás da origem"
    status.aheadCount > 0 -> "${status.aheadCount} commit(s) à frente da origem"
    else -> "em dia com a origem"
}

private fun filesText(status: GitStatusUiItem): String {
    val parts = buildList {
        if (status.modifiedCount > 0) add("${status.modifiedCount} modificado(s)")
        if (status.newCount > 0) add("${status.newCount} novo(s)")
        if (status.removedCount > 0) add("${status.removedCount} removido(s)")
    }
    return parts.ifEmpty { listOf("nenhuma alteração local") }.joinToString(", ")
}

/** Badge colorido M/N/R exibido ao lado de um arquivo com alteração local pendente. */
@Composable
private fun GitChangeBadge(kind: GitFileChangeKind) {
    val colors = LocalAppColors.current
    val (background, foreground, label) = when (kind) {
        GitFileChangeKind.MODIFIED -> Triple(colors.gitModifiedBackground, colors.gitModifiedText, "M")
        GitFileChangeKind.NEW -> Triple(colors.gitNewBackground, colors.gitNewText, "N")
        GitFileChangeKind.REMOVED -> Triple(colors.gitRemovedBackground, colors.gitRemovedText, "R")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(label, style = AppTypography.hint.copy(fontWeight = FontWeight.SemiBold), color = foreground)
    }
}

private fun indentFor(depth: Int): Dp = (8 + depth * 16).dp

@Composable
private fun FolderRow(folder: FolderUiItem, depth: Int, onToggleFolder: (String) -> Unit) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggleFolder(folder.id) }
            )
            .padding(start = indentFor(depth), end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FolderChevron(expanded = folder.expanded, tint = colors.text)
        FolderGlyph(tint = colors.textFaint)
        Text(
            text = folder.name,
            style = AppTypography.treeItem,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FileRow(file: FileUiItem, depth: Int, onOpenFile: (String) -> Unit) {
    val colors = LocalAppColors.current
    val background = if (file.isActive) colors.activeBackground else Color.Transparent
    val textColor = if (file.isActive) colors.accent else colors.textBody
    val iconTint = if (file.isActive) colors.accent else colors.textFaint
    val weight = if (file.isActive) FontWeight.SemiBold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpenFile(file.id) }
            )
            .padding(start = indentFor(depth) + 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FileGlyph(tint = iconTint)
        // `weight(1f)` mantém o badge M/N/R sempre ancorado logo após o nome
        // (truncado com "…" quando necessário), em vez de sua posição
        // depender do tamanho do nome do arquivo — mesmo raciocínio do
        // cabeçalho de fonte.
        Text(
            text = file.name,
            style = AppTypography.treeItem.copy(fontWeight = weight),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        file.changeKind?.let { kind -> GitChangeBadge(kind) }
    }
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.searchBarBackground)
            .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text("Filtrar arquivos", style = AppTypography.caption, color = colors.textFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = AppTypography.caption.fontSize,
                color = colors.text
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
