package hanabix.hudble

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import hanabix.hudble.data.BluetoothScanner
import hanabix.hudble.data.Clock
import hanabix.hudble.data.DeviceStatus
import hanabix.hudble.data.GattServices
import hanabix.hudble.data.HostBatteryObserver
import hanabix.hudble.data.DeviceStatus.Scanning
import hanabix.hudble.ui.HUDScreen

class MainActivity : ComponentActivity() {

    private val hostBatteryObserver by lazy { HostBatteryObserver(application) }
    private val clock by lazy { Clock() }
    private val bluetoothScanner by lazy {
        BluetoothScanner(
            application,
            listOf(GattServices.HEART_RATE, GattServices.RUNNING_SPEED_CADENCE),
        )
    }

    private val viewModel by viewModels<DeviceViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeviceViewModel(bluetoothScanner) as T
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startScan()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        viewModel.onKeyEvent(event)
        return super.dispatchKeyEvent(event)
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
            val deviceStatus by viewModel.deviceStatus.collectAsState(initial = Scanning)

            HUDScreen(
                pace = "6'12\"",
                heartRate = "152",
                cadence = "178",
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
            viewModel.startScan()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
