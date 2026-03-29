package zhongl.huf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HudColorScheme = darkColorScheme(
    primary = HudGreen,
    onPrimary = HudBlack,
    background = HudBlack,
    onBackground = HudGreen,
    surface = HudBlack,
    onSurface = HudGreen,
)

@Composable
fun HeadUpFitnessTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        content = content,
    )
}
