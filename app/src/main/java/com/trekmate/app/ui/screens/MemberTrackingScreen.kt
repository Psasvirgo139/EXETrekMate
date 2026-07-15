package com.trekmate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.core.model.MemberPresence
import com.trekmate.app.feature.map.MapPrepState
import com.trekmate.app.feature.map.MapViewModel
import com.trekmate.app.feature.tour.TourViewModel
import com.trekmate.app.feature.tracking.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberTrackingScreen(
    tour: CurrentTour,
    onViewMap: () -> Unit,
    tourViewModel: TourViewModel = hiltViewModel(),
    trackingViewModel: TrackingViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel()
) {
    val members by tourViewModel.members.collectAsState()
    val presenceList by trackingViewModel.presenceList.collectAsState()
    val lostStatus by trackingViewModel.lostStatus.collectAsState()
    val mapPrepState by mapViewModel.mapPrepState.collectAsState()
    val currentUserId by trackingViewModel.currentUserId.collectAsState()
    val isPossiblyLost = lostStatus?.isPossiblyLostFromLeader == true

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Group Tracking") })
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

            val presenceMap = presenceList.associateBy { it.userId }
            val leaderPresence = presenceMap[tour.leaderId]

            item {
                StatusBanner(
                    leaderId = tour.leaderId,
                    leaderPresence = leaderPresence
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tour Info", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Group: ${tour.groupId}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text("Leader: ${tour.leaderId}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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

            items(otherMembers) { member ->
                val presence = presenceMap[member.userId]
                MemberRow(
                    userId = member.userId,
                    isLeader = member.isLeader,
                    presence = presence,
                    isLost = false
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        } // end LazyColumn

        // ── MapPrepCard overlay (unified GPS + map download) ───────────────
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
private fun StatusBanner(
    leaderId: String,
    leaderPresence: MemberPresence?
) {
    var secondsAgo by remember { mutableStateOf<Long?>(null) }

    // Live ticker updating every 1s
    LaunchedEffect(leaderPresence?.lastSeenAt) {
        val lastSeen = leaderPresence?.lastSeenAt
        if (lastSeen == null) {
            secondsAgo = null
            return@LaunchedEffect
        }
        while (true) {
            secondsAgo = (System.currentTimeMillis() - lastSeen) / 1000
            delay(1000L)
        }
    }

    val isLost = leaderPresence != null && leaderPresence.lastSeenAt != null && 
                 (secondsAgo ?: 0L) >= 60L

    val containerColor = when {
        leaderPresence == null -> MaterialTheme.colorScheme.surfaceVariant
        isLost -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val title = when {
        leaderPresence == null -> "Đang kết nối…"
        isLost -> "Cảnh báo: Có khả năng lạc đoàn!"
        else -> "Đang kết nối an toàn"
    }

    val text = when {
        leaderPresence == null -> "Đang tìm kiếm tín hiệu BLE từ Leader ($leaderId)…"
        isLost -> "Mất tín hiệu từ Leader $leaderId được ${secondsAgo ?: 60} giây."
        else -> {
            val sec = secondsAgo ?: 0L
            if (sec <= 0L) "Đang đi cùng Leader (vừa cập nhật)."
            else "Đang nhận tín hiệu tốt từ Leader (lần cuối thấy: ${sec} giây trước)."
        }
    }

    val icon = when {
        leaderPresence == null -> Icons.Default.LocationOn
        isLost -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }

    val iconColor = when {
        leaderPresence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        isLost -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val textColor = when {
        leaderPresence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        isLost -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
