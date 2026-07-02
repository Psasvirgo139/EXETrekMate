package com.trekmate.app.core.ble

import javax.inject.Inject
import javax.inject.Singleton

interface BlePacketDecoder {
    fun decode(bytes: ByteArray): PacketParseResult
}

@Singleton
class BlePacketDecoderImpl @Inject constructor() : BlePacketDecoder {

    override fun decode(bytes: ByteArray): PacketParseResult {
        if (bytes.size < BlePacketConstants.TOTAL_PAYLOAD_BYTES) {
            return PacketParseResult.Invalid("Payload too short: ${bytes.size} bytes")
        }

        return runCatching {
            val userId = decodeField(bytes, 0, BlePacketConstants.USER_ID_BYTES)
            val groupId = decodeField(bytes, BlePacketConstants.USER_ID_BYTES, BlePacketConstants.GROUP_ID_BYTES)

            if (userId.isBlank()) return PacketParseResult.Invalid("userId is blank")
            if (groupId.isBlank()) return PacketParseResult.Invalid("groupId is blank")

            PacketParseResult.Valid(BlePacket(userId = userId, groupId = groupId))
        }.getOrElse { e ->
            PacketParseResult.Invalid("Decode error: ${e.message}")
        }
    }

    private fun decodeField(bytes: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, bytes.size)
        val fieldBytes = bytes.copyOfRange(offset, end)
        // Strip null padding
        val nullIdx = fieldBytes.indexOfFirst { it == 0.toByte() }
        val actual = if (nullIdx >= 0) fieldBytes.copyOf(nullIdx) else fieldBytes
        return actual.toString(Charsets.UTF_8).trim()
    }
}
