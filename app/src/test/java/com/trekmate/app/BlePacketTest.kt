package com.trekmate.app

import com.trekmate.app.core.ble.*
import org.junit.Assert.*
import org.junit.Test

class BlePacketTest {

    private val encoder = BlePacketEncoderImpl()
    private val decoder = BlePacketDecoderImpl()

    @Test
    fun `encode and decode round trip returns same packet`() {
        val original = BlePacket(userId = "user1234", groupId = "grp56789")
        val encoded = encoder.encode(original)
        assertTrue(encoded.isSuccess)

        val decoded = decoder.decode(encoded.getOrThrow())
        assertTrue(decoded is PacketParseResult.Valid)
        val result = (decoded as PacketParseResult.Valid).packet
        assertEquals(original.userId, result.userId)
        assertEquals(original.groupId, result.groupId)
    }

    @Test
    fun `encode payload is exactly 16 bytes`() {
        val packet = BlePacket(userId = "abcdefgh", groupId = "12345678")
        val bytes = encoder.encode(packet).getOrThrow()
        assertEquals(BlePacketConstants.TOTAL_PAYLOAD_BYTES, bytes.size)
    }

    @Test
    fun `decode empty bytes returns invalid`() {
        val result = decoder.decode(ByteArray(0))
        assertTrue(result is PacketParseResult.Invalid)
    }

    @Test
    fun `decode truncated bytes returns invalid`() {
        val result = decoder.decode(ByteArray(4))
        assertTrue(result is PacketParseResult.Invalid)
    }

    @Test
    fun `decode random bytes does not crash`() {
        val random = ByteArray(16) { (it * 7).toByte() }
        val result = decoder.decode(random)
        assertNotNull(result)
    }

    @Test
    fun `encode blank userId returns failure`() {
        val result = encoder.encode(BlePacket(userId = "", groupId = "grp1"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `encode blank groupId returns failure`() {
        val result = encoder.encode(BlePacket(userId = "user1", groupId = ""))
        assertTrue(result.isFailure)
    }

    @Test
    fun `isSameGroup returns true for matching group`() {
        val packet = BlePacket(userId = "u1", groupId = "group1")
        assertTrue(packet.isSameGroup("group1"))
        assertFalse(packet.isSameGroup("group2"))
    }

    @Test
    fun `long userId is truncated to fit payload`() {
        val longId = "user1234567890abcdef"
        val packet = BlePacket(userId = longId, groupId = "grp12345")
        val bytes = encoder.encode(packet).getOrThrow()
        assertEquals(BlePacketConstants.TOTAL_PAYLOAD_BYTES, bytes.size)
    }
}
