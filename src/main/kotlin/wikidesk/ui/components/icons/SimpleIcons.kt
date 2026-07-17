package wikidesk.ui.components.icons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Pequenos glifos desenhados manualmente (sem depender de uma biblioteca de
 * ícones) para reproduzir fielmente os ícones minimalistas do protótipo
 * visual, que também eram desenhados com bordas simples em vez de um sistema
 * de ícones.
 */

/** Ícone de "alternar sidebar": retângulo com uma divisória vertical perto da borda esquerda. */
@Composable
fun SidebarToggleGlyph(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 14.dp, height = 11.dp)
            .border(width = 1.5.dp, color = tint, shape = RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp)
                .size(width = 1.5.dp, height = 11.dp)
                .background(tint)
        )
    }
}

/**
 * Versão espelhada de [SidebarToggleGlyph], com a divisória vertical perto
 * da borda direita — comunica visualmente "painel da direita" (sumário),
 * em vez de reutilizar o mesmo ícone da sidebar esquerda.
 */
@Composable
fun TocToggleGlyph(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 14.dp, height = 11.dp)
            .border(width = 1.5.dp, color = tint, shape = RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .offset(x = 9.dp)
                .size(width = 1.5.dp, height = 11.dp)
                .background(tint)
        )
    }
}

/** Ícone de busca: lupa simples (círculo + cabo). */
@Composable
fun SearchGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(13.dp)) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - strokeWidth
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(size.width / 2f - 1.5f, size.height / 2f - 1.5f),
            style = Stroke(width = strokeWidth)
        )
        val handleStart = Offset(size.width - 3f, size.height - 3f)
        val handleEnd = Offset(size.width + 1f, size.height + 1f)
        drawLine(color = tint, start = handleStart, end = handleEnd, strokeWidth = strokeWidth)
    }
}

/**
 * Seta de expansão de pasta, rotacionando entre "fechada" (▸) e "aberta" (▾),
 * assim como o `folder.arrowTransform` no protótipo.
 */
@Composable
fun FolderChevron(expanded: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else -45f,
        animationSpec = tween(120)
    )
    // Desenha apenas as bordas direita e inferior de um quadrado (um "canto"),
    // depois rotaciona — o mesmo truque usado no protótipo HTML para formar
    // uma seta de expansão (▸ / ▾) sem depender de uma fonte de ícones.
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(6.dp)
            .rotate(rotation)
    ) {
        val strokeWidth = 1.4.dp.toPx()
        drawLine(
            color = tint,
            start = Offset(size.width, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Marcador discreto de documento (página com canto dobrado), usado ao lado do
 * nome de cada arquivo na árvore. Propositalmente fino e sempre em um tom
 * neutro — a hierarquia (ativo/inativo) é comunicada pelo texto, não pelo
 * ícone, para não competir visualmente com o nome do arquivo.
 */
@Composable
fun FileGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(width = 8.dp, height = 10.dp)) {
        val strokeWidth = 1.dp.toPx()
        val fold = size.width * 0.4f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - fold, 0f)
            lineTo(size.width, fold)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = strokeWidth))
        drawLine(
            color = tint,
            start = Offset(size.width - fold, 0f),
            end = Offset(size.width - fold, fold),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint,
            start = Offset(size.width - fold, fold),
            end = Offset(size.width, fold),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Marcador discreto de pasta (aba + corpo arredondado), usado ao lado do
 * nome de cada pasta na árvore — mesmo alpha/estilo sutil do [FileGlyph],
 * para manter a hierarquia visual comunicada pelo texto, não pelo ícone.
 */
@Composable
fun FolderGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(width = 10.dp, height = 9.dp)) {
        val strokeWidth = 1.dp.toPx()
        val tabWidth = size.width * 0.45f
        val tabHeight = size.height * 0.22f

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, tabHeight)
            lineTo(0f, size.height)
            lineTo(size.width, size.height)
            lineTo(size.width, tabHeight)
            lineTo(tabWidth + 1.5f, tabHeight)
            lineTo(tabWidth - 1.5f, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = strokeWidth))
    }
}

/** Interruptor de tema claro/escuro (pill com bolinha deslizante). */
@Composable
fun ThemeSwitch(
    isDark: Boolean,
    trackColor: Color,
    knobColor: Color,
    borderColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val knobOffset by animateFloatAsState(targetValue = if (isDark) 23f else 3f, animationSpec = tween(150))
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .background(trackColor, RoundedCornerShape(13.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset.dp, y = 2.dp)
                .size(20.dp)
                .background(knobColor, CircleShape)
        )
    }
}
