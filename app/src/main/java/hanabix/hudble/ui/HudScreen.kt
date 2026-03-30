package hanabix.hudble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
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
        val metricText = 20.sp.scaled(scale)
        val sidePadding = 34.dp.scaled(scale)
        val bottomPadding = 34.dp.scaled(scale)
        val textMeasurer = rememberTextMeasurer()
        val metricTextStyle = remember(metricText, mono) {
            TextStyle(
                fontSize = metricText,
                fontFamily = mono,
            )
        }
        val valueWidth = remember(textMeasurer, metricTextStyle) {
            textMeasurer.measure(
                text = "000000",
                style = metricTextStyle,
            ).size.width
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MetricRow(
                modifier = Modifier
                    .offset(x = sidePadding, y = 30.dp.scaled(scale))
                    .testTag("pace_metric"),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_hud_timer),
                        contentDescription = state.pace.label,
                        tint = HudGreen,
                        modifier = Modifier
                            .size(24.dp.scaled(scale))
                            .semantics { contentDescription = state.pace.label },
                    )
                },
                value = state.pace.value,
                valueTag = "pace_value",
                unit = state.pace.unit,
                unitTag = "pace_unit",
                textSize = metricText,
                mono = mono,
                scale = scale,
                valueWidth = valueWidth.pxToDp(),
            )

            MetricRow(
                modifier = Modifier
                    .offset(x = sidePadding, y = 62.dp.scaled(scale))
                    .testTag("heart_rate_metric"),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_hud_cardiology),
                        contentDescription = state.heartRate.label,
                        tint = HudGreen,
                        modifier = Modifier
                            .size(24.dp.scaled(scale))
                            .semantics { contentDescription = state.heartRate.label },
                    )
                },
                value = state.heartRate.value,
                valueTag = "heart_rate_value",
                unit = state.heartRate.unit,
                unitTag = "heart_rate_unit",
                textSize = metricText,
                mono = mono,
                scale = scale,
                valueWidth = valueWidth.pxToDp(),
            )

            MetricRow(
                modifier = Modifier
                    .offset(x = sidePadding, y = 94.dp.scaled(scale))
                    .testTag("cadence_metric"),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_hud_steps),
                        contentDescription = state.cadence.label,
                        tint = HudGreen,
                        modifier = Modifier
                            .size(24.dp.scaled(scale))
                            .semantics { contentDescription = state.cadence.label },
                    )
                },
                value = state.cadence.value,
                valueTag = "cadence_value",
                unit = state.cadence.unit,
                unitTag = "cadence_unit",
                textSize = metricText,
                mono = mono,
                scale = scale,
                valueWidth = valueWidth.pxToDp(),
            )

            Text(
                text = state.distance,
                color = HudGreen,
                fontSize = metricText,
                fontFamily = mono,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 30.dp.scaled(scale))
                    .padding(end = sidePadding)
                    .testTag("distance_value"),
            )

            Text(
                text = state.duration,
                color = HudGreen,
                fontSize = metricText,
                fontFamily = mono,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 62.dp.scaled(scale))
                    .padding(end = sidePadding)
                    .testTag("duration_value"),
            )

            Text(
                text = state.currentTime,
                color = HudGreen,
                fontSize = metricText,
                fontFamily = mono,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sidePadding, bottom = bottomPadding)
                    .testTag("current_time"),
            )

            Text(
                text = state.deviceName,
                color = HudGreen,
                fontSize = metricText,
                fontFamily = mono,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding)
                    .testTag("device_name"),
            )

            Text(
                text = state.batteryLevel,
                color = HudGreen,
                fontSize = metricText,
                fontFamily = mono,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = sidePadding, bottom = bottomPadding)
                    .testTag("battery_level"),
            )
        }
    }
}

@Composable
private fun MetricRow(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    value: String,
    valueTag: String,
    unit: String,
    unitTag: String,
    textSize: TextUnit,
    mono: FontFamily,
    scale: Float,
    valueWidth: Dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp.scaled(scale)),
    ) {
        Box(
            modifier = Modifier.requiredWidth(24.dp.scaled(scale)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp.scaled(scale)),
        ) {
            Text(
                text = value,
                color = HudGreen,
                fontSize = textSize,
                fontFamily = mono,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .alignByBaseline()
                    .requiredWidth(valueWidth)
                    .testTag(valueTag),
            )
            Text(
                text = unit,
                color = HudGreen,
                fontSize = textSize,
                fontFamily = mono,
                maxLines = 1,
                modifier = Modifier
                    .alignByBaseline()
                    .testTag(unitTag),
            )
        }
    }
}

private fun Dp.scaled(scale: Float): Dp = (value * scale).dp

private fun TextUnit.scaled(scale: Float): TextUnit = (value * scale).sp

@Composable
private fun Int.pxToDp(): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { this@pxToDp.toDp() }
}

@Preview(widthDp = 480, heightDp = 640, showBackground = true, backgroundColor = 0x000000)
@Composable
private fun HudScreenPreview() {
    HeadUpFitnessTheme {
        HudScreen(state = HudUiState.preview())
    }
}
