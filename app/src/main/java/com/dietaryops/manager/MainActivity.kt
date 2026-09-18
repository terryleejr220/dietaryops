package com.dietaryops.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.ui.AdaptiveMainLayout
import com.dietaryops.manager.ui.theme.DeliveryScannerTheme

class MainActivity : ComponentActivity() {

    private lateinit var productManager: ProductManager
    private lateinit var printerManager: ZebraPrinterManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase Remote Config on startup
        RemoteConfigManager.initialize()
        RemoteConfigManager.fetchAndActivate()

        productManager = ProductManager(this)
        printerManager = ZebraPrinterManager(this)
        settingsManager = SettingsManager(this)

        setContent {
            DeliveryScannerTheme {
                Surface {
                    val context = LocalContext.current
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        val permsToRequest = mutableListOf(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                permsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                                permsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                            }
                        }
                        if (permsToRequest.isNotEmpty()) {
                            permissionLauncher.launch(permsToRequest.toTypedArray())
                        }

                        // Seed default catalog & expiration rules
                        productManager.seedDefaultsIfEmpty()
                    }

                    AdaptiveMainLayout(
                        productManager = productManager,
                        printerManager = printerManager,
                        settingsManager = settingsManager
                    )
                }
            }
        }
    }
}
