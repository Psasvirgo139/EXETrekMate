package com.trekmate.app.core.network.sse

import com.google.gson.Gson
import com.trekmate.app.core.network.dto.MemberListResponse
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
        while (currentCoroutineContext().isActive) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl}exe/tours/$tourId/events")
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        emit(TourSseEvent.Disconnected)
                        delay(RECONNECT_DELAY_MS)
                        return@use
                    }

                    val source = response.body?.source() ?: run {
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
                                dispatchEvent(eventName, data)?.let { emit(it) }
                                if (eventName == "tour_ended") return@flow
                                eventName = ""
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Network error, server restart, etc.
            }

            emit(TourSseEvent.Disconnected)
            delay(RECONNECT_DELAY_MS)
        }
    }.flowOn(Dispatchers.IO)

    private fun dispatchEvent(eventName: String, data: String): TourSseEvent? =
        when (eventName) {
            "member_update" -> runCatching {
                val response = gson.fromJson(data, MemberListResponse::class.java)
                TourSseEvent.MemberUpdate(response.members)
            }.getOrNull()

            "tour_ended" -> TourSseEvent.TourEnded

            else -> null
        }
}
