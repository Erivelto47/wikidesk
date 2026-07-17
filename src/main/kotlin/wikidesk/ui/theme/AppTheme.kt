package wikidesk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppColors = staticCompositionLocalOf { AppPalette.light }

/**
 * Tema raiz do aplicativo. Fornece [LocalAppColors] para toda a árvore de UI
 * e configura o `MaterialTheme` subjacente (usado apenas por componentes
 * padrão como `TextField`/`Scrollbar`) para que fiquem coerentes com a
 * paleta custom, evitando a aparência genérica de Material Design.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) AppPalette.dark else AppPalette.light

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            background = colors.background,
            surface = colors.panelBackground,
            primary = colors.accent,
            onBackground = colors.text,
            onSurface = colors.text
        )
    } else {
        lightColorScheme(
            background = colors.background,
            surface = colors.panelBackground,
            primary = colors.accent,
            onBackground = colors.text,
            onSurface = colors.text
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
