package hanabix.hudble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import hanabix.hudble.R

private val HUDGreen = Color(0xFF00FF55)

data class HUDUiState(
    val pace: String,
    val heartRate: String,
    val cadence: String,
    val currentTime: String,
    val deviceName: String,
    val batteryLevel: String,
) {
    companion object {
        fun preview() = HUDUiState(
            pace = "6'21\"",
            heartRate = "156",
            cadence = "178",
            currentTime = "15:47",
            deviceName = "Enduro 2",
            batteryLevel = "87%",
        )
    }
}

@Composable
fun HUDScreen(
    viewModel: HUDViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    HUDScreenContent(state, modifier)
}

@Composable
fun HUDScreenContent(
    state: HUDUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 160.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top metrics row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(
                painter = painterResource(R.drawable.ic_hud_timer),
                value = state.pace,
                desc = "Pace",
                tag = "pace_value",
            )

            Metric(
                painter = painterResource(R.drawable.ic_hud_cardiology),
                value = state.heartRate,
                desc = "Heart Rate",
                tag = "heart_rate_value",
            )

            Metric(
                painter = painterResource(R.drawable.ic_hud_steps),
                value = state.cadence,
                desc = "Cadence",
                tag = "cadence_value",
            )
        }

        // Bottom info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Span(state.currentTime, "current_time")
            Span(state.deviceName, "device_name")
            Span(state.batteryLevel, "battery_level")
        }
    }
}

@Composable
private fun Span(text: String, tag: String) {
    Text(
        text = text,
        color = HUDGreen,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun Metric(
    modifier: Modifier = Modifier,
    painter: Painter,
    value: String,
    desc: String,
    tag: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = desc,
            tint = HUDGreen,
            modifier = Modifier
                .size(10.dp)
                .semantics { contentDescription = desc },
        )
        Span(value, tag)
    }
}

@Preview(widthDp = 480, heightDp = 640, showBackground = true, backgroundColor = 0x000000)
@Composable
private fun HUDScreenPreview() {
    HUDScreenContent(state = HUDUiState.preview())
}
