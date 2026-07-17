package wikidesk.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Indica se algum overlay de tela cheia (busca global, modal "Adicionar
 * fonte") está visível no momento.
 *
 * Existe por causa do WebView embutido dos diagramas Mermaid: ele é um
 * componente AWT/Swing pesado que a plataforma Desktop do Compose desenha
 * SEMPRE por cima de qualquer conteúdo Compose — inclusive por cima de
 * modais e da busca. Não há z-index que resolva isso do lado Compose; a
 * única saída é o próprio WebView se recolher (altura zero, sem sair da
 * composição, para não destruir o diagrama já renderizado) enquanto um
 * overlay estiver aberto. Ver `wikidesk.mermaid.MermaidBlockView`.
 */
val LocalOverlayVisible = compositionLocalOf { false }
