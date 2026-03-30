package hanabix.hudble

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HudScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displaysHudSectionsFromReadme() {
        composeTestRule.onNodeWithTag("pace_metric").fetchSemanticsNode()
        composeTestRule.onNodeWithTag("heart_rate_metric").fetchSemanticsNode()
        composeTestRule.onNodeWithTag("cadence_metric").fetchSemanticsNode()
        composeTestRule.onNodeWithTag("distance_value").fetchSemanticsNode()
        composeTestRule.onNodeWithTag("duration_value").fetchSemanticsNode()
        composeTestRule.onNodeWithTag("current_time").assertTextContains("15:47")
        composeTestRule.onNodeWithTag("device_name").assertTextContains("Enduro 2")
        composeTestRule.onNodeWithTag("battery_level").assertTextContains("87%")
    }

    @Test
    fun displaysPreviewMetricValuesAndUnits() {
        composeTestRule.onNodeWithContentDescription("配速").fetchSemanticsNode()
        composeTestRule.onNodeWithContentDescription("心率").fetchSemanticsNode()
        composeTestRule.onNodeWithContentDescription("步频").fetchSemanticsNode()
        composeTestRule.onNodeWithTag("pace_value").assertTextContains("6'21\"")
        composeTestRule.onNodeWithTag("pace_unit").assertTextContains("/km")
        composeTestRule.onNodeWithTag("heart_rate_value").assertTextContains("156")
        composeTestRule.onNodeWithTag("heart_rate_unit").assertTextContains("bpm")
        composeTestRule.onNodeWithTag("cadence_value").assertTextContains("178")
        composeTestRule.onNodeWithTag("cadence_unit").assertTextContains("spm")
        composeTestRule.onNodeWithTag("distance_value").assertTextContains("5.43 km")
        composeTestRule.onNodeWithTag("duration_value").assertTextContains("00:36:18")
    }
}
