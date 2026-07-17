package wikidesk.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de cores do aplicativo. Os dois conjuntos ([light] e [dark]) usam a
 * mesma estrutura para garantir que os dois temas tenham exatamente as mesmas
 * proporções e hierarquia visual — apenas os valores de cor mudam.
 *
 * Os valores foram portados diretamente do protótipo visual em
 * `Markdown documentation viewer/Markdown Docs Viewer.dc.html`. Onde o
 * protótipo usava `oklch(...)`, foi feita uma conversão aproximada para sRGB
 * (comentada ao lado de cada valor), já que o design não depende de suporte
 * a espaços de cor Oklch em tempo de execução.
 */
data class AppColors(
    val isDark: Boolean,

    // Superfícies
    val background: Color,
    val sidebarBackground: Color,
    val panelBackground: Color,
    val chromeBackground: Color,
    val tabsBackground: Color,
    val searchBarBackground: Color,
    val quoteBackground: Color,
    val modalBackground: Color,
    val overlay: Color,

    // Texto
    val text: Color,
    val textBody: Color,
    val textMuted: Color,
    val textFaint: Color,

    // Bordas
    /** Divisórias estruturais entre grandes painéis (sidebar↔conteúdo, conteúdo↔sumário, barra de título) — baixo contraste, quase imperceptível. */
    val borderSoft: Color,
    /** Divisórias de conteúdo (linhas de tab, cabeçalho de bloco de código, modal de busca) — mais definida. */
    val border: Color,
    val borderStrong: Color,

    // Estados interativos
    val hover: Color,
    val activeBackground: Color,
    val accent: Color,
    val windowDot: Color,

    // Blocos de código
    val codeBackground: Color,
    val codeHeaderBackground: Color,
    val codeBorder: Color,
    val codeButtonBackground: Color,

    // Interruptor de tema
    val switchTrack: Color,
    val switchKnob: Color,

    // Syntax highlight (código)
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,

    // Badge de linguagem no bloco de código
    val langBadgeBackground: Color,
    val langBadgeText: Color,

    // Badges de status Git por arquivo (M/N/R) e cor de destaque no rodapé de commit
    val gitModifiedBackground: Color,
    val gitModifiedText: Color,
    val gitNewBackground: Color,
    val gitNewText: Color,
    val gitRemovedBackground: Color,
    val gitRemovedText: Color,

    // Callouts/admonitions (ℹ Nota, ⚠ Atenção, ✓ Sucesso, ✕ Erro)
    val calloutNoteBackground: Color,
    val calloutNoteAccent: Color,
    val calloutWarningBackground: Color,
    val calloutWarningAccent: Color,
    val calloutSuccessBackground: Color,
    val calloutSuccessAccent: Color,
    val calloutErrorBackground: Color,
    val calloutErrorAccent: Color
)

object AppPalette {

    val light = AppColors(
        isDark = false,
        background = Color(0xFFFFFFFF),
        sidebarBackground = Color(0xFFF7F7F8),
        panelBackground = Color(0xFFFAFAFA),
        chromeBackground = Color(0xFFF2F2F4),
        tabsBackground = Color(0xFFF7F7F8),
        searchBarBackground = Color(0xFFFFFFFF),
        quoteBackground = Color(0xFFF5F5F9),
        modalBackground = Color(0xFFFFFFFF),
        overlay = Color(0x59141418), // rgba(20,20,24,0.35)

        text = Color(0xFF1C1C1F),
        textBody = Color(0xFF2C2C31),
        textMuted = Color(0xFF6B6B74),
        textFaint = Color(0xFF9C9CA4),

        borderSoft = Color(0xFFEDEDF0),
        border = Color(0xFFE6E6EA),
        borderStrong = Color(0xFFD5D5DB),

        hover = Color(0xFFECEEF1),
        activeBackground = Color(0xFFECEAFC),
        accent = Color(0xFF5E6AD2), // oklch(0.55 0.16 265) aprox.
        windowDot = Color(0xFFC8C8CF),

        codeBackground = Color(0xFFF7F7F9),
        codeHeaderBackground = Color(0xFFF0F0F3),
        codeBorder = Color(0xFFE4E4E9),
        codeButtonBackground = Color(0xFFFFFFFF),

        switchTrack = Color(0xFFE6E6EA),
        switchKnob = Color(0xFFFFFFFF),

        syntaxKeyword = Color(0xFF7B4FE0), // oklch(0.5 0.18 290) aprox.
        syntaxString = Color(0xFF3C9A6D),  // oklch(0.5 0.13 150) aprox.
        syntaxComment = Color(0xFF9C9CA4),

        langBadgeBackground = Color(0xFFE7E6FA), // oklch(0.94 0.03 265) aprox.
        langBadgeText = Color(0xFF5643A6),       // oklch(0.45 0.16 265) aprox.

        gitModifiedBackground = Color(0xFFFBEFD9),
        gitModifiedText = Color(0xFF92660A),
        gitNewBackground = Color(0xFFE1F5E7),
        gitNewText = Color(0xFF1E8A4C),
        gitRemovedBackground = Color(0xFFFCE8E6),
        gitRemovedText = Color(0xFFC23B2E),

        calloutNoteBackground = Color(0xFFE8F0FE),
        calloutNoteAccent = Color(0xFF3568D4),
        calloutWarningBackground = Color(0xFFFBEFD9),
        calloutWarningAccent = Color(0xFF92660A),
        calloutSuccessBackground = Color(0xFFE1F5E7),
        calloutSuccessAccent = Color(0xFF1E8A4C),
        calloutErrorBackground = Color(0xFFFCE8E6),
        calloutErrorAccent = Color(0xFFC23B2E)
    )

    val dark = AppColors(
        isDark = true,
        // Tons mais separados entre si (spec §2): cada superfície um degrau
        // mais escura/clara que a anterior, em vez de quase idênticas.
        background = Color(0xFF1B1C1F),
        sidebarBackground = Color(0xFF212226),
        panelBackground = Color(0xFF1F2023),
        chromeBackground = Color(0xFF1A1B1E),
        tabsBackground = Color(0xFF202124),
        searchBarBackground = Color(0xFF26272B),
        quoteBackground = Color(0xFF232428),
        modalBackground = Color(0xFF232428),
        overlay = Color(0x8C000000), // rgba(0,0,0,0.55)

        text = Color(0xFFE5E5E8),
        textBody = Color(0xFFD3D3D8),
        textMuted = Color(0xFF9A9AA2),
        textFaint = Color(0xFF6F6F78),

        borderSoft = Color(0xFF26272A),
        border = Color(0xFF2F3033),
        borderStrong = Color(0xFF3A3B3F),

        hover = Color(0xFF28292D),
        activeBackground = Color(0xFF2A2A3C),
        accent = Color(0xFF7B86F2), // oklch(0.72 0.15 265) aprox.
        windowDot = Color(0xFF4A4B50),

        codeBackground = Color(0xFF161719),
        codeHeaderBackground = Color(0xFF1E1F22),
        codeBorder = Color(0xFF2F3033),
        codeButtonBackground = Color(0xFF26272B),

        switchTrack = Color(0xFF3A3B3F),
        switchKnob = Color(0xFFE5E5E8),

        syntaxKeyword = Color(0xFFB49CF5), // oklch(0.78 0.13 290) aprox.
        syntaxString = Color(0xFF7FD9AC),  // oklch(0.78 0.12 150) aprox.
        syntaxComment = Color(0xFF6F6F78),

        langBadgeBackground = Color(0xFF34304F), // oklch(0.3 0.06 265) aprox.
        langBadgeText = Color(0xFFC0B0F7),        // oklch(0.8 0.13 265) aprox.

        gitModifiedBackground = Color(0xFF3A2E15),
        gitModifiedText = Color(0xFFE0B45A),
        gitNewBackground = Color(0xFF16301F),
        gitNewText = Color(0xFF6FD897),
        gitRemovedBackground = Color(0xFF3A1917),
        gitRemovedText = Color(0xFFE58579),

        calloutNoteBackground = Color(0xFF1D2A3F),
        calloutNoteAccent = Color(0xFF7CA6F2),
        calloutWarningBackground = Color(0xFF3A2E15),
        calloutWarningAccent = Color(0xFFE0B45A),
        calloutSuccessBackground = Color(0xFF16301F),
        calloutSuccessAccent = Color(0xFF6FD897),
        calloutErrorBackground = Color(0xFF3A1917),
        calloutErrorAccent = Color(0xFFE58579)
    )
}
