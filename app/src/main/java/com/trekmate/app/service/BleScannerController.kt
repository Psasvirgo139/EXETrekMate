package com.trekmate.app.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.trekmate.app.core.ble.BlePacketConstants
import com.trekmate.app.core.ble.BlePacketDecoder
import com.trekmate.app.core.ble.PacketParseResult
import com.trekmate.app.core.model.BleObservation as DomainBleObservation
import com.trekmate.app.core.storage.BleObservationStore
import com.trekmate.app.core.time.ClockProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface BleScannerController {
    val state: StateFlow<ScanningState>
    val observations: Flow<DomainBleObservation>
    fun start(groupId: String, currentUserId: String, scope: CoroutineScope)
    fun stop()
}

@Singleton
@SuppressLint("MissingPermission")
class BleScannerControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: BlePacketDecoder,
    private val observationStore: BleObservationStore,
    private val clock: ClockProvider
) : BleScannerController {

    private val _state = MutableStateFlow<ScanningState>(ScanningState.Idle)
    override val state: StateFlow<ScanningState> = _state.asStateFlow()

    private val _observations = Channel<DomainBleObservation>(Channel.BUFFERED)
    override val observations: Flow<DomainBleObservation> = _observations.receiveAsFlow()

    private var activeGroupId: String? = null
    private var activeUserId: String? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanScope: CoroutineScope? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = ScanningState.Failed("Scan failed with code: $errorCode")
        }
    }

    override fun start(groupId: String, currentUserId: String, scope: CoroutineScope) {
        if (_state.value == ScanningState.Running) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = ScanningState.Failed("Bluetooth not available")
            return
        }

        activeGroupId = groupId
        activeUserId = currentUserId
        scanScope = scope
        bleScanner = adapter.bluetoothLeScanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _state.value = ScanningState.Starting
        bleScanner?.startScan(emptyList(), settings, scanCallback)
        _state.value = ScanningState.Running
    }

    override fun stop() {
        bleScanner?.stopScan(scanCallback)
        bleScanner = null
        activeGroupId = null
        activeUserId = null
        _state.value = ScanningState.Stopped
    }

    private fun handleScanResult(result: ScanResult) {
        val manufacturerData = result.scanRecord
            ?.getManufacturerSpecificData(BlePacketConstants.MANUFACTURER_ID) ?: return

        when (val parsed = decoder.decode(manufacturerData)) {
            is PacketParseResult.Invalid -> { /* ignore malformed */ }
            is PacketParseResult.Valid -> {
                val packet = parsed.packet
                val currentGroup = activeGroupId ?: return
                val currentUser = activeUserId ?: return

                if (!packet.isSameGroup(currentGroup)) return
                if (packet.userId == currentUser) return

                val observation = DomainBleObservation(
                    userId = packet.userId,
                    groupId = packet.groupId,
                    rssi = result.rssi,
                    seenAt = clock.currentTimeMillis()
                )

                scanScope?.launch {
                    observationStore.upsertObservation(observation)
                    _observations.trySend(observation)
                }
            }
        }
    }
}
