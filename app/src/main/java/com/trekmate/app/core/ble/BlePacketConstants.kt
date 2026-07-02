package com.trekmate.app.core.ble

/**
 * BLE advertisement payload layout for TrekMate MVP.
 *
 * Manufacturer-specific data format (placed in AdvertiseData.addManufacturerData):
 *   [0..1]  : MANUFACTURER_ID (2 bytes, little-endian) — handled by Android API
 *   [0..7]  : userId bytes (8 bytes, UTF-8 first 8 chars of compact userId)
 *   [8..15] : groupId bytes (8 bytes, UTF-8 first 8 chars of compact groupId)
 *
 * Total payload: 16 bytes, comfortably within the 31-byte advertising limit
 * even after manufacturer ID overhead.
 */
object BlePacketConstants {
    const val MANUFACTURER_ID: Int = 0x4D54  // 'TM' — TrekMate
    const val USER_ID_BYTES: Int = 8
    const val GROUP_ID_BYTES: Int = 8
    const val TOTAL_PAYLOAD_BYTES: Int = USER_ID_BYTES + GROUP_ID_BYTES  // 16
}
