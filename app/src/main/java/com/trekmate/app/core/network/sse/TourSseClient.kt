package com.trekmate.app.core.network.sse

import android.util.Log
import com.google.gson.Gson
import com.trekmate.app.core.network.dto.MemberListResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TourSseClient"

private const val RECONNECT_DELAY_MS = 5_000L

@Singleton
class TourSseClient @Inject constructor(
    baseClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String
) {
    // SSE requires no read timeout — the connection stays open indefinitely
    private val client = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Returns a cold Flow that:
     * 1. Connects to the SSE endpoint for [tourId]
     * 2. Parses each event and emits [TourSseEvent]
     * 3. On disconnect, emits [TourSseEvent.Disconnected] then retries after [RECONNECT_DELAY_MS]
     * 4. Stops when the collecting coroutine is cancelled (tour cleared / app closed)
     */
    fun eventFlow(tourId: String): Flow<TourSseEvent> = flow {
        Log.i(TAG, "eventFlow started for tourId=$tourId")
        while (currentCoroutineContext().isActive) {
            try {
                val url = "${baseUrl}exe/tours/$tourId/events"
                Log.d(TAG, "Connecting to SSE: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "SSE connect failed: HTTP ${response.code}")
                        emit(TourSseEvent.Disconnected)
                        delay(RECONNECT_DELAY_MS)
                        return@use
                    }
                    Log.i(TAG, "SSE connected: HTTP ${response.code}")

                    val source = response.body?.source() ?: run {
                        Log.w(TAG, "SSE body is null")
                        emit(TourSseEvent.Disconnected)
                        delay(RECONNECT_DELAY_MS)
                        return@use
                    }

                    var eventName = ""
                    val dataBuffer = StringBuilder()

                    while (currentCoroutineContext().isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        when {
                            line.startsWith("event:") -> {
                                eventName = line.removePrefix("event:").trim()
                            }
                            line.startsWith("data:") -> {
                                dataBuffer.append(line.removePrefix("data:").trim())
                            }
                            line.startsWith(":") -> {
                                // SSE comment / heartbeat — ignore
                            }
                            line.isEmpty() && dataBuffer.isNotEmpty() -> {
                                val data = dataBuffer.toString()
                                dataBuffer.clear()
                                Log.d(TAG, "SSE event: name='$eventName' data='${data.take(200)}'")
                                val event = dispatchEvent(eventName, data)
                                if (event == null) {
                                    Log.w(TAG, "dispatchEvent returned null for name='$eventName'")
                                } else {
                                    emit(event)
                                }
                                if (eventName == "tour_ended") return@flow
                                eventName = ""
                            }
                        }
                    }
                    Log.i(TAG, "SSE inner loop exited (source exhausted or cancelled)")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "SSE flow cancelled")
                throw e  // Must propagate cancellation
            } catch (e: Exception) {
                Log.w(TAG, "SSE error: ${e.javaClass.simpleName}: ${e.message}")
            }

            emit(TourSseEvent.Disconnected)
            delay(RECONNECT_DELAY_MS)
        }
    }.flowOn(Dispatchers.IO)

    private fun dispatchEvent(eventName: String, data: String): TourSseEvent? =
        when (eventName) {
            "member_update" -> runCatching {
                val response = gson.fromJson(data, MemberListResponse::class.java)
                Log.d(TAG, "Parsed MemberUpdate: ${response.members.size} member(s)")
                TourSseEvent.MemberUpdate(response.members)
            }.getOrElse { e ->
                Log.e(TAG, "Failed to parse member_update: ${e.message}")
                null
            }

            "tour_ended" -> TourSseEvent.TourEnded

            else -> null
        }
}
