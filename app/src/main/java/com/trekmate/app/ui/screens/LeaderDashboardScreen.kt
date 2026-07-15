package com.trekmate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
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
import com.trekmate.app.feature.map.MapViewModel
import com.trekmate.app.feature.map.MapPrepState
import com.trekmate.app.service.AdvertisingState
import com.trekmate.app.service.ScanningState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderDashboardScreen(
    tour: CurrentTour,
    onEndTour: () -> Unit,
    onViewMap: () -> Unit,
    tourViewModel: TourViewModel = hiltViewModel(),
    trackingViewModel: TrackingViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel()
) {
    val members by tourViewModel.members.collectAsState()
    val presenceList by trackingViewModel.presenceList.collectAsState()
    val lostStatus by trackingViewModel.lostStatus.collectAsState()
    val advertisingState by trackingViewModel.advertisingState.collectAsState()
    val scanningState by trackingViewModel.scanningState.collectAsState()
    val scanHitCount by trackingViewModel.scanHitCount.collectAsState()
    val mapPrepState by mapViewModel.mapPrepState.collectAsState()
    val currentUserId by trackingViewModel.currentUserId.collectAsState()
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
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "End Tour")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = if (mapPrepState !is MapPrepState.Idle) 160.dp else 16.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                item { TourInfoCard(tour = tour) }

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

                // Render current user profile (Tôi)
                currentUserId?.let { myId ->
                    val isMeLeader = tour.leaderId == myId
                    item {
                        MyProfileCard(
                            userId = myId,
                            isLeader = isMeLeader,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                val otherMembers = members.filter { it.userId != currentUserId }

                item {
                    Text(
                        "Thành viên khác (${otherMembers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val presenceMap = presenceList.associateBy { it.userId }
                val lostIds = lostStatus?.lostMembers?.map { it.userId }?.toSet() ?: emptySet()

                items(otherMembers) { member ->
                    val presence = presenceMap[member.userId]
                    MemberRow(
                        userId = member.userId,
                        isLeader = member.isLeader,
                        presence = presence,
                        isLost = member.userId in lostIds
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            } // end LazyColumn

            // ── MapPrepCard overlay (unified GPS + map download) ──────────
            MapPrepCard(
                state = mapPrepState,
                onViewMap = onViewMap,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            )
        } // end Box
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

enum class DistanceCategory(val label: String, val color: Color) {
    CLOSE("Gần (Close)", Color(0xFF2E7D32)),             // Green
    IN_RANGE("Vừa (In range)", Color(0xFF1976D2)),         // Blue
    FAR_AWAY("Xa (Far away)", Color(0xFFF57C00)),         // Orange
    DANGER("Nguy cơ cao (Danger)", Color(0xFFD32F2F)),     // Red
    LOST_SIGNAL("Mất tín hiệu (Lost signal)", Color(0xFF757575)), // Gray
    NOT_SEEN_YET("Chưa từng thấy (Not seen yet)", Color(0xFF9E9E9E)) // Light Gray
}

internal fun getDistanceCategory(presence: MemberPresence?): DistanceCategory {
    if (presence == null) return DistanceCategory.NOT_SEEN_YET
    if (!presence.isRecentlySeen) return DistanceCategory.LOST_SIGNAL
    val rssi = presence.lastRssi ?: return DistanceCategory.LOST_SIGNAL
    return when {
        rssi >= -60 -> DistanceCategory.CLOSE
        rssi >= -80 -> DistanceCategory.IN_RANGE
        rssi >= -90 -> DistanceCategory.FAR_AWAY
        else -> DistanceCategory.DANGER
    }
}

@Composable
internal fun MyProfileCard(
    userId: String,
    isLeader: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = userId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("Tôi", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    if (isLeader) {
                        Badge { Text("Leader") }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Đang chia sẻ vị trí qua BLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
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
    val category = getDistanceCategory(presence)
    val containerColor = when {
        isLost -> MaterialTheme.colorScheme.errorContainer
        presence?.isRecentlySeen == true -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(userId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (isLeader) Badge { Text("Leader") }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = category.color,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = category.color
                    )
                    if (presence != null && presence.isRecentlySeen) {
                        Text(
                            text = "(${presence.lastRssi} dBm) • ${formatMs(presence.lastSeenAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    } else if (presence != null && presence.lastSeenAt != null) {
                        Text(
                            text = "• ${formatMs(presence.lastSeenAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
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
