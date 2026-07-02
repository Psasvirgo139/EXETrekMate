package com.trekmate.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.core.time.ClockProvider
import com.trekmate.app.feature.auth.AuthRepository
import com.trekmate.app.feature.notification.TrekMateNotificationManager
import com.trekmate.app.feature.notification.TrekMateNotificationManagerImpl
import com.trekmate.app.feature.tour.TourRepository
import com.trekmate.app.feature.tracking.LostDetectionEngine
import com.trekmate.app.feature.tracking.LostDetectionInput
import com.trekmate.app.feature.tracking.PresenceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class TrekMateBleService : Service() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var tourRepository: TourRepository
    @Inject lateinit var presenceRepository: PresenceRepository
    @Inject lateinit var lostDetectionEngine: LostDetectionEngine
    @Inject lateinit var advertiserController: BleAdvertiserController
    @Inject lateinit var scannerController: BleScannerController
    @Inject lateinit var notificationManager: TrekMateNotificationManager
    @Inject lateinit var clock: ClockProvider

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lostDetectionJob: Job? = null

    companion object {
        private const val TAG = "TrekMateBleService"
        const val ACTION_START = "com.trekmate.app.ACTION_START_BLE"
        const val ACTION_STOP = "com.trekmate.app.ACTION_STOP_BLE"
        private const val LOST_EVAL_INTERVAL_MS = 10_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForeground(
            TrekMateNotificationManagerImpl.NOTIFICATION_ID_TRACKING,
            notificationManager.buildTrackingNotification()
        )
        observeTourState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        advertiserController.stop()
        scannerController.stop()
        lostDetectionJob?.cancel()
        serviceScope.cancel()
        notificationManager.clearAll()
        super.onDestroy()
    }

    private fun observeTourState() {
        serviceScope.launch {
            combine(
                tourRepository.observeCurrentTour(),
                authRepository.observeCurrentUser()
            ) { tour, user -> Pair(tour, user) }
                .collectLatest { (tour, user) ->
                    if (tour == null || user == null) {
                        Log.d(TAG, "No active tour or user — stopping BLE")
                        advertiserController.stop()
                        scannerController.stop()
                        lostDetectionJob?.cancel()
                        stopSelf()
                        return@collectLatest
                    }

                    Log.d(TAG, "Active tour ${tour.tourId} — starting BLE for group ${tour.groupId}")
                    advertiserController.start(userId = user.userId, groupId = tour.groupId)
                    scannerController.start(
                        groupId = tour.groupId,
                        currentUserId = user.userId,
                        scope = serviceScope
                    )
                    startLostDetectionLoop(user.userId, tour.leaderId, tour.role)
                }
        }
    }

    private fun startLostDetectionLoop(userId: String, leaderId: String, role: TourRole) {
        lostDetectionJob?.cancel()
        lostDetectionJob = serviceScope.launch {
            while (isActive) {
                delay(LOST_EVAL_INTERVAL_MS)
                evaluateLost(userId, leaderId, role)
            }
        }
    }

    private suspend fun evaluateLost(userId: String, leaderId: String, role: TourRole) {
        val members = tourRepository.observeMembers().first()
        val presenceList = presenceRepository.observePresence().first()

        val input = LostDetectionInput(
            currentUserId = userId,
            leaderId = leaderId,
            role = role,
            members = members.map { it.userId },
            presenceList = presenceList,
            nowMs = clock.currentTimeMillis()
        )

        val result = lostDetectionEngine.evaluate(input)
        notificationManager.showLostWarning(result)
    }
}
