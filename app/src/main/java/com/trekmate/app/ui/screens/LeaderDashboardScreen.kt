package com.trekmate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.feature.tour.TourViewModel
import com.trekmate.app.feature.tracking.LostDetectionResult
import com.trekmate.app.feature.tracking.TrackingViewModel
import com.trekmate.app.service.AdvertisingState
import com.trekmate.app.service.ScanningState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderDashboardScreen(
    tour: CurrentTour,
    onEndTour: () -> Unit,
    tourViewModel: TourViewModel = hiltViewModel(),
    trackingViewModel: TrackingViewModel = hiltViewModel()
) {
    val members by tourViewModel.members.collectAsState()
    val presenceList by trackingViewModel.presenceList.collectAsState()
    val lostStatus by trackingViewModel.lostStatus.collectAsState()
    val advertisingState by trackingViewModel.advertisingState.collectAsState()
    val scanningState by trackingViewModel.scanningState.collectAsState()
    val scanHitCount by trackingViewModel.scanHitCount.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Tour") },
            text = { Text("This will end the tour for all members. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    tourViewModel.endTour()
                    onEndTour()
                }) { Text("End Tour", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leader Dashboard") },
                actions = {
                    TextButton(
                        onClick = { showEndDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("End Tour") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                TourInfoCard(tour = tour)
            }

            item {
                BleDebugCard(
                    advertisingState = advertisingState,
                    scanningState = scanningState,
                    scanHitCount = scanHitCount
                )
            }

            item {
                lostStatus?.let { result ->
                    if (result.lostMembers.isNotEmpty()) {
                        LostAlert(lostCount = result.lostMembers.size)
                    }
                }
            }

            item {
                Text(
                    "Members (${members.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val presenceMap = presenceList.associateBy { it.userId }
            val lostIds = lostStatus?.lostMembers?.map { it.userId }?.toSet() ?: emptySet()

            items(members) { member ->
                val presence = presenceMap[member.userId]
                MemberRow(
                    userId = member.userId,
                    isLeader = member.isLeader,
                    presence = presence,
                    isLost = member.userId in lostIds
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TourInfoCard(tour: CurrentTour) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Active Tour", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Tour ID:", style = MaterialTheme.typography.bodySmall)
                Text(tour.tourId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Group ID:", style = MaterialTheme.typography.bodySmall)
                Text(tour.groupId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Join Code:", style = MaterialTheme.typography.bodySmall)
                Text(
                    tour.joinCode,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LostAlert(lostCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                "$lostCount member(s) may be lost (no BLE for 60s)",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun MemberRow(
    userId: String,
    isLeader: Boolean,
    presence: MemberPresence?,
    isLost: Boolean
) {
    val containerColor = when {
        isLost -> MaterialTheme.colorScheme.errorContainer
        presence?.isRecentlySeen == true -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = if (isLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(userId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    if (isLeader) Badge { Text("Leader") }
                }
                presence?.let { p ->
                    Text(
                        "RSSI: ${p.lastRssi ?: "?"} dBm  |  Last seen: ${formatMs(p.lastSeenAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text("Not seen yet", style = MaterialTheme.typography.labelSmall)
            }
            if (isLost) {
                Icon(Icons.Default.Warning, contentDescription = "Lost", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatMs(ms: Long?): String {
    if (ms == null) return "never"
    val secAgo = (System.currentTimeMillis() - ms) / 1000
    return "${secAgo}s ago"
}

/**
 * Debug card showing BLE advertising/scanning runtime state.
 * Visible directly on the dashboard — no LogCat needed.
 */
@Composable
internal fun BleDebugCard(
    advertisingState: AdvertisingState,
    scanningState: ScanningState,
    scanHitCount: Int
) {
    val advLabel = when (advertisingState) {
        is AdvertisingState.Running  -> "Running ✓"
        is AdvertisingState.Starting -> "Starting…"
        is AdvertisingState.Failed   -> "Failed ✗"
        is AdvertisingState.Stopped  -> "Stopped"
        is AdvertisingState.Idle     -> "Idle"
    }
    val advColor = when (advertisingState) {
        is AdvertisingState.Running  -> Color(0xFF2E7D32)
        is AdvertisingState.Failed   -> MaterialTheme.colorScheme.error
        else                         -> MaterialTheme.colorScheme.outline
    }
    val scanLabel = when (scanningState) {
        is ScanningState.Running  -> "Running ✓"
        is ScanningState.Starting -> "Starting…"
        is ScanningState.Failed   -> "Failed ✗ — ${(scanningState as ScanningState.Failed).reason}"
        is ScanningState.Stopped  -> "Stopped"
        is ScanningState.Idle     -> "Idle"
    }
    val scanColor = when (scanningState) {
        is ScanningState.Running -> Color(0xFF2E7D32)
        is ScanningState.Failed  -> MaterialTheme.colorScheme.error
        else                     -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "BLE Debug",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Advertise:", style = MaterialTheme.typography.bodySmall)
                Text(advLabel, style = MaterialTheme.typography.bodySmall, color = advColor, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Scan:", style = MaterialTheme.typography.bodySmall)
                Text(scanLabel, style = MaterialTheme.typography.bodySmall, color = scanColor, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Packets received:", style = MaterialTheme.typography.bodySmall)
                Text("$scanHitCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = if (scanHitCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
