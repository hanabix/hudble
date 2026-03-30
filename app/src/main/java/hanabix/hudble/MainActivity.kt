package hanabix.hudble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import hanabix.hudble.ui.HudScreen
import hanabix.hudble.ui.HudUiState
import hanabix.hudble.ui.theme.HeadUpFitnessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeadUpFitnessTheme {
                HudScreen(
                    state = HudUiState.preview(),
                )
            }
        }
    }
}
