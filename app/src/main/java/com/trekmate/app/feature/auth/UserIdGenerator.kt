package com.trekmate.app.feature.auth

import java.util.UUID
import javax.inject.Inject

/**
 * Generates compact user IDs suitable for BLE payloads.
 * Uses first 8 characters of a UUID (32-bit hex) as the compact representation.
 * Full UUID is stored locally; compact form is embedded in BLE packets.
 */
class UserIdGenerator @Inject constructor() {

    fun generate(): String = UUID.randomUUID().toString().replace("-", "").take(16)
}
