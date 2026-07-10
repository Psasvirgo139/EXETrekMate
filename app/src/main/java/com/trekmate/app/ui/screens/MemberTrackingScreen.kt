package com.trekmate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trekmate.app.core.model.CurrentTour
import com.trekmate.app.feature.tour.TourViewModel
import com.trekmate.app.feature.tracking.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberTrackingScreen(
    tour: CurrentTour,
    tourViewModel: TourViewModel = hiltViewModel(),
    trackingViewModel: TrackingViewModel = hiltViewModel()
) {
    val members by tourViewModel.members.collectAsState()
    val presenceList by trackingViewModel.presenceList.collectAsState()
    val lostStatus by trackingViewModel.lostStatus.collectAsState()
    val advertisingState by trackingViewModel.advertisingState.collectAsState()
    val scanningState by trackingViewModel.scanningState.collectAsState()
    val scanHitCount by trackingViewModel.scanHitCount.collectAsState()
    val isPossiblyLost = lostStatus?.isPossiblyLostFromLeader == true

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Group Tracking") })
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
                StatusBanner(isPossiblyLost = isPossiblyLost)
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

            item {
                BleDebugCard(
                    advertisingState = advertisingState,
                    scanningState = scanningState,
                    scanHitCount = scanHitCount
                )
            }

            item {
                Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            val presenceMap = presenceList.associateBy { it.userId }

            items(members) { member ->
                val presence = presenceMap[member.userId]
                MemberRow(
                    userId = member.userId,
                    isLeader = member.isLeader,
                    presence = presence,
                    isLost = false
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatusBanner(isPossiblyLost: Boolean) {
    if (isPossiblyLost) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column {
                    Text(
                        "Possible separation from leader",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "No BLE signal from leader for over 60 seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Connected to group", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
