package com.trekmate.app.core.ble

sealed interface PacketParseResult {
    data class Valid(val packet: BlePacket) : PacketParseResult
    data class Invalid(val reason: String) : PacketParseResult
}
