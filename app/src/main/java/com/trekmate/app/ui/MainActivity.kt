package com.trekmate.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.trekmate.app.feature.qr.QrCodeRenderer
import com.trekmate.app.ui.screens.PermissionScreen
import com.trekmate.app.ui.theme.TrekMateTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TrekMatePermission"
    }

    @Inject
    lateinit var qrRenderer: QrCodeRenderer

    // BLE permissions (Nearby Devices group on Android 12+, or Bluetooth + Location on ≤11)
    private val blePermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    // Notification permission — separate group, must be requested AFTER BLE
    private val notifPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    private var missingPermissions by mutableStateOf<List<String>>(emptyList())
    var openAppSettings: () -> Unit = {}

    // Step 2: After BLE group resolved, request Notifications
    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d(TAG, "Notification permission result: $results")
        refreshMissingPermissions()
    }

    // Step 1: Request BLE group first, then chain to notifications
    private val bleLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d(TAG, "BLE permission result: $results")
        // Chain: after BLE group resolved, request notifications if needed
        val missingNotif = notifPermissions.filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missingNotif.isNotEmpty()) {
            Log.d(TAG, "Launching notification permission request: $missingNotif")
            notifLauncher.launch(missingNotif.toTypedArray())
        } else {
            refreshMissingPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openAppSettings = {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }

        checkAndRequestPermissions()

        setContent {
            TrekMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val missing = missingPermissions
                    if (missing.isNotEmpty()) {
                        PermissionScreen(
                            missingPermissions = missing,
                            onRequestPermissions = { checkAndRequestPermissions() },
                            onOpenSettings = openAppSettings
                        )
                    } else {
                        TrekMateNavHost(qrRenderer = qrRenderer)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check when user returns from Settings
        refreshMissingPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingBle = blePermissions.filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        val missingNotif = notifPermissions.filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }

        refreshMissingPermissions()

        when {
            missingBle.isNotEmpty() -> {
                Log.d(TAG, "Launching BLE permission request: $missingBle")
                // bleLauncher callback will chain to notifLauncher automatically
                bleLauncher.launch(missingBle.toTypedArray())
            }
            missingNotif.isNotEmpty() -> {
                Log.d(TAG, "BLE already granted. Launching notification request: $missingNotif")
                notifLauncher.launch(missingNotif.toTypedArray())
            }
            else -> Log.d(TAG, "All permissions already granted")
        }
    }

    private fun refreshMissingPermissions() {
        val allRequired = blePermissions.toList() + notifPermissions.toList()
        missingPermissions = allRequired.filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Missing permissions: $missingPermissions")
    }
}
