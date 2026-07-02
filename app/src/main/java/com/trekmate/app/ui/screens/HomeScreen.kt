package com.trekmate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trekmate.app.core.model.CurrentUser

@Composable
fun HomeScreen(
    currentUser: CurrentUser?,
    onCreateTour: () -> Unit,
    onJoinByCode: () -> Unit,
    onJoinByQr: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TrekMate",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Lost detection via Bluetooth",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        currentUser?.let { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Your Identity", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user.userId,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onCreateTour,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Tour (Leader)")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onJoinByCode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Join by Code")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onJoinByQr,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Join by QR Scan")
        }

        if (currentUser == null) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}
