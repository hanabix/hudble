package hanabix.hubu

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import hanabix.hubu.ble.BleConnect
import hanabix.hubu.ble.BleScan
import hanabix.hubu.ble.BleViewModel
import hanabix.hubu.ble.DeviceInfo
import hanabix.hubu.ble.STATUS_CONNECTING
import hanabix.hubu.ble.STATUS_TAP_TO_RECONNECT
import hanabix.hubu.util.Clock
import hanabix.hubu.util.HostBattery
import hanabix.hubu.ui.HUDScreen

class MainActivity : ComponentActivity() {
    private val tapKeyCodes = setOf(
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
    )

    private val hostBattery by lazy { HostBattery(application) }
    private val clock by lazy { Clock() }

    private val viewModel by viewModels<BleViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val scanner = BleScan(application)
                val connector = BleConnect(application)
                return BleViewModel(scanner, connector, DeviceInfo.ScannedDeviceInfo) as T
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.run()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP &&
            event.keyCode in tapKeyCodes &&
            viewModel.bleStatus.value == STATUS_TAP_TO_RECONNECT
        ) {
            viewModel.run()
        }
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            val batteryLevel by remember { hostBattery.observe() }
                .collectAsState(initial = "")
            val currentTime by remember { clock.now() }
                .collectAsState(initial = "")
            val deviceStatus by viewModel.bleStatus.collectAsState(initial = STATUS_CONNECTING)
            val heartRate by viewModel.heartRate.collectAsState()
            val pace by viewModel.pace.collectAsState()
            val cadence by viewModel.cadence.collectAsState()

            HUDScreen(
                pace = pace,
                heartRate = heartRate,
                cadence = cadence,
                currentTime = currentTime,
                deviceStatus = deviceStatus,
                batteryLevel = batteryLevel,
            )
        }
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.run()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
