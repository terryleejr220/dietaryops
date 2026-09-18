package com.dietaryops.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dietaryops.manager.data.SettingsManager
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class ZebraPrinterManager(private val context: Context) {

    val sppManager = BluetoothSppManager(context)

    val connectionState: StateFlow<ConnectionState> = sppManager.connectionState
    val lastError: StateFlow<String?> = sppManager.lastError
    val connectedDevice: StateFlow<BluetoothDevice?> = sppManager.connectedDevice
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = sppManager.discoveredDevices

    @SuppressLint("MissingPermission")
    fun isStrictPrinterDevice(device: BluetoothDevice): Boolean {
        // 1. Check name for printer keywords
        val name = try { device.name ?: "" } catch (_: SecurityException) { "" }
        if (name.contains("Printer", ignoreCase = true) ||
            name.contains("Zebra", ignoreCase = true) ||
            name.contains("QLn", ignoreCase = true) ||
            name.contains("ZQ", ignoreCase = true) ||
            name.contains("ZD", ignoreCase = true) ||
            name.contains("ZPL", ignoreCase = true) ||
            name.contains("Print", ignoreCase = true) ||
            name.contains("POS", ignoreCase = true) ||
            name.contains("Epson", ignoreCase = true) ||
            name.contains("Star", ignoreCase = true) ||
            name.contains("Bixolon", ignoreCase = true) ||
            name.contains("Brother", ignoreCase = true)
        ) {
            return true
        }

        // 2. Check Bluetooth device class (Imaging / Printer)
        val devClass = try { device.bluetoothClass } catch (_: SecurityException) { null }
        if (devClass != null) {
            val major = devClass.majorDeviceClass
            val subClass = devClass.deviceClass
            if (major == BluetoothClass.Device.Major.IMAGING ||
                subClass == 1664 // BluetoothClass.Device.PRINTER
            ) {
                return true
            }
        }

        return false
    }

    @SuppressLint("MissingPermission")
    fun isPrinterOrPaired(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        return isStrictPrinterDevice(device)
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        val paired = sppManager.getPairedDevices()
        val pairedPrinters = paired.filter { isStrictPrinterDevice(it) }
        return if (pairedPrinters.isNotEmpty()) pairedPrinters else paired
    }

    @SuppressLint("MissingPermission")
    fun getPreferredPrinter(): BluetoothDevice? {
        val paired = getPairedPrinters()
        return paired.firstOrNull {
            val devName = try { it.name } catch (_: SecurityException) { null } ?: ""
            devName.contains("QLn", ignoreCase = true) || devName.contains("Zebra", ignoreCase = true)
        } ?: paired.firstOrNull()
    }

    suspend fun connect(device: BluetoothDevice): Boolean {
        return sppManager.connect(device)
    }

    suspend fun disconnect() {
        sppManager.disconnect()
    }

    suspend fun printZpl(zpl: String): Boolean {
        return sppManager.printZpl(zpl)
    }

    suspend fun printProductLabel(
        product: SyscoProduct,
        deliveryDate: LocalDate = LocalDate.now(),
        storageLocation: String? = null
    ): Boolean {
        val staffInitials = SettingsManager(context).staffInitials
        val zpl = ZplGenerator.generateLabel(product, deliveryDate, storageLocation, staffInitials = staffInitials)
        return sppManager.printZpl(zpl)
    }

    /**
     * One-shot asynchronous Bluetooth print connection for Zebra QLn420 printer.
     */
    @SuppressLint("MissingPermission")
    fun printDirect(device: BluetoothDevice, zplData: String, quantity: Int = 1, onResult: (Boolean, String) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                if (!sppManager.hasBluetoothPermissions()) {
                    mainHandler.post { onResult(false, "Bluetooth permissions missing") }
                    return@Thread
                }
                val socket = device.createRfcommSocketToServiceRecord(BluetoothSppManager.SPP_UUID)
                socket.connect()
                val outStream = socket.outputStream
                val printCount = quantity.coerceAtLeast(1)

                val modifiedZpl = if (zplData.contains("^XZ")) {
                    zplData.replace("^XZ", "^PQ$printCount,0,1,Y\n^XZ")
                } else {
                    "$zplData\n^PQ$printCount,0,1,Y\n"
                }

                outStream.write(modifiedZpl.toByteArray(Charsets.US_ASCII))
                outStream.flush()

                // Add buffer drain / delay before closing Bluetooth socket so printer doesn't reset RFCOMM connection prematurely
                Thread.sleep(400)

                outStream.close()
                socket.close()
                val nameStr = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                mainHandler.post { onResult(true, "Printed $printCount label(s) successfully to $nameStr") }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, e.localizedMessage ?: "Bluetooth printer error") }
            }
        }.start()
    }
}
