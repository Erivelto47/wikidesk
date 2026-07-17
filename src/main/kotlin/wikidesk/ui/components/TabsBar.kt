package wikidesk.ui.components

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors

/** Uma aba representando um documento aberto. */
data class TabUiItem(
    val id: String,
    val label: String,
    val isActive: Boolean
)

/**
 * Barra de abas, permitindo manter vários documentos abertos simultaneamente
 * (seção "Abas" da especificação). Não é obrigatória para a primeira
 * entrega, mas já é parte do shell principal para simplificar a evolução
 * futura.
 */
@Composable
fun TabsBar(
    tabs: List<TabUiItem>,
    modifier: Modifier = Modifier,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAllTabs: () -> Unit = {},
    onCloseTabsToRight: (String) -> Unit = {},
    onCloseTabsToLeft: (String) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(colors.tabsBackground)
            .horizontalScroll(scrollState)
    ) {
        tabs.forEach { tab ->
            TabChip(
                tab = tab,
                onSelect = { onSelectTab(tab.id) },
                onClose = { onCloseTab(tab.id) },
                onCloseAll = onCloseAllTabs,
                onCloseToRight = { onCloseTabsToRight(tab.id) },
                onCloseToLeft = { onCloseTabsToLeft(tab.id) }
            )
        }
    }
}

@Composable
private fun TabChip(
    tab: TabUiItem,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onCloseAll: () -> Unit,
    onCloseToRight: () -> Unit,
    onCloseToLeft: () -> Unit
) {
    val colors = LocalAppColors.current
    val background = if (tab.isActive) colors.background else androidx.compose.ui.graphics.Color.Transparent
    val textColor = if (tab.isActive) colors.text else colors.textMuted

    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Fechar", onClose),
                ContextMenuItem("Fechar todas", onCloseAll),
                ContextMenuItem("Fechar todas à direita", onCloseToRight),
                ContextMenuItem("Fechar todas à esquerda", onCloseToLeft)
            )
        }
    ) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 120.dp, max = 200.dp)
            .background(background)
            .border(width = 1.dp, color = colors.border)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = tab.label,
            style = AppTypography.tabLabel.copy(
                fontWeight = if (tab.isActive) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = colors.textFaint, style = AppTypography.tabLabel)
        }
    }
    }
}
