package wikidesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors

/** Item de sumário já preparado para renderização. */
data class TocUiItem(
    val id: String,
    val text: String,
    val level: Int,
    val isActive: Boolean
)

/**
 * Painel direito com o sumário do documento atual ("Nesta página"). O item
 * correspondente à seção visível é discretamente destacado com uma barra de
 * cor e peso de fonte maior, sem competir visualmente com o conteúdo.
 */
@Composable
fun TableOfContents(
    tocItems: List<TocUiItem>,
    width: Dp,
    modifier: Modifier = Modifier,
    onSelectItem: (String) -> Unit,
    onResize: (deltaPx: Float) -> Unit
) {
    val colors = LocalAppColors.current

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(colors.panelBackground)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = "Nesta página",
                style = AppTypography.overline,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 10.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(tocItems, key = { it.id }) { item ->
                    TocRow(item = item, onSelect = { onSelectItem(item.id) })
                }
            }
        }

        // Alça de redimensionamento na borda esquerda do painel.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
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
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(1.dp)
                .background(colors.border)
        )
    }
}

@Composable
private fun TocRow(item: TocUiItem, onSelect: () -> Unit) {
    val colors = LocalAppColors.current
    val barColor = if (item.isActive) colors.accent else androidx.compose.ui.graphics.Color.Transparent
    val textColor = if (item.isActive) colors.text else colors.textMuted
    val weight = if (item.isActive) FontWeight.SemiBold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            )
            .padding(start = (12 + (item.level - 1) * 10).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(barColor)
        )
        Text(
            text = item.text,
            style = AppTypography.tocItem.copy(fontWeight = weight),
            color = textColor,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
