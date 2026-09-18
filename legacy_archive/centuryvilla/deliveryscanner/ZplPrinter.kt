package com.centuryvilla.deliveryscanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.centuryvilla.deliveryscanner.data.model.SyscoProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object ZplPrinter {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yy")

    /**
     * Builds ZPL for a receiving / delivery label (2.25in x 1.25in @ 203 DPI).
     * Supports unit count (e.g. "1 cs", "2 cases", "6 cans") and multiple label copies.
     */
    fun generateDeliveryLabel(
        product: SyscoProduct,
        deliveryDate: LocalDate = LocalDate.now(),
        quantity: Int = 1,
        unitCount: String = "1 cs"
    ): String {
        val useByDate = deliveryDate.plusDays(product.shelfLifeDays.toLong())
        val delivStr = deliveryDate.format(dateFormatter)
        val useByStr = useByDate.format(dateFormatter)
        val barcodePayload = "${product.upc}|$delivStr"

        val displayName = if (product.name.length > 22) product.name.take(20) + ".." else product.name
        val packStr = product.getFormattedPackInfo()

        return """
^XA
^PW456
^LL0254
^PON
^LH0,0

^FO20,12^A0N,32,28^FD$displayName^FS
^FO20,46^A0N,18,16^FDLOC: ${product.location}  Pack: $packStr^FS
^FO20,68^GB416,2,2^FS

^FO20,78^A0N,20,18^FDDelivered:^FS
^FO130,78^A0N,22,20^FD$delivStr^FS

^FO20,108^A0N,26,24^FDUSE BY:^FS
^FO130,102^A0N,36,34^FD$useByStr^FS

^FO20,146^GB416,1,1^FS
^FO20,158^A0N,18,16^FDBy: _____  Recv: $unitCount  +$product.shelfLifeDays d^FS

^FO75,188^BY1,2,28^BCN,28,N,N,N^FD$barcodePayload^FS
^PQ$quantity,0,1,Y
^XZ
        """.trimIndent()
    }

    /**
     * Builds ZPL for an OPENED product label with delivery date, open date, and calculated use-by date.
     */
    fun generateOpenedLabel(
        product: SyscoProduct,
        deliveryDate: LocalDate = LocalDate.now(),
        openDate: LocalDate = LocalDate.now(),
        quantity: Int = 1,
        unitCount: String = "1 unit"
    ): String {
        val unopenedMax = deliveryDate.plusDays(product.shelfLifeDays.toLong())
        val openedMax = openDate.plusDays(product.openedShelfLifeDays.toLong())
        val useByDate = if (openedMax.isBefore(unopenedMax)) openedMax else unopenedMax

        val delivStr = deliveryDate.format(dateFormatter)
        val openStr = openDate.format(dateFormatter)
        val useByStr = useByDate.format(dateFormatter)

        val displayName = if (product.name.length > 18) product.name.take(16) + ".." else product.name
        val packStr = product.getFormattedPackInfo()

        return """
^XA
^PW456
^LL0254
^PON
^LH0,0

^FO20,12^A0N,32,28^FD$displayName^FS
^FO325,12^A0N,20,18^FD[OPEN]^FS
^FO20,46^A0N,18,16^FDLOC: ${product.location}  Pack: $packStr^FS
^FO20,68^GB416,2,2^FS

^FO20,78^A0N,18,16^FDDeliv: $delivStr^FS
^FO225,78^A0N,18,16^FDOpened: $openStr^FS

^FO20,104^A0N,24,22^FDUSE BY:^FS
^FO130,98^A0N,34,32^FD$useByStr^FS

^FO20,138^GB416,1,1^FS
^FO20,148^A0N,16,14^FDBy: _____  Qty: $unitCount  Limit: +${product.openedShelfLifeDays}d^FS

^FO110,172^BY1,2,26^BCN,26,N,N,N^FD${product.upc}^FS
^PQ$quantity,0,1,Y
^XZ
        """.trimIndent()
    }

    fun generateAdminBadgeLabel(): String {
        return """
^XA
^PW456
^LL0254
^PON
^LH0,0

^FO20,16^A0N,28,26^FDCENTURY VILLA DIETARY^FS
^FO20,48^GB416,2,2^FS

^FO20,60^A0N,24,22^FDMANAGER ACCESS BADGE^FS
^FO20,88^A0N,18,16^FDScan with camera to toggle Admin^FS

^FO55,120^BY2,2,45^BCN,45,Y,N,N^FDCV-ADMIN-AUTH^FS

^FO20,205^GB416,1,1^FS
^FO20,218^A0N,16,16^FDKitchen Operations & Compliance^FS
^PQ1,0,1,Y
^XZ
        """.trimIndent()
    }

    fun generateLabel(product: SyscoProduct, deliveryDate: LocalDate = LocalDate.now()): String {
        return generateDeliveryLabel(product, deliveryDate, 1)
    }

    @SuppressLint("MissingPermission")
    suspend fun printAsync(device: BluetoothDevice, zplData: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val outStream: OutputStream = socket.outputStream
                outStream.write(zplData.toByteArray(Charsets.US_ASCII))
                outStream.flush()
                outStream.close()
                socket.close()
                Pair(true, "Printed successfully to ${device.name}")
            } catch (e: Exception) {
                Pair(false, e.localizedMessage ?: "Bluetooth print error")
            }
        }

    @SuppressLint("MissingPermission")
    fun print(device: BluetoothDevice, zplData: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val outStream: OutputStream = socket.outputStream
                outStream.write(zplData.toByteArray(Charsets.US_ASCII))
                outStream.flush()
                outStream.close()
                socket.close()
                onResult(true, "Printed successfully to ${device.name}")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Bluetooth print error")
            }
        }.start()
    }
}
