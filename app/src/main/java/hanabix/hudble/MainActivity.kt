package hanabix.hudble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import hanabix.hudble.data.BluetoothScanner
import hanabix.hudble.data.Clock
import hanabix.hudble.data.GattServices
import hanabix.hudble.data.HostBatteryObserver
import hanabix.hudble.ui.HUDScreen
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private val hostBatteryObserver by lazy { HostBatteryObserver(application) }
    private val clock by lazy { Clock() }
    private val bluetoothScanner by lazy {
        BluetoothScanner(
            application,
            listOf(GattServices.HEART_RATE, GattServices.RUNNING_SPEED_CADENCE),
        )
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val batteryLevel by remember { hostBatteryObserver.observe() }
                .collectAsState(initial = "")
            val currentTime by remember { clock.now() }
                .collectAsState(initial = "")

            var deviceStatus by remember { mutableStateOf("Scanning...") }

            LaunchedEffect(Unit) {
                val device: BluetoothDevice? = bluetoothScanner
                    .scan(10.seconds)
                    .firstOrNull()

                deviceStatus = device?.name ?: "Tap to rescan"
            }

            HUDScreen(
                pace = "6'12\"",
                heartRate = "152",
                cadence = "178",
                currentTime = currentTime,
                deviceStatus = deviceStatus,
                batteryLevel = batteryLevel,
            )
        }
    }
}
