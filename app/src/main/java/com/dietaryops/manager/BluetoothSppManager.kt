package com.dietaryops.manager

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothSppManager(private val context: Context) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()
    private var lastConnectedDevice: BluetoothDevice? = null

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    device?.let {
                        if (!_discoveredDevices.value.contains(it)) {
                            _discoveredDevices.value = _discoveredDevices.value + it
                        }
                    }
                }
            }
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }
        return try {
            bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            _lastError.value = "Missing Bluetooth permission: ${e.message}"
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _lastError.value = "Bluetooth permissions missing or Bluetooth disabled"
            return false
        }
        try {
            if (!receiverRegistered) {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                context.registerReceiver(receiver, filter)
                receiverRegistered = true
            }
            _discoveredDevices.value = emptyList()
            return bluetoothAdapter.startDiscovery()
        } catch (e: SecurityException) {
            _lastError.value = "Security Exception during discovery: ${e.message}"
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (hasBluetoothPermissions() && bluetoothAdapter?.isDiscovering == true) {
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (_: SecurityException) {}
        }
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = "BLUETOOTH_CONNECT permission not granted"
            return@withContext false
        }

        stopDiscovery()
        disconnectInternal()

        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null

        try {
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()
            socket = newSocket
            outputStream = newSocket.outputStream
            _connectedDevice.value = device
            lastConnectedDevice = device
            _connectionState.value = ConnectionState.CONNECTED
            true
        } catch (e: Exception) {
            disconnectInternal()
            _connectionState.value = ConnectionState.ERROR
            val devName = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
            _lastError.value = "Failed to connect to $devName: ${e.localizedMessage}"
            false
        }
    }

    suspend fun sendData(data: ByteArray, maxRetries: Int = 2): Boolean = withContext(Dispatchers.IO) {
        repeat(maxRetries) { attempt ->
            var stream = outputStream
            if (stream == null || _connectionState.value != ConnectionState.CONNECTED) {
                val targetDevice = _connectedDevice.value ?: lastConnectedDevice
                if (targetDevice != null) {
                    val reconnected = connect(targetDevice)
                    if (reconnected) {
                        stream = outputStream
                    }
                }
            }

            if (stream != null && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    stream.write(data)
                    stream.flush()
                    return@withContext true
                } catch (e: Exception) {
                    disconnectInternal()
                    if (attempt < maxRetries - 1) {
                        delay(500L * (attempt + 1))
                        val targetDevice = lastConnectedDevice
                        if (targetDevice != null) {
                            connect(targetDevice)
                        }
                    } else {
                        _connectionState.value = ConnectionState.ERROR
                        _lastError.value = "Error sending data: ${e.localizedMessage}"
                    }
                }
            }
        }

        if (_connectionState.value != ConnectionState.ERROR) {
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = "Not connected to printer after retries"
        }
        return@withContext false
    }

    suspend fun printZpl(zpl: String): Boolean {
        return sendData(zpl.toByteArray(Charsets.US_ASCII))
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        try {
            outputStream?.close()
        } catch (_: IOException) {}
        outputStream = null

        try {
            socket?.close()
        } catch (_: IOException) {}
        socket = null

        _connectedDevice.value = null
        if (_connectionState.value != ConnectionState.ERROR) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
}
