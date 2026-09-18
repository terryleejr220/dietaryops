package com.centuryvilla.deliveryscanner.ui

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centuryvilla.deliveryscanner.ProductManager
import com.centuryvilla.deliveryscanner.ZplPrinter
import com.centuryvilla.deliveryscanner.data.model.InventoryItem
import com.centuryvilla.deliveryscanner.data.model.SyscoProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainViewModel(
    private val productManager: ProductManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    private val _scannedProduct = MutableStateFlow<SyscoProduct?>(null)
    val scannedProduct: StateFlow<SyscoProduct?> = _scannedProduct.asStateFlow()

    private val _detectedDeliveryDate = MutableStateFlow(LocalDate.now())
    val detectedDeliveryDate: StateFlow<LocalDate> = _detectedDeliveryDate.asStateFlow()

    private val _detectedOpenDate = MutableStateFlow(LocalDate.now())
    val detectedOpenDate: StateFlow<LocalDate> = _detectedOpenDate.asStateFlow()

    private val _isFromDeliveryLabel = MutableStateFlow(false)
    val isFromDeliveryLabel: StateFlow<Boolean> = _isFromDeliveryLabel.asStateFlow()

    private val _labelCopies = MutableStateFlow(1)
    val labelCopies: StateFlow<Int> = _labelCopies.asStateFlow()

    private val _unitCountInput = MutableStateFlow("1 cs")
    val unitCountInput: StateFlow<String> = _unitCountInput.asStateFlow()

    private val _autoPrint = MutableStateFlow(false)
    val autoPrint: StateFlow<Boolean> = _autoPrint.asStateFlow()

    private val _autoPrintMode = MutableStateFlow("OPENED")
    val autoPrintMode: StateFlow<String> = _autoPrintMode.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to scan Sysco UPC, Delivery Tag, or Admin Badge")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _inventoryList = MutableStateFlow<List<InventoryItem>>(emptyList())
    val inventoryList: StateFlow<List<InventoryItem>> = _inventoryList.asStateFlow()

    private val _selectedPrinter = MutableStateFlow<BluetoothDevice?>(null)
    val selectedPrinter: StateFlow<BluetoothDevice?> = _selectedPrinter.asStateFlow()

    init {
        loadInventory()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun toggleAdminMode() {
        _isAdminMode.value = !_isAdminMode.value
        _statusMessage.value = if (_isAdminMode.value) "Admin Mode Unlocked!" else "Locked to Staff Print Mode."
    }

    fun setAdminMode(enabled: Boolean) {
        _isAdminMode.value = enabled
        _statusMessage.value = if (enabled) "Admin Mode Unlocked!" else "Locked to Staff Print Mode."
    }

    fun setSelectedPrinter(device: BluetoothDevice?) {
        _selectedPrinter.value = device
    }

    fun setAutoPrint(enabled: Boolean) {
        _autoPrint.value = enabled
    }

    fun setAutoPrintMode(mode: String) {
        _autoPrintMode.value = mode
    }

    fun setLabelCopies(copies: Int) {
        _labelCopies.value = copies
    }

    fun setUnitCountInput(input: String) {
        _unitCountInput.value = input
    }

    fun setDetectedDeliveryDate(date: LocalDate) {
        _detectedDeliveryDate.value = date
    }

    fun setDetectedOpenDate(date: LocalDate) {
        _detectedOpenDate.value = date
    }

    fun setStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun loadInventory() {
        _inventoryList.value = productManager.getInventoryList()
    }

    fun updateInventoryCount(name: String, newCount: String) {
        productManager.updateCount(name, newCount)
        loadInventory()
    }

    fun syncInventoryToSheets() {
        viewModelScope.launch {
            _statusMessage.value = "Syncing inventory to Google Sheets..."
            val (success, message) = productManager.syncToGoogleSheetsAsync(_inventoryList.value)
            _statusMessage.value = message
        }
    }

    fun verifyAdminPin(pin: String): Boolean {
        val isValid = productManager.verifyPin(pin)
        if (isValid) {
            setAdminMode(true)
        }
        return isValid
    }

    fun changeAdminPin(newPin: String) {
        productManager.setAdminPin(newPin)
        _statusMessage.value = "Admin PIN updated successfully."
    }

    fun setWebhookUrl(url: String) {
        productManager.setWebhookUrl(url)
        _statusMessage.value = "Google Sheets Webhook URL updated."
    }

    fun getWebhookUrl(): String {
        return productManager.getWebhookUrl()
    }

    fun saveProduct(product: SyscoProduct) {
        productManager.saveProduct(product)
        _scannedProduct.value = product
        _statusMessage.value = "Product details saved for ${product.name}."
    }

    fun searchProducts(query: String): List<SyscoProduct> {
        return productManager.searchProducts(query)
    }

    fun selectProduct(product: SyscoProduct) {
        _scannedProduct.value = product
        val today = LocalDate.now()
        _detectedOpenDate.value = today
        _detectedDeliveryDate.value = today
        _isFromDeliveryLabel.value = false
        val copies = if (product.packSize > 1) product.packSize else 1
        _labelCopies.value = copies
        _unitCountInput.value = "1 cs"
        _statusMessage.value = "Selected: ${product.name} (${product.getFormattedPackInfo()})"
    }

    fun saveQuickAddProduct(product: SyscoProduct) {
        productManager.saveProduct(product)
        selectProduct(product)
    }

    fun handleScannedBarcode(rawBarcode: String) {
        val cleanCode = rawBarcode.trim()

        if (cleanCode == ProductManager.ADMIN_BADGE_CODE) {
            toggleAdminMode()
            return
        }

        val (upc, parsedDelivDate) = parseScannedPayload(cleanCode)
        val prod = productManager.getProduct(upc)
        _scannedProduct.value = prod

        val today = LocalDate.now()
        _detectedOpenDate.value = today

        if (parsedDelivDate != null) {
            _isFromDeliveryLabel.value = true
            _detectedDeliveryDate.value = parsedDelivDate
            _labelCopies.value = 1
            _unitCountInput.value = "1 unit"

            if (_autoPrint.value && _selectedPrinter.value != null) {
                _statusMessage.value = "Delivery tag scanned! Printing Opened label..."
                printOpenedLabel(prod, parsedDelivDate, today, 1, "1 unit")
            } else {
                _statusMessage.value = "Delivered tag detected (${prod.name}). Ready to print Opened label."
            }
        } else {
            _isFromDeliveryLabel.value = false
            _detectedDeliveryDate.value = today
            val copies = if (prod.packSize > 1) prod.packSize else 1
            _labelCopies.value = copies
            _unitCountInput.value = "1 cs"

            if (_autoPrint.value && _selectedPrinter.value != null) {
                if (_autoPrintMode.value == "OPENED") {
                    _statusMessage.value = "Printing $copies opened label(s) for ${prod.name}..."
                    printOpenedLabel(prod, today, today, copies, "1 cs")
                } else {
                    _statusMessage.value = "Printing $copies delivery label(s) for ${prod.name}..."
                    printDeliveryLabel(prod, today, copies, "1 cs")
                }
            } else {
                _statusMessage.value = "Scanned case: ${prod.name} (${prod.packSize} units/cs). Ready to print."
            }
        }
    }

    fun printDeliveryLabel(
        product: SyscoProduct? = _scannedProduct.value,
        delivDate: LocalDate = _detectedDeliveryDate.value,
        quantity: Int = _labelCopies.value,
        unitCount: String = _unitCountInput.value
    ) {
        val prod = product ?: run {
            _statusMessage.value = "No product scanned!"
            return
        }
        val printer = _selectedPrinter.value ?: run {
            _statusMessage.value = "No printer selected!"
            return
        }
        viewModelScope.launch {
            _statusMessage.value = "Printing $quantity delivery label(s)..."
            val zpl = ZplPrinter.generateDeliveryLabel(prod, delivDate, quantity, unitCount)
            val (success, msg) = ZplPrinter.printAsync(printer, zpl)
            _statusMessage.value = msg
        }
    }

    fun printOpenedLabel(
        product: SyscoProduct? = _scannedProduct.value,
        delivDate: LocalDate = _detectedDeliveryDate.value,
        openDate: LocalDate = _detectedOpenDate.value,
        quantity: Int = _labelCopies.value,
        unitCount: String = _unitCountInput.value
    ) {
        val prod = product ?: run {
            _statusMessage.value = "No product scanned!"
            return
        }
        val printer = _selectedPrinter.value ?: run {
            _statusMessage.value = "No printer selected!"
            return
        }
        viewModelScope.launch {
            _statusMessage.value = "Printing $quantity opened label(s)..."
            val zpl = ZplPrinter.generateOpenedLabel(prod, delivDate, openDate, quantity, unitCount)
            val (success, msg) = ZplPrinter.printAsync(printer, zpl)
            _statusMessage.value = msg
        }
    }

    fun printAdminBadge() {
        val printer = _selectedPrinter.value ?: run {
            _statusMessage.value = "No printer selected!"
            return
        }
        viewModelScope.launch {
            _statusMessage.value = "Printing Admin Badge..."
            val zpl = ZplPrinter.generateAdminBadgeLabel()
            val (success, msg) = ZplPrinter.printAsync(printer, zpl)
            _statusMessage.value = msg
        }
    }

    private fun parseScannedPayload(raw: String): Pair<String, LocalDate?> {
        val clean = raw.trim()
        return if (clean.contains("|")) {
            val parts = clean.split("|")
            val upc = parts[0].trim()
            val dateStr = parts.getOrNull(1)?.trim()
            val parsedDate = try {
                if (!dateStr.isNullOrBlank()) {
                    LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yy"))
                } else null
            } catch (e: Exception) {
                null
            }
            Pair(upc, parsedDate)
        } else {
            Pair(clean, null)
        }
    }
}
