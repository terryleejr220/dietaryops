package com.dietaryops.manager.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dietaryops.manager.CameraXBarcodeScanner
import com.dietaryops.manager.ConnectionState
import com.dietaryops.manager.ProductManager
import com.dietaryops.manager.RemoteConfigManager
import com.dietaryops.manager.data.SettingsManager.Companion.DEFAULT_CATEGORIES
import com.dietaryops.manager.ScannedItemState
import com.dietaryops.manager.SyscoProduct
import com.dietaryops.manager.ZebraPrinterManager
import com.dietaryops.manager.ZplGenerator
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ScanRecord
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.util.SyscoUpcNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

val UNIT_OPTIONS = listOf("EA", "LB", "CS", "CTN", "GAL", "BLK", "BOX", "PK")

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ReceivingScreen(
    productManager: ProductManager,
    printerManager: ZebraPrinterManager,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val allCatalogItems by productManager.getAllCatalogItems().collectAsState(initial = emptyList())

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    val printerState by printerManager.connectionState.collectAsState()
    val connectedDevice by printerManager.connectedDevice.collectAsState()
    val pairedPrinters = remember(printerManager) { printerManager.getPairedPrinters() }
    var selectedPrinter by remember { mutableStateOf(printerManager.getPreferredPrinter()) }
    var showPrinterDropdown by remember { mutableStateOf(false) }

    var scannedState by remember { mutableStateOf<ScannedItemState?>(null) }
    var currentScanRecord by remember { mutableStateOf<ScanRecord?>(null) }
    var labelQuantity by remember { mutableStateOf(1) }
    var isScanningEnabled by remember { mutableStateOf(true) }
    var isCameraActive by remember { mutableStateOf(false) } // Default camera OFF for battery savings
    var autoPrint by remember { mutableStateOf(settingsManager.autoPrint) }
    var statusMessage by remember { mutableStateOf("Camera off to save battery. Tap 'Start Scanner' or 'Manual UPC'") }
    var isSyncing by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var manualUpcInput by remember { mutableStateOf("") }
    var showManualInputCard by remember { mutableStateOf(false) }
    var showCustomDaysDialog by remember { mutableStateOf(false) }
    var customDaysInput by remember { mutableStateOf("") }

    // Initialize/update label quantity when scannedState changes
    LaunchedEffect(scannedState) {
        scannedState?.let { state ->
            val defaultQty = if (state.onHandAmount > 0.0) state.onHandAmount.toInt() else settingsManager.defaultLabelQuantity
            labelQuantity = defaultQty.coerceAtLeast(1)
        }
    }

    // Auto-turn off camera while manual typing or editing to save battery
    LaunchedEffect(showManualInputCard, showEditDialog) {
        if (showManualInputCard || showEditDialog) {
            isCameraActive = false
        }
    }

    // Auto-pause camera after inactivity (20s) or post-scan (8s) to save battery life
    LaunchedEffect(isCameraActive, isScanningEnabled) {
        if (isCameraActive && isScanningEnabled) {
            delay(20000)
            isCameraActive = false
            statusMessage = "Camera paused to save battery. Tap 'Resume Scanner' or 'Scan Next'."
        } else if (isCameraActive && !isScanningEnabled) {
            delay(8000)
            isCameraActive = false
        }
    }

    // Real-time scan processor
    val processUpcScan: (String) -> Unit = { rawUpc ->
        coroutineScope.launch {
            val state = productManager.processScan(rawUpc)
            scannedState = state
            isScanningEnabled = false

            // Create local scan record & automatically save to master catalog and log to sheets
            val effectiveOnHand = if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
            val scanRecord = ScanRecord(
                syscoUpc = state.normalizedUpc,
                itemName = state.product.name,
                deliveryDate = state.dateCalculation.deliveryDateIso,
                useByDate = state.dateCalculation.useByDateIso,
                category = state.product.category,
                shelfLifeDays = state.product.shelfLifeDays,
                unit = state.product.unit,
                onHandAmount = effectiveOnHand,
                isAudit = settingsManager.isAuditMode
            )
            productManager.saveProduct(state.product)
            productManager.logScanToSheets(scanRecord)
            currentScanRecord = scanRecord

            // Real-time background sync with Google Sheets
            val webAppUrl = settingsManager.webAppUrl
            if (webAppUrl.isNotBlank()) {
                isSyncing = true
                statusMessage = "Syncing scan to Google Sheets..."
                coroutineScope.launch(Dispatchers.IO) {
                    val result = productManager.repository.syncWithGoogleSheets(webAppUrl)
                    withContext(Dispatchers.Main) {
                        isSyncing = false
                        result.onSuccess { count ->
                            statusMessage = if (count > 0) "Scanned & synced to Google Sheets!" else "Scanned: ${state.product.name}"
                        }.onFailure { err ->
                            statusMessage = "Scanned locally. Sheets sync offline: ${err.localizedMessage}"
                        }
                    }
                }
            }

            // Auto-print if enabled and printer selected
            val activePrinter = connectedDevice ?: selectedPrinter
            val printQty = if (state.onHandAmount > 0.0) state.onHandAmount.toInt() else settingsManager.defaultLabelQuantity
            if (autoPrint && activePrinter != null) {
                statusMessage = "Printing $printQty label(s) for ${state.product.name}..."
                val zpl = ZplGenerator.generateLabel(state.product, state.dateCalculation.deliveryDate, staffInitials = settingsManager.staffInitials)
                printerManager.printDirect(activePrinter, zpl, quantity = printQty) { success, msg ->
                    statusMessage = msg
                    if (success) {
                        coroutineScope.launch {
                            productManager.repository.markRecordPrinted(scanRecord.id)
                        }
                    }
                }
            } else if (!isSyncing) {
                statusMessage = "Scanned: ${state.product.name} (${state.normalizedUpc})"
            }
        }
    }

    val remoteBannerMessage by RemoteConfigManager.bannerMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (remoteBannerMessage.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = remoteBannerMessage,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (settingsManager.isAuditMode) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "🔍 AUDIT / FIRST SCAN MODE: Scans logged for testing & info only",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Printer & Live Status Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Printer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    val (stateColor, stateText) = when (printerState) {
                        ConnectionState.CONNECTED -> Pair(Color(0xFF2E7D32), "CONNECTED")
                        ConnectionState.CONNECTING -> Pair(Color(0xFFF57C00), "CONNECTING")
                        ConnectionState.ERROR -> Pair(MaterialTheme.colorScheme.error, "ERROR")
                        ConnectionState.DISCONNECTED -> Pair(Color.Gray, "DISCONNECTED")
                    }
                    Surface(
                        color = stateColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stateText,
                            color = stateColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Printer selector dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showPrinterDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayName = (connectedDevice ?: selectedPrinter)?.let {
                                try { it.name ?: it.address } catch (_: SecurityException) { it.address }
                            } ?: "No Printer Selected (Tap to pair)"
                            Text(displayName, fontSize = 13.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = showPrinterDropdown,
                        onDismissRequest = { showPrinterDropdown = false }
                    ) {
                        if (pairedPrinters.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No paired Bluetooth printers found") },
                                onClick = { showPrinterDropdown = false }
                            )
                        } else {
                            pairedPrinters.forEach { device ->
                                val devName = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                                DropdownMenuItem(
                                    text = { Text(devName) },
                                    onClick = {
                                        selectedPrinter = device
                                        settingsManager.preferredPrinterAddress = device.address
                                        showPrinterDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-print on scan:", fontSize = 13.sp)
                    Switch(
                        checked = autoPrint,
                        onCheckedChange = {
                            autoPrint = it
                            settingsManager.autoPrint = it
                        }
                    )
                }
            }
        }

        // Action Row: Positioned directly above Camera Window for Ergonomics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isCameraActive = true
                    isScanningEnabled = true
                    scannedState = null
                    currentScanRecord = null
                    statusMessage = "Ready to scan barcode..."
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isCameraActive) Icons.Default.QrCodeScanner else Icons.Default.CameraAlt,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isCameraActive) "Scan Next Barcode" else "Start Scanner")
            }

            OutlinedButton(
                onClick = {
                    val nextShowState = !showManualInputCard
                    showManualInputCard = nextShowState
                    if (nextShowState) {
                        isCameraActive = false // Pause camera while typing
                    } else {
                        isCameraActive = true // Resume camera when manual card closed
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Manual UPC")
            }
        }

        // Camera Preview Area
        if (hasCameraPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                CameraXBarcodeScanner(
                    modifier = Modifier.fillMaxSize(),
                    isScanningEnabled = isScanningEnabled,
                    isCameraActive = isCameraActive,
                    onResumeCamera = {
                        isCameraActive = true
                        isScanningEnabled = true
                        statusMessage = "Ready to scan barcode..."
                    },
                    onBarcodeFound = { rawUpc, _ ->
                        processUpcScan(rawUpc)
                    }
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Camera permission required to scan barcodes.", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Status Indicator Message Banner
        Surface(
            color = if (statusMessage.contains("error", ignoreCase = true) || statusMessage.contains("failed", ignoreCase = true))
                MaterialTheme.colorScheme.errorContainer
            else if (isSyncing)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = statusMessage,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Scanned Product Details Card (UPC, Name, Category, Unit, Delivery Date, Use-By Date)
        scannedState?.let { state ->
            val prod = state.product
            val dateCalc = state.dateCalculation

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Item Barcode: ${prod.upc}", fontSize = 11.sp, color = Color.Gray)
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(if (state.isKnownCatalog) "Catalog Item" else "New Item", fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    if (!state.isKnownCatalog) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Unrecognized Item",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Search/select from catalog dropdown via Edit.",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 11.sp
                                    )
                                }
                                TextButton(onClick = { showEditDialog = true }) {
                                    Text("Select Catalog", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Metadata row: Category chip + Inline On Hand Quantity Controller (aligned horizontally)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(prod.category, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )

                        // Inline On Hand quantity controller badge alongside Category chip
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val displayOnHand = if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
                                Text("On Hand:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                                IconButton(
                                    onClick = {
                                        val current = if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
                                        if (current > 0.5) {
                                            val newQty = (current - 1.0).coerceAtLeast(0.5)
                                            scannedState = state.copy(onHandAmount = newQty)
                                            currentScanRecord = currentScanRecord?.copy(onHandAmount = newQty)
                                            coroutineScope.launch {
                                                currentScanRecord?.let { rec ->
                                                    productManager.repository.updateScanRecordOnHandAmount(rec.id, state.normalizedUpc, newQty)
                                                }
                                                val cat = productManager.repository.getCatalogItem(state.normalizedUpc)
                                                if (cat.name != "Unrecognized Item") {
                                                    productManager.repository.saveCatalogItem(cat.copy(lastOnHandAmount = newQty))
                                                }
                                            }
                                        }
                                    },
                                    enabled = (if (state.onHandAmount > 0.0) state.onHandAmount else 1.0) > 0.5,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease On Hand", modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = "${if (displayOnHand % 1.0 == 0.0) displayOnHand.toInt().toString() else displayOnHand} ${prod.unit}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                IconButton(
                                    onClick = {
                                        val current = if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
                                        val newQty = current + 1.0
                                        scannedState = state.copy(onHandAmount = newQty)
                                        currentScanRecord = currentScanRecord?.copy(onHandAmount = newQty)
                                        coroutineScope.launch {
                                            currentScanRecord?.let { rec ->
                                                productManager.repository.updateScanRecordOnHandAmount(rec.id, state.normalizedUpc, newQty)
                                            }
                                            val cat = productManager.repository.getCatalogItem(state.normalizedUpc)
                                            if (cat.name != "Unrecognized Item") {
                                                productManager.repository.saveCatalogItem(cat.copy(lastOnHandAmount = newQty))
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase On Hand", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Dates Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Delivery Date", fontSize = 11.sp, color = Color.Gray)
                            Text(dateCalc.deliveryDateFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Use-By / Expiration", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                "${dateCalc.useByDateFormatted} (+${prod.shelfLifeDays}d)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Quick Shelf Life Chips Selector
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Quick Shelf Life:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "+3d" to 3,
                                "+7d" to 7,
                                "+14d" to 14,
                                "+30d" to 30,
                                "+90d" to 90,
                                "+180d" to 180,
                                "+365d" to 365
                            ).forEach { (label, days) ->
                                val isSelected = prod.shelfLifeDays == days
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val newCalc = DateCalculator.calculate(
                                            scanDate = dateCalc.deliveryDate,
                                            shelfLifeDays = days,
                                            category = prod.category
                                        )
                                        val updatedState = state.copy(
                                            product = prod.copy(shelfLifeDays = days),
                                            dateCalculation = newCalc
                                        )
                                        scannedState = updatedState
                                        currentScanRecord = currentScanRecord?.copy(
                                            shelfLifeDays = days,
                                            useByDate = newCalc.useByDateIso
                                        )
                                        coroutineScope.launch {
                                            currentScanRecord?.let { rec ->
                                                productManager.repository.addScanRecord(rec.copy(shelfLifeDays = days, useByDate = newCalc.useByDateIso))
                                            }
                                        }
                                    },
                                    label = { Text(label, fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                            AssistChip(
                                onClick = { showCustomDaysDialog = true },
                                label = { Text("+Custom", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    // Label Quantity Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Number of Labels:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (labelQuantity > 1) labelQuantity-- },
                                    enabled = labelQuantity > 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease labels", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = "$labelQuantity",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { labelQuantity++ },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase labels", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Actions Row: Print Label & Save & Sync
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val activePrinter = connectedDevice ?: selectedPrinter
                                if (activePrinter != null) {
                                    val zpl = ZplGenerator.generateLabel(
                                        itemName = prod.name,
                                        upc = prod.upc,
                                        deliveryDate = dateCalc.deliveryDate,
                                        useByDate = dateCalc.useByDate,
                                        category = prod.category,
                                        onHandAmount = state.onHandAmount,
                                        staffInitials = settingsManager.staffInitials
                                    )
                                    printerManager.printDirect(activePrinter, zpl, quantity = labelQuantity) { _, msg ->
                                        statusMessage = msg
                                    }
                                } else {
                                    Toast.makeText(context, "Select or connect printer first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print ($labelQuantity)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val recordToSave = (currentScanRecord ?: ScanRecord(
                                        syscoUpc = state.normalizedUpc,
                                        itemName = state.product.name,
                                        deliveryDate = state.dateCalculation.deliveryDateIso,
                                        useByDate = state.dateCalculation.useByDateIso,
                                        category = state.product.category,
                                        shelfLifeDays = state.product.shelfLifeDays,
                                        unit = state.product.unit,
                                        onHandAmount = state.onHandAmount,
                                        isAudit = settingsManager.isAuditMode
                                    )).copy(
                                        itemName = state.product.name,
                                        deliveryDate = state.dateCalculation.deliveryDateIso,
                                        useByDate = state.dateCalculation.useByDateIso,
                                        category = state.product.category,
                                        shelfLifeDays = state.product.shelfLifeDays,
                                        unit = state.product.unit,
                                        onHandAmount = state.onHandAmount,
                                        isAudit = settingsManager.isAuditMode
                                    )
                                    productManager.repository.addScanRecord(recordToSave)
                                    currentScanRecord = recordToSave

                                    val webAppUrl = settingsManager.webAppUrl
                                    if (webAppUrl.isNotBlank()) {
                                        isSyncing = true
                                        statusMessage = "Syncing with Google Sheets..."
                                        val res = productManager.repository.syncWithGoogleSheets(webAppUrl)
                                        isSyncing = false
                                        res.onSuccess {
                                            statusMessage = "Saved & Synced to Google Sheets!"
                                            Toast.makeText(context, "Saved & Synced to Google Sheets!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            statusMessage = "Saved locally. Sync error: ${err.localizedMessage}"
                                        }
                                    } else {
                                        Toast.makeText(context, "Saved record locally!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save & Sync", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.weight(0.7f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Manual UPC Entry Card
        AnimatedVisibility(visible = showManualInputCard) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Manual Barcode Lookup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = manualUpcInput,
                        onValueChange = { manualUpcInput = it },
                        label = { Text("Enter 12-digit Item Barcode (UPC)") },
                        placeholder = { Text("e.g. 074865123401") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (manualUpcInput.isNotBlank()) {
                                processUpcScan(manualUpcInput)
                                showManualInputCard = false
                            } else {
                                Toast.makeText(context, "Please enter a valid UPC barcode", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Lookup & Calculate")
                    }
                }
            }
        }
    }

    // Manual Edit / Override Dialog
    if (showEditDialog && scannedState != null) {
        val currentState = scannedState!!
        var editName by remember { mutableStateOf(currentState.product.name) }
        var editCategory by remember { mutableStateOf(currentState.product.category) }
        var editUnit by remember { mutableStateOf(currentState.product.unit) }
        val initialOnHand = if (currentState.onHandAmount > 0.0) currentState.onHandAmount else 1.0
        var editOnHandStr by remember { mutableStateOf(if (initialOnHand % 1.0 == 0.0) initialOnHand.toInt().toString() else initialOnHand.toString()) }
        var editDaysStr by remember { mutableStateOf(currentState.product.shelfLifeDays.toString()) }
        var editDeliveryDateStr by remember { mutableStateOf(currentState.dateCalculation.deliveryDateIso) }

        var isCategoryExpanded by remember { mutableStateOf(false) }
        var isUnitExpanded by remember { mutableStateOf(false) }

        val editDaysInt = editDaysStr.toIntOrNull() ?: currentState.product.shelfLifeDays
        val parsedDeliveryDate = DateCalculator.parseDate(editDeliveryDateStr) ?: LocalDate.now()
        val calculatedPreview = DateCalculator.calculate(
            scanDate = parsedDeliveryDate,
            shelfLifeDays = editDaysInt
        )

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Item & Date Override") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Item Barcode: ${currentState.normalizedUpc}", fontSize = 12.sp, color = Color.Gray)

                    var nameDropdownExpanded by remember { mutableStateOf(false) }
                    val filteredNames = allCatalogItems.filter { it.name.contains(editName, ignoreCase = true) && it.name.isNotBlank() && !it.name.startsWith("Item (") }

                    ExposedDropdownMenuBox(
                        expanded = nameDropdownExpanded,
                        onExpandedChange = { nameDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { 
                                editName = it
                                nameDropdownExpanded = true
                            },
                            label = { Text("Product Name") },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        if (filteredNames.isNotEmpty() && nameDropdownExpanded) {
                            ExposedDropdownMenu(
                                expanded = nameDropdownExpanded,
                                onDismissRequest = { nameDropdownExpanded = false }
                            ) {
                                filteredNames.take(5).forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.name) },
                                        onClick = {
                                            editName = item.name
                                            editCategory = item.category
                                            editDaysStr = item.defaultShelfLifeDays.toString()
                                            editOnHandStr = item.lastOnHandAmount.toString()
                                            editUnit = item.unit
                                            nameDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editOnHandStr,
                        onValueChange = { editOnHandStr = it },
                        label = { Text("On Hand Amount / Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Selector
                    ExposedDropdownMenuBox(
                        expanded = isCategoryExpanded,
                        onExpandedChange = { isCategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isCategoryExpanded,
                            onDismissRequest = { isCategoryExpanded = false }
                        ) {
                            DEFAULT_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        editCategory = cat
                                        editDaysStr = DateCalculator.getShelfLifeDaysForCategory(cat).toString()
                                        isCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit Selector
                    ExposedDropdownMenuBox(
                        expanded = isUnitExpanded,
                        onExpandedChange = { isUnitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit of Measure") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isUnitExpanded,
                            onDismissRequest = { isUnitExpanded = false }
                        ) {
                            UNIT_OPTIONS.forEach { unitOpt ->
                                DropdownMenuItem(
                                    text = { Text(unitOpt) },
                                    onClick = {
                                        editUnit = unitOpt
                                        isUnitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editDeliveryDateStr,
                        onValueChange = { editDeliveryDateStr = it },
                        label = { Text("Delivery Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Shelf Life Chips in Edit Dialog
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Quick Shelf Life Preset:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(3, 7, 14, 30, 90, 180, 365).forEach { d ->
                                FilterChip(
                                    selected = editDaysStr == d.toString(),
                                    onClick = { editDaysStr = d.toString() },
                                    label = { Text("+${d}d", fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editDaysStr,
                        onValueChange = { editDaysStr = it },
                        label = { Text("Shelf Life Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Preview Calculated Dates:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Delivery: ${calculatedPreview.deliveryDateFormatted}", fontSize = 13.sp)
                            Text(
                                "Use By: ${calculatedPreview.useByDateFormatted} (+${calculatedPreview.shelfLifeDays}d)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        val validDays = DateCalculator.clampShelfLifeDays(editDaysInt)
                        val parsedOnHand = editOnHandStr.toDoubleOrNull()
                        val editOnHand = if (parsedOnHand != null && parsedOnHand > 0.0) parsedOnHand else initialOnHand
                        val scannedUpc = currentState.normalizedUpc.ifBlank { currentState.rawUpc.trim() }
                        val cleanName = editName.ifBlank { "Unrecognized Item" }

                        // Preserve exact scanned barcode & register alternate barcode mapping if linking to existing catalog item
                        val savedCatalogItem = productManager.linkBarcodeToCatalogItem(
                            scannedUpc = scannedUpc,
                            productName = cleanName,
                            category = editCategory,
                            unit = editUnit,
                            shelfLifeDays = validDays,
                            onHandAmount = editOnHand
                        )

                        val updatedProduct = SyscoProduct(
                            upc = scannedUpc,
                            name = cleanName,
                            shelfLifeDays = validDays,
                            category = editCategory,
                            unit = editUnit,
                            lastOnHandAmount = editOnHand,
                            syscoItemNumber = savedCatalogItem.syscoItemNumber,
                            piazzaItemNumber = savedCatalogItem.piazzaItemNumber,
                            vendorItemNumber = savedCatalogItem.vendorItemNumber,
                            vendorSku = savedCatalogItem.vendorSku,
                            alternateBarcodes = savedCatalogItem.alternateBarcodes
                        )

                        val newCalc = DateCalculator.calculate(
                            scanDate = parsedDeliveryDate,
                            shelfLifeDays = validDays
                        )
                        val updatedState = ScannedItemState(
                            rawUpc = currentState.rawUpc,
                            normalizedUpc = scannedUpc,
                            product = updatedProduct,
                            dateCalculation = newCalc,
                            isKnownCatalog = true,
                            onHandAmount = editOnHand
                        )
                        scannedState = updatedState

                        // Update local scan record in Room database preserving exact scanned UPC
                        val existingRecordId = currentScanRecord?.id ?: UUID.randomUUID().toString()
                        val updatedScanRecord = ScanRecord(
                            id = existingRecordId,
                            syscoUpc = scannedUpc,
                            itemName = updatedProduct.name,
                            deliveryDate = newCalc.deliveryDateIso,
                            useByDate = newCalc.useByDateIso,
                            category = updatedProduct.category,
                            shelfLifeDays = updatedProduct.shelfLifeDays,
                            unit = updatedProduct.unit,
                            onHandAmount = editOnHand,
                            printed = currentScanRecord?.printed ?: false,
                            syncedToSheets = false,
                            isAudit = settingsManager.isAuditMode
                        )
                        productManager.repository.addScanRecord(updatedScanRecord)
                        currentScanRecord = updatedScanRecord

                        showEditDialog = false
                        statusMessage = "Saved product & date override!"
                        Toast.makeText(context, "Saved product & date override!", Toast.LENGTH_SHORT).show()
                        
                        val webAppUrl = settingsManager.webAppUrl
                        if (webAppUrl.isNotBlank()) {
                            isSyncing = true
                            val res = productManager.repository.syncWithGoogleSheets(webAppUrl)
                            isSyncing = false
                            res.onSuccess {
                                statusMessage = "Saved & Synced to Google Sheets!"
                            }.onFailure { err ->
                                statusMessage = "Saved locally. Sync error: ${err.localizedMessage}"
                            }
                        }
                    }
                }) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Custom Shelf Life Days Input Dialog
    if (showCustomDaysDialog && scannedState != null) {
        val currentState = scannedState!!
        AlertDialog(
            onDismissRequest = {
                showCustomDaysDialog = false
                customDaysInput = ""
            },
            title = { Text("Enter Custom Shelf-Life Days") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set custom retention shelf life in days for ${currentState.product.name}:", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = customDaysInput,
                        onValueChange = { customDaysInput = it },
                        label = { Text("Shelf Life Days (e.g. 21)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = customDaysInput.toIntOrNull()
                        if (days != null && days > 0) {
                            val validDays = DateCalculator.clampShelfLifeDays(days, currentState.product.category)
                            val newCalc = DateCalculator.calculate(
                                scanDate = currentState.dateCalculation.deliveryDate,
                                shelfLifeDays = validDays,
                                category = currentState.product.category
                            )
                            val updatedState = currentState.copy(
                                product = currentState.product.copy(shelfLifeDays = validDays),
                                dateCalculation = newCalc
                            )
                            scannedState = updatedState
                            currentScanRecord = currentScanRecord?.copy(
                                shelfLifeDays = validDays,
                                useByDate = newCalc.useByDateIso
                            )
                            coroutineScope.launch {
                                currentScanRecord?.let { rec ->
                                    productManager.repository.addScanRecord(rec.copy(shelfLifeDays = validDays, useByDate = newCalc.useByDateIso))
                                }
                            }
                            showCustomDaysDialog = false
                            customDaysInput = ""
                            Toast.makeText(context, "Set custom shelf life to +${validDays}d!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enter a valid positive number of days", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomDaysDialog = false
                        customDaysInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
