package com.trekmate.app.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import com.trekmate.app.core.ble.BlePacket
import com.trekmate.app.core.ble.BlePacketConstants
import com.trekmate.app.core.ble.BlePacketEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface BleAdvertiserController {
    val state: StateFlow<AdvertisingState>
    fun start(userId: String, groupId: String)
    fun stop()
}

@Singleton
@SuppressLint("MissingPermission")
class BleAdvertiserControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encoder: BlePacketEncoder
) : BleAdvertiserController {

    private val _state = MutableStateFlow<AdvertisingState>(AdvertisingState.Idle)
    override val state: StateFlow<AdvertisingState> = _state.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            _state.value = AdvertisingState.Running
        }

        override fun onStartFailure(errorCode: Int) {
            _state.value = AdvertisingState.Failed("Advertise failed: error $errorCode")
        }
    }

    override fun start(userId: String, groupId: String) {
        if (_state.value == AdvertisingState.Running) return

        if (userId.isBlank() || groupId.isBlank()) {
            _state.value = AdvertisingState.Failed("userId or groupId is blank")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = AdvertisingState.Failed("Bluetooth not available")
            return
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            _state.value = AdvertisingState.Failed("Device does not support multiple advertisement")
            return
        }

        val payload = encoder.encode(BlePacket(userId = userId, groupId = groupId))
            .getOrElse { e ->
                _state.value = AdvertisingState.Failed("Packet encode failed: ${e.message}")
                return
            }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BlePacketConstants.MANUFACTURER_ID, payload)
            .build()

        advertiser = adapter.bluetoothLeAdvertiser
        _state.value = AdvertisingState.Starting
        advertiser?.startAdvertising(settings, data, advertiseCallback)
            ?: run { _state.value = AdvertisingState.Failed("BluetoothLeAdvertiser unavailable") }
    }

    override fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
        _state.value = AdvertisingState.Stopped
    }
}
