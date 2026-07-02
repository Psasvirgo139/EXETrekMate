package com.trekmate.app.core.network.ws

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.trekmate.app.core.network.dto.TourMemberDto
import com.trekmate.app.core.network.sse.TourSseEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TourWsClient"
private const val RECONNECT_DELAY_MS = 5_000L

/**
 * Real-time tour event client backed by WebSocket.
 *
 * WebSockets bypass nginx/Cloudflare response buffering that prevented
 * SSE events from arriving in real-time. The exposed Flow has the same
 * [TourSseEvent] type so the rest of the app (TourRepositoryImpl) is unchanged.
 *
 * Server message format:
 *   {"type":"member_update","members":[{"user_id":"...","is_leader":true},...]}
 *   {"type":"tour_ended"}
 *   {"type":"heartbeat"}   ← ignored
 */
@Singleton
class TourWebSocketClient @Inject constructor(
    baseClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)   // OkHttp-level ping to detect dead connections
        .build()

    fun eventFlow(tourId: String): Flow<TourSseEvent> = callbackFlow {
        val wsUrl = baseUrl
            .trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/exe/tours/$tourId/ws"

        Log.i(TAG, "Connecting WebSocket: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WS opened: tourId=$tourId HTTP ${response.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS message: ${text.take(200)}")
                val event = parseMessage(text) ?: return
                if (!isClosedForSend) trySend(event)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WS failure: ${t.message}")
                if (!isClosedForSend) trySend(TourSseEvent.Disconnected)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closed: code=$code reason='$reason'")
                if (!isClosedForSend) trySend(TourSseEvent.Disconnected)
                close()
            }
        })

        awaitClose {
            Log.d(TAG, "WS flow cancelled — closing socket")
            ws.close(1000, "Cancelled")
        }
    }

    private fun parseMessage(text: String): TourSseEvent? {
        return try {
            val json = gson.fromJson(text, JsonObject::class.java)
            when (json.get("type")?.asString) {
                "member_update" -> {
                    val membersArray = json.getAsJsonArray("members")
                    val members = membersArray.map { elem ->
                        val obj = elem.asJsonObject
                        TourMemberDto(
                            userId   = obj.get("user_id").asString,
                            isLeader = obj.get("is_leader").asBoolean
                        )
                    }
                    Log.d(TAG, "Parsed member_update: ${members.size} member(s)")
                    TourSseEvent.MemberUpdate(members)
                }
                "tour_ended" -> {
                    Log.d(TAG, "Parsed tour_ended")
                    TourSseEvent.TourEnded
                }
                "heartbeat" -> null   // keep-alive, no action needed
                else -> {
                    Log.d(TAG, "Unknown WS message type: ${json.get("type")}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WS message '$text': ${e.message}")
            null
        }
    }
}
