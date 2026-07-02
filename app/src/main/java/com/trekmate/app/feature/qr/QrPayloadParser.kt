package com.trekmate.app.feature.qr

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

interface QrPayloadParser {
    fun parse(rawValue: String): Result<TourJoinPayload>
}

@Singleton
class QrPayloadParserImpl @Inject constructor() : QrPayloadParser {

    override fun parse(rawValue: String): Result<TourJoinPayload> {
        if (rawValue.isBlank()) return Result.failure(IllegalArgumentException("Empty QR payload"))

        return runCatching {
            val uri = Uri.parse(rawValue)
            require(uri.scheme == "trekmate") { "Not a TrekMate QR code (scheme: ${uri.scheme})" }
            require(uri.host == "join") { "Not a join QR code (host: ${uri.host})" }
            val code = uri.getQueryParameter("code")
                ?: throw IllegalArgumentException("Missing join code in QR payload")
            require(code.isNotBlank()) { "Join code is empty" }
            TourJoinPayload(joinCode = code)
        }
    }

    companion object {
        fun buildPayload(joinCode: String): String = "trekmate://join?code=$joinCode"
    }
}
