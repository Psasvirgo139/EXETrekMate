package com.trekmate.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
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

    @Inject
    lateinit var qrRenderer: QrCodeRenderer

    private val requiredPermissions: List<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    private var missingPermissions by mutableStateOf<List<String>>(emptyList())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        missingPermissions = results.filterValues { !it }.keys.toList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            TrekMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val missing = missingPermissions
                    if (missing.isNotEmpty()) {
                        PermissionScreen(
                            missingPermissions = missing,
                            onRequestPermissions = { permissionLauncher.launch(requiredPermissions.toTypedArray()) }
                        )
                    } else {
                        TrekMateNavHost(qrRenderer = qrRenderer)
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter { perm ->
            checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            missingPermissions = missing
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
