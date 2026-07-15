package com.trekmate.app.feature.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.trekmate.app.R
import com.trekmate.app.feature.tracking.LostDetectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface TrekMateNotificationManager {
    fun buildTrackingNotification(): Notification
    fun showLostWarning(result: LostDetectionResult)
    fun clearLostWarning()
    fun clearAll()
}

@Singleton
class TrekMateNotificationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TrekMateNotificationManager {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastLostResult: LostDetectionResult? = null

    // Stateful tracking to detect transitions
    private var wasMemberLost: Boolean = false
    private var lastLeaderLostCount: Int = 0

    companion object {
        const val CHANNEL_TRACKING = "trekmate_tracking"
        const val CHANNEL_LOST = "trekmate_lost"
        const val NOTIFICATION_ID_TRACKING = 1001
        const val NOTIFICATION_ID_LOST = 1002
        const val NOTIFICATION_ID_RECONNECTED = 1003
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.notification_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Active tour tracking" }

            val lostChannel = NotificationChannel(
                CHANNEL_LOST,
                context.getString(R.string.notification_channel_lost),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Lost member alerts" }

            notificationManager.createNotificationChannels(listOf(trackingChannel, lostChannel))
        }
    }

    override fun buildTrackingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setContentTitle(context.getString(R.string.notification_tracking_title))
            .setContentText(context.getString(R.string.notification_tracking_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun showLostWarning(result: LostDetectionResult) {
        val isMemberLost = result.isPossiblyLostFromLeader
        val leaderLostCount = result.lostMembers.size

        // Detect state transitions
        val transitionedToSafe = (wasMemberLost || lastLeaderLostCount > 0) && (!isMemberLost && leaderLostCount == 0)
        val transitionedToLost = (!wasMemberLost && lastLeaderLostCount == 0) && (isMemberLost || leaderLostCount > 0)
        val lostCountChanged = leaderLostCount != lastLeaderLostCount

        wasMemberLost = isMemberLost
        lastLeaderLostCount = leaderLostCount

        // 1. If everything is safe now
        if (!isMemberLost && leaderLostCount == 0) {
            clearLostWarning()
            if (transitionedToSafe) {
                // Post a positive "Reconnected" notification
                showReconnectedNotification()
            }
            return
        }

        // 2. If lost, only notify/alert when there is a state change to avoid spamming alerts every loop
        if (transitionedToLost || lostCountChanged || result != lastLostResult) {
            lastLostResult = result

            val (title, text) = when {
                isMemberLost -> Pair(
                    context.getString(R.string.notification_lost_leader_title),
                    context.getString(R.string.notification_lost_leader_text)
                )
                else -> Pair(
                    context.getString(R.string.notification_lost_members_title),
                    "$leaderLostCount member(s) may be lost"
                )
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_LOST)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true) // Crucial: prevents continuous vibration/sound on updates
                .build()

            notificationManager.notify(NOTIFICATION_ID_LOST, notification)
        }
    }

    private fun showReconnectedNotification() {
        val title = "Reconnected"
        val text = "You are back in group range."
        val notification = NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // Dismisses itself after 5 seconds automatically
            .build()

        notificationManager.notify(NOTIFICATION_ID_RECONNECTED, notification)
    }

    override fun clearLostWarning() {
        lastLostResult = null
        notificationManager.cancel(NOTIFICATION_ID_LOST)
    }

    override fun clearAll() {
        lastLostResult = null
        wasMemberLost = false
        lastLeaderLostCount = 0
        notificationManager.cancelAll()
    }
}
