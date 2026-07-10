package com.trekmate.app.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
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
    /** Running count of scan results accepted (same-group, not self). Resets on stop. */
    val scanHitCount: StateFlow<Int>
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

    companion object {
        private const val TAG = "TrekBleScanner"
    }

    private val _state = MutableStateFlow<ScanningState>(ScanningState.Idle)
    override val state: StateFlow<ScanningState> = _state.asStateFlow()

    private val _scanHitCount = MutableStateFlow(0)
    override val scanHitCount: StateFlow<Int> = _scanHitCount.asStateFlow()

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

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "already started (code 1)"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "app registration failed (code 2)"
                SCAN_FAILED_INTERNAL_ERROR -> "internal error (code 3)"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported (code 4)"
                else -> "unknown error code $errorCode"
            }
            Log.e(TAG, "BLE scan failed: $reason")
            _state.value = ScanningState.Failed("Scan failed: $reason")
        }
    }

    override fun start(groupId: String, currentUserId: String, scope: CoroutineScope) {
        // Allow restart from Failed or Stopped; only skip if genuinely already running
        if (_state.value is ScanningState.Running) {
            Log.d(TAG, "Scan already running for group $groupId — skip restart")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter not available or disabled")
            _state.value = ScanningState.Failed("Bluetooth not available or disabled")
            return
        }

        // Clean up any previous scan state before starting fresh
        bleScanner?.let { scanner ->
            try { scanner.stopScan(scanCallback) } catch (e: Exception) { /* ignore */ }
        }

        activeGroupId = groupId
        activeUserId = currentUserId
        scanScope = scope
        bleScanner = adapter.bluetoothLeScanner
        _scanHitCount.value = 0

        if (bleScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null — missing BLUETOOTH_SCAN permission?")
            _state.value = ScanningState.Failed("BluetoothLeScanner unavailable (check permissions)")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.d(TAG, "Starting BLE scan for groupId=$groupId userId=$currentUserId")
        _state.value = ScanningState.Starting
        bleScanner?.startScan(emptyList(), settings, scanCallback)
        _state.value = ScanningState.Running
        Log.d(TAG, "BLE scan started successfully")
    }

    override fun stop() {
        Log.d(TAG, "Stopping BLE scan")
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Exception while stopping scan (may be harmless): ${e.message}")
        }
        bleScanner = null
        activeGroupId = null
        activeUserId = null
        _scanHitCount.value = 0
        _state.value = ScanningState.Stopped
    }

    private fun handleScanResult(result: ScanResult) {
        val manufacturerData = result.scanRecord
            ?.getManufacturerSpecificData(BlePacketConstants.MANUFACTURER_ID) ?: return

        when (val parsed = decoder.decode(manufacturerData)) {
            is PacketParseResult.Invalid -> {
                Log.v(TAG, "Ignored invalid BLE packet from ${result.device.address}")
            }
            is PacketParseResult.Valid -> {
                val packet = parsed.packet
                val currentGroup = activeGroupId ?: return
                val currentUser = activeUserId ?: return

                if (!packet.isSameGroup(currentGroup)) {
                    Log.v(TAG, "Ignored packet from different group: ${packet.groupId}")
                    return
                }
                if (packet.userId == currentUser) {
                    Log.v(TAG, "Ignored own advertisement packet")
                    return
                }

                Log.d(TAG, "✓ Accepted BLE packet | userId=${packet.userId} (${packet.userId.length} chars) | RSSI=${result.rssi}")
                _scanHitCount.value++

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


