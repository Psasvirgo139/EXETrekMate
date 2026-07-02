package com.trekmate.app.core.ble

data class BlePacket(
    val userId: String,
    val groupId: String
) {
    fun isSameGroup(currentGroupId: String): Boolean = groupId == currentGroupId
}
