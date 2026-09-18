package com.dietaryops.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import java.time.LocalDate

object ZplPrinter {

    /**
     * Delegates ZPL label generation to ZplGenerator for 2.25" x 1.25" (456x254 dots) labels.
     */
    fun generateLabel(product: SyscoProduct, deliveryDate: LocalDate = LocalDate.now()): String {
        return ZplGenerator.generateLabel(product, deliveryDate)
    }

    /**
     * Delegates ZPL shelf label generation to ZplGenerator for 2.25" x 1.25" (456x254 dots) shelf labels.
     */
    fun generateShelfLabelZpl(
        itemName: String,
        upc: String,
        storageLocation: String,
        syscoItemNumber: String = "",
        piazzaItemNumber: String = "",
        parLevel: Double = 0.0,
        lastOnHand: Double = 0.0,
        unit: String = "EA",
        companyHeader: String = "DIETARY OPS SHELF LABEL",
        barcodeType: String = "BC"
    ): String {
        return ZplGenerator.generateShelfLabelZpl(
            itemName = itemName,
            upc = upc,
            storageLocation = storageLocation,
            syscoItemNumber = syscoItemNumber,
            piazzaItemNumber = piazzaItemNumber,
            parLevel = parLevel,
            lastOnHand = lastOnHand,
            unit = unit,
            companyHeader = companyHeader,
            barcodeType = barcodeType
        )
    }

    @SuppressLint("MissingPermission")
    fun print(device: BluetoothDevice, zplData: String, quantity: Int = 1, onResult: (Boolean, String) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(BluetoothSppManager.SPP_UUID)
                socket.connect()
                val outStream = socket.outputStream

                val printQty = quantity.coerceAtLeast(1)
                val modifiedZpl = if (zplData.contains("^XZ")) {
                    zplData.replace("^XZ", "^PQ$printQty,0,1,Y\n^XZ")
                } else {
                    "$zplData\n^PQ$printQty,0,1,Y\n"
                }

                outStream.write(modifiedZpl.toByteArray(Charsets.US_ASCII))
                outStream.flush()

                // Add buffer drain / delay before closing Bluetooth socket so printer doesn't reset RFCOMM connection prematurely
                Thread.sleep(400)

                outStream.close()
                socket.close()

                val nameStr = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                mainHandler.post {
                    onResult(true, "Printed $printQty label(s) successfully to $nameStr")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onResult(false, e.localizedMessage ?: "Bluetooth print error")
                }
            }
        }.start()
    }
}
