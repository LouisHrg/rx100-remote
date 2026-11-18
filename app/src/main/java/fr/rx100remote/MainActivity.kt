package fr.rx100remote

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import fr.rx100remote.ui.App
import fr.rx100remote.ui.CameraViewModel

class MainActivity : ComponentActivity() {
    private val vm: CameraViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.log("Permissions : $result")
        vm.connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }

        if (savedInstanceState == null) {
            // INTERNET, *_NETWORK_STATE, *_WIFI_STATE and multicast are install-time permissions;
            // only NEARBY_WIFI_DEVICES (Android 13+) is a runtime one.
            if (Build.VERSION.SDK_INT >= 33) {
                permissionLauncher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
            } else {
                vm.connect()
            }
        }
    }
}
