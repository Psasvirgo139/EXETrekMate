package com.trekmate.app.core.ble

import javax.inject.Inject
import javax.inject.Singleton

interface BlePacketEncoder {
    fun encode(packet: BlePacket): Result<ByteArray>
}

@Singleton
class BlePacketEncoderImpl @Inject constructor() : BlePacketEncoder {

    override fun encode(packet: BlePacket): Result<ByteArray> = runCatching {
        require(packet.userId.isNotBlank()) { "userId must not be blank" }
        require(packet.groupId.isNotBlank()) { "groupId must not be blank" }

        val payload = ByteArray(BlePacketConstants.TOTAL_PAYLOAD_BYTES)
        encodeField(packet.userId, payload, 0, BlePacketConstants.USER_ID_BYTES)
        encodeField(packet.groupId, payload, BlePacketConstants.USER_ID_BYTES, BlePacketConstants.GROUP_ID_BYTES)
        payload
    }

    private fun encodeField(value: String, dest: ByteArray, offset: Int, length: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val copyLen = minOf(bytes.size, length)
        bytes.copyInto(dest, destinationOffset = offset, endIndex = copyLen)
    }
}
