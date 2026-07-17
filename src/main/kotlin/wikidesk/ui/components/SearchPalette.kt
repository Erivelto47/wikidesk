package wikidesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wikidesk.ui.components.icons.SearchGlyph
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors

/** Um resultado exibido na busca global (command palette). */
data class SearchResultUiItem(
    val id: String,
    val title: String,
    val folderName: String
)

/**
 * Busca global no estilo command palette (⌘K / Ctrl+K). Permite localizar
 * documentos por nome, título ou conteúdo, com navegação por teclado.
 *
 * O fechamento por tecla Esc e a navegação ↑/↓ são tratados no nível da
 * janela (ver `Main.kt`), que direciona os eventos de teclado para o estado
 * do aplicativo — este componente é responsável apenas pela apresentação.
 */
@Composable
fun SearchPalette(
    query: String,
    results: List<SearchResultUiItem>,
    sectionLabel: String,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onSelectResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.overlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 120.dp)
                .width(560.dp)
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.modalBackground)
                .border(width = 1.dp, color = colors.borderStrong, shape = RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // absorve cliques para não fechar o modal
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = colors.border)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchGlyph(tint = colors.textFaint)
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Buscar arquivos, títulos, conteúdo…",
                            style = AppTypography.searchInput,
                            color = colors.textFaint
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = AppTypography.searchInput.fontSize,
                            color = colors.text
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
                Box(
                    modifier = Modifier
                        .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("esc", style = AppTypography.hint, color = colors.textFaint)
                }
            }

            val resultsScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(resultsScrollState)
            ) {
                Text(
                    text = sectionLabel.uppercase(),
                    style = AppTypography.hint,
                    color = colors.textFaint,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )

                results.forEach { result ->
                    SearchResultRow(result = result, onClick = { onSelectResult(result.id) })
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = colors.border)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("↑↓ navegar", style = AppTypography.hint, color = colors.textFaint)
                Text("↵ abrir", style = AppTypography.hint, color = colors.textFaint)
                Text("esc fechar", style = AppTypography.hint, color = colors.textFaint)
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResultUiItem, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = result.title,
            style = AppTypography.searchResultTitle,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result.folderName,
            style = AppTypography.searchResultSubtitle,
            color = colors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
