package com.trekmate.app

import com.trekmate.app.feature.qr.QrPayloadParserImpl
import org.junit.Assert.*
import org.junit.Test

class QrPayloadParserTest {

    private val parser = QrPayloadParserImpl()

    @Test
    fun `parse valid QR payload returns TourJoinPayload`() {
        val result = parser.parse("trekmate://join?code=ABC123")
        assertTrue(result.isSuccess)
        assertEquals("ABC123", result.getOrNull()?.joinCode)
    }

    @Test
    fun `parse empty string returns failure`() {
        val result = parser.parse("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse wrong scheme returns failure`() {
        val result = parser.parse("https://example.com?code=ABC")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse missing code param returns failure`() {
        val result = parser.parse("trekmate://join")
        assertTrue(result.isFailure)
    }

    @Test
    fun `buildPayload produces parseable URL`() {
        val url = QrPayloadParserImpl.buildPayload("MYCODE")
        val result = parser.parse(url)
        assertTrue(result.isSuccess)
        assertEquals("MYCODE", result.getOrNull()?.joinCode)
    }
}
