package com.trekmate.app.core.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin wrapper around [com.google.android.gms.location.FusedLocationProviderClient].
 *
 * Returns [Result.failure] if GPS is unavailable or times out.
 * Caller must hold ACCESS_FINE_LOCATION permission.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TrekLocationProvider"
    }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<Pair<Double, Double>> =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()

            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                        cont.resume(Result.success(Pair(location.latitude, location.longitude)))
                    } else {
                        Log.w(TAG, "FusedLocation returned null — GPS not ready")
                        cont.resume(Result.failure(Exception("GPS location unavailable")))
                    }
                }
                .addOnFailureListener { e: Exception ->
                    Log.e(TAG, "Location request failed: ${e.message}")
                    cont.resume(Result.failure(e))
                }

            cont.invokeOnCancellation {
                cts.cancel()
            }
        }
}
