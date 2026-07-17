package wikidesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wikidesk.ui.components.icons.SearchGlyph
import wikidesk.ui.components.icons.SidebarToggleGlyph
import wikidesk.ui.components.icons.ThemeSwitch
import wikidesk.ui.components.icons.TocToggleGlyph
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors

/**
 * Barra superior do aplicativo. Reúne a ação principal de onboarding de
 * conteúdo ("+ Adicionar fonte"), navegação (voltar/avançar), acesso à busca
 * global, alternância de sidebar/sumário, tema e configurações — conforme a
 * seção "Barra superior" da especificação.
 *
 * Não reproduz os "traffic lights" do protótipo HTML: aquele elemento
 * simulava a janela do sistema operacional dentro do mock estático. Em uma
 * aplicação Compose Desktop real, a decoração da janela já é fornecida pelo
 * próprio SO.
 */
@Composable
fun TopBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    onAddSource: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleSidebar: () -> Unit,
    onToggleToc: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(colors.chromeBackground)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolbarIconButton(onClick = onToggleSidebar, tooltip = "Alternar barra lateral") {
            SidebarToggleGlyph(tint = colors.textMuted)
        }

        ToolbarTextButton(text = "‹", enabled = canGoBack, onClick = onGoBack)
        ToolbarTextButton(text = "›", enabled = canGoForward, onClick = onGoForward)

        SearchBarTrigger(onClick = onOpenSearch, modifier = Modifier.widthIn(max = 480.dp))

        Box(modifier = Modifier.weight(1f))

        AddSourceButton(onClick = onAddSource)

        ToolbarIconButton(onClick = onToggleToc, tooltip = "Alternar sumário") {
            TocToggleGlyph(tint = colors.textMuted)
        }

        ToolbarIconButton(onClick = onOpenSettings, tooltip = "Configurações") {
            Text("⚙", color = colors.textMuted, style = AppTypography.heading3)
        }

        ThemeSwitch(
            isDark = isDarkTheme,
            trackColor = colors.switchTrack,
            knobColor = colors.switchKnob,
            borderColor = colors.border,
            onToggle = onToggleTheme
        )
    }
}

@Composable
private fun RowScope.SearchBarTrigger(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.searchBarBackground)
            .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(7.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchGlyph(tint = colors.textFaint)
        Text(
            text = "Buscar na documentação…",
            style = AppTypography.caption,
            color = colors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text("⌘K", style = AppTypography.hint, color = colors.textFaint)
        }
    }
}

/**
 * Botão primário e evidente de adicionar uma nova fonte de documentação — o
 * único botão colorido da toolbar, para saltar aos olhos como a ação
 * principal de onboarding de conteúdo (spec §11).
 */
@Composable
private fun AddSourceButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ Adicionar fonte",
            style = AppTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

/**
 * Botão-ícone da toolbar com hover real (fundo `hover` do tema ao passar o
 * mouse) — o desktop tem hover de verdade, diferente de mobile, então usamos
 * `Modifier.hoverable` + `interactionSource` em vez de simular apenas o
 * estado de clique (spec §8).
 */
@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    tooltip: String,
    content: @Composable () -> Unit
) {
    // `tooltip` é mantido na assinatura para documentar a intenção de cada botão
    // e para uma futura integração com um componente de Tooltip nativo do Compose.
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered) colors.hover else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ToolbarTextButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered && enabled) colors.hover else Color.Transparent)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.textMuted else colors.textFaint,
            style = AppTypography.heading3
        )
    }
}
