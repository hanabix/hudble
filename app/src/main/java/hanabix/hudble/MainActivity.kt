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
                DeviceViewModel(applicationContext, bluetoothScanner) as T
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
        // 保持屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            val batteryLevel by remember { hostBatteryObserver.observe() }
                .collectAsState(initial = "")
            val currentTime by remember { clock.now() }
                .collectAsState(initial = "")
            val deviceStatus by viewModel.deviceStatus.collectAsState(initial = Scanning)
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
            viewModel.startScan()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
