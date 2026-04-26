package hanabix.hubu.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import hanabix.hubu.R

private val HUDGreen = Color(0xFF00FF55)

@Composable
fun HUDScreen(
    currentTime: String,
    batteryLevel: String,
    deviceStatus: String,
    pace: String?,
    heartRate: String?,
    cadence: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 160.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Bar {
            Metric(
                painter = painterResource(R.drawable.ic_hud_timer),
                value = pace,
                desc = "Pace",
                tag = "pace_value",
            )

            Metric(
                painter = painterResource(R.drawable.ic_hud_cardiology),
                value = heartRate,
                desc = "Heart Rate",
                tag = "heart_rate_value",
            )

            Metric(
                painter = painterResource(R.drawable.ic_hud_steps),
                value = cadence,
                desc = "Cadence",
                tag = "cadence_value",
            )
        }

        Bar {
            Span(currentTime, "current_time")
            Span(deviceStatus, "device_status")
            Span(batteryLevel, "battery_level")
        }
    }
}

@Composable
private fun Bar(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
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
    value: String?,
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
        Span(value ?: "N/A", tag)
    }
}

@Preview(widthDp = 480, heightDp = 640, showBackground = true, backgroundColor = 0x000000)
@Composable
private fun HUDScreenNotFoundPreview() {
    HUDScreen(
        pace = null,
        heartRate = null,
        cadence = null,
        currentTime = "15:47",
        deviceStatus = "Unavailable",
        batteryLevel = "87%",
    )
}


@Preview(widthDp = 480, heightDp = 640, showBackground = true, backgroundColor = 0x000000)
@Composable
private fun HUDScreenScanningPreview() {
    HUDScreen(
        pace = null,
        heartRate = null,
        cadence = null,
        currentTime = "15:47",
        deviceStatus = "Connecting",
        batteryLevel = "87%",
    )
}
