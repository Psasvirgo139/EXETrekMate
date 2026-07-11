package com.trekmate.app.core.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * GPS location wrapper around [com.google.android.gms.location.FusedLocationProviderClient].
 *
 * Strategy (both stages have explicit timeouts to prevent hanging forever):
 *  1. `lastLocation`  — instant cache read, 5 s timeout
 *  2. `requestLocationUpdates` (1 update) — active GPS, 25 s timeout
 *
 * KEY FIX: Uses a dedicated [HandlerThread] ("trek-location-thread") instead of
 * [Looper.getMainLooper()].  This prevents the callback from being queued behind
 * Compose recompositions / heavy UI work on the Main thread, which was the root
 * cause of GPS hanging indefinitely after joining a tour.
 *
 * Caller must hold ACCESS_FINE_LOCATION permission.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TrekLocationProvider"
        private const val LAST_LOCATION_TIMEOUT_MS = 5_000L
        private const val FRESH_LOCATION_TIMEOUT_MS = 25_000L
    }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Dedicated background thread for FusedLocationProviderClient callbacks.
     * Using a separate HandlerThread means GPS callbacks are never queued behind
     * Main-thread UI work, which was the root cause of the 25 s timeout being hit.
     */
    private val locationThread = HandlerThread("trek-location-thread").also { it.start() }
    private val locationLooper: Looper get() = locationThread.looper

    /**
     * Returns (latitude, longitude) or throws on failure.
     *
     * Return type is [Pair<Double, Double>] directly (not wrapped in Result) so callers
     * can use normal try/catch or [runCatching].  The previous API returned
     * Result<Pair<Double,Double>> which caused callers to accidentally double-wrap it.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double> {
        if (!hasLocationPermission()) {
            throw SecurityException("Quyền truy cập vị trí (GPS) chưa được cấp.")
        }
        if (!isLocationEnabled()) {
            throw Exception("Định vị (GPS) trên thiết bị đang tắt. Vui lòng bật định vị.")
        }

        // ── Stage 1: last known location (instant, cache only) ────────────────
        val cachedLocation: Location? = withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc: Location? ->
                        Log.d(TAG, "lastLocation → $loc")
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "lastLocation failed: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

        if (cachedLocation != null) {
            Log.d(TAG, "Using cached location: (${cachedLocation.latitude}, ${cachedLocation.longitude})")
            return Pair(cachedLocation.latitude, cachedLocation.longitude)
        }

        Log.d(TAG, "No cached location — requesting fresh GPS fix (timeout ${FRESH_LOCATION_TIMEOUT_MS}ms)")

        // ── Stage 2: fresh GPS fix via requestLocationUpdates ─────────────────
        // Uses locationLooper (dedicated HandlerThread) instead of getMainLooper().
        // This is the critical fix: if Main Looper is busy with Compose recomposition
        // when the tour starts, callbacks on getMainLooper() never fire → 25 s timeout.
        val freshLocation: Location? = withTimeoutOrNull(FRESH_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                    .setMaxUpdates(1)
                    .setMinUpdateIntervalMillis(0L)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val loc = result.lastLocation
                        Log.d(TAG, "requestLocationUpdates → $loc")
                        fusedClient.removeLocationUpdates(this)
                        if (cont.isActive) cont.resume(loc)
                    }
                }

                // KEY: locationLooper — NOT getMainLooper()
                fusedClient.requestLocationUpdates(
                    request,
                    callback,
                    locationLooper
                )

                cont.invokeOnCancellation {
                    Log.d(TAG, "Location request cancelled — removing updates")
                    fusedClient.removeLocationUpdates(callback)
                }
            }
        }

        if (freshLocation != null) {
            Log.d(TAG, "Fresh GPS fix: (${freshLocation.latitude}, ${freshLocation.longitude})")
            return Pair(freshLocation.latitude, freshLocation.longitude)
        }

        Log.e(TAG, "GPS timeout after ${FRESH_LOCATION_TIMEOUT_MS}ms — both stages failed")
        throw Exception("Không lấy được GPS trong ${FRESH_LOCATION_TIMEOUT_MS / 1000}s (thử bật GPS và ra ngoài trời)")
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
