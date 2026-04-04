package hanabix.hudble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hanabix.hudble.R
import hanabix.hudble.ui.theme.HeadUpFitnessTheme
import hanabix.hudble.ui.theme.HudBlack
import hanabix.hudble.ui.theme.HudGreen

data class HudMetric(
    val label: String,
    val value: String,
    val unit: String,
)

data class HudUiState(
    val pace: HudMetric,
    val heartRate: HudMetric,
    val cadence: HudMetric,
    val distance: String,
    val duration: String,
    val currentTime: String,
    val deviceName: String,
    val batteryLevel: String,
) {
    companion object {
        fun preview() = HudUiState(
            pace = HudMetric(label = "配速", value = "6'21\"", unit = "/km"),
            heartRate = HudMetric(label = "心率", value = "156", unit = "bpm"),
            cadence = HudMetric(label = "步频", value = "178", unit = "spm"),
            distance = "5.43 km",
            duration = "00:36:18",
            currentTime = "15:47",
            deviceName = "Enduro 2",
            batteryLevel = "87%",
        )
    }
}

@Composable
fun HudScreen(
    state: HudUiState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(HudBlack),
    ) {
        val scale = minOf(maxWidth.value / 480f, maxHeight.value / 640f)
        val mono = FontFamily.Monospace
        val metricText = 16.sp.scaled(scale)  // text-base = 16px

        Box(modifier = Modifier.fillMaxSize()) {
            // Top metrics row
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 224.dp.scaled(scale))
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                MetricItem(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_hud_timer),
                            contentDescription = state.pace.label,
                            tint = HudGreen,
                            modifier = Modifier
                                .size(18.dp.scaled(scale))
                                .semantics { contentDescription = state.pace.label },
                        )
                    },
                    value = state.pace.value,
                    valueTag = "pace_value",
                    textSize = metricText,
                    mono = mono,
                    scale = scale,
                )

                MetricItem(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_hud_cardiology),
                            contentDescription = state.heartRate.label,
                            tint = HudGreen,
                            modifier = Modifier
                                .size(18.dp.scaled(scale))
                                .semantics { contentDescription = state.heartRate.label },
                        )
                    },
                    value = state.heartRate.value,
                    valueTag = "heart_rate_value",
                    textSize = metricText,
                    mono = mono,
                    scale = scale,
                )

                MetricItem(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_hud_steps),
                            contentDescription = state.cadence.label,
                            tint = HudGreen,
                            modifier = Modifier
                                .size(18.dp.scaled(scale))
                                .semantics { contentDescription = state.cadence.label },
                        )
                    },
                    value = state.cadence.value,
                    valueTag = "cadence_value",
                    textSize = metricText,
                    mono = mono,
                    scale = scale,
                )
            }

            // Bottom info row
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 520.dp.scaled(scale))
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = state.currentTime,
                    color = HudGreen,
                    fontSize = metricText,
                    fontFamily = mono,
                    modifier = Modifier.testTag("current_time"),
                )

                Text(
                    text = state.deviceName,
                    color = HudGreen,
                    fontSize = metricText,
                    fontFamily = mono,
                    modifier = Modifier.testTag("device_name"),
                )

                Text(
                    text = state.batteryLevel,
                    color = HudGreen,
                    fontSize = metricText,
                    fontFamily = mono,
                    modifier = Modifier.testTag("battery_level"),
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    valueTag: String,
    textSize: TextUnit,
    mono: FontFamily,
    scale: Float,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp.scaled(scale)),
    ) {
        icon()
        Text(
            text = value,
            color = HudGreen,
            fontSize = textSize,
            fontFamily = mono,
            maxLines = 1,
            modifier = Modifier.testTag(valueTag),
        )
    }
}

private fun Dp.scaled(scale: Float): Dp = (value * scale).dp

private fun TextUnit.scaled(scale: Float): TextUnit = (value * scale).sp

@Preview(widthDp = 480, heightDp = 640, showBackground = true, backgroundColor = 0x000000)
@Composable
private fun HudScreenPreview() {
    HeadUpFitnessTheme {
        HudScreen(state = HudUiState.preview())
    }
}
