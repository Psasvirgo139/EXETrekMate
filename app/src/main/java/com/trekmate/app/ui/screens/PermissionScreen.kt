package com.trekmate.app.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Maps technical permission strings to friendly labels for display. */
private fun permissionFriendlyName(perm: String): String = when (perm) {
    Manifest.permission.BLUETOOTH_SCAN      -> "Nearby Devices — Bluetooth Scan"
    Manifest.permission.BLUETOOTH_ADVERTISE -> "Nearby Devices — Bluetooth Advertise"
    Manifest.permission.BLUETOOTH_CONNECT   -> "Nearby Devices — Bluetooth Connect"
    Manifest.permission.BLUETOOTH           -> "Bluetooth"
    Manifest.permission.BLUETOOTH_ADMIN     -> "Bluetooth Admin"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location (required for BLE on Android ≤ 11)"
    Manifest.permission.POST_NOTIFICATIONS  -> "Notifications (for lost-member alerts)"
    else -> perm.substringAfterLast('.')
}

@Composable
fun PermissionScreen(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "TrekMate needs the following permissions to detect group members via Bluetooth:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // List each missing permission with a friendly name
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                missingPermissions.forEach { perm ->
                    Text(
                        "• ${permissionFriendlyName(perm)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "If a permission was already denied, tap \"Open Settings\" and enable it manually.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Settings")
        }
    }
}

