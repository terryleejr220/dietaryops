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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
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
import com.dietaryops.manager.ui.components.ScannerOverlay
import com.dietaryops.manager.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.dietaryops.manager.data.model.AppPermission
import com.dietaryops.manager.ui.components.LearnOnScanBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
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
    var isCameraActive by remember { mutableStateOf(true) } // Active camera for instant scanning
    var autoPrint by remember { mutableStateOf(settingsManager.autoPrint) }
    var statusMessage by remember { mutableStateOf("Ready to scan Sysco UPC, Case Barcode, or EVS Item") }
    var isSyncing by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var manualUpcInput by remember { mutableStateOf("") }
    var showManualInputCard by remember { mutableStateOf(false) }
    var showTruckManifestCard by remember { mutableStateOf(false) }
    var showCustomDaysDialog by remember { mutableStateOf(false) }
    var customDaysInput by remember { mutableStateOf("") }

    // Learn on Scan Bottom Sheet state
    var showLearnOnScanSheet by remember { mutableStateOf(false) }
    var unmappedBarcodeForLearning by remember { mutableStateOf("") }

    // High-Quantity Print Warning Safety Dialog state
    var pendingHighQtyJob by remember { mutableStateOf<Pair<String, Pair<Int, String>>?>(null) }

    // Prep Sticker Dialog state
    var showPrepStickerDialog by remember { mutableStateOf(false) }
    var prepItemNameInput by remember { mutableStateOf("") }
    var prepCategoryInput by remember { mutableStateOf("PREPARED") }
    var prepRetentionDaysInput by remember { mutableStateOf(7) }
    var prepLabelQuantity by remember { mutableStateOf(1) }

    var lastSyncedRecordId by remember { mutableStateOf<String?>(null) }
    var recordHasUnsyncedChanges by remember { mutableStateOf(false) }
    var isPackageOpened by remember { mutableStateOf(false) }

    val currentStaffAttribution = remember(settingsManager.staffName, settingsManager.employeeId) {
        if (settingsManager.employeeId.isNotBlank()) {
            "${settingsManager.staffName} (${settingsManager.employeeId})"
        } else {
            settingsManager.staffName
        }
    }

    // Initialize/update label quantity when scannedState changes (Case Pack Wizard)
    LaunchedEffect(scannedState) {
        scannedState?.let { state ->
            val casePack = state.product.casePackQty.coerceAtLeast(1)
            val defaultQty = if (state.onHandAmount > 0.0) {
                (state.onHandAmount * casePack).toInt()
            } else {
                casePack * settingsManager.defaultLabelQuantity
            }
            labelQuantity = defaultQty.coerceAtLeast(1)
        }
    }

    // Real-time scan processor (Non-blocking async execution)
    val processUpcScan: (String) -> Unit = { rawUpc ->
        val normUpc = SyscoUpcNormalizer.normalize(rawUpc).ifBlank { rawUpc.trim() }
        statusMessage = "Scanned: $normUpc — Processing..."

        coroutineScope.launch(Dispatchers.IO) {
            val state = productManager.processScan(rawUpc)
            withContext(Dispatchers.Main) {
                scannedState = state
                isScanningEnabled = false
                isPackageOpened = false
                recordHasUnsyncedChanges = false
                statusMessage = "Scanned: ${state.product.displayNameForLabel} ($normUpc)"
            }

            // Create local scan record & save to master catalog with staff attribution
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
                isAudit = settingsManager.isAuditMode,
                receivedBy = currentStaffAttribution
            )
            productManager.saveProduct(state.product)
            withContext(Dispatchers.Main) {
                currentScanRecord = scanRecord
            }

            // Single sync entry point: saves to Room then pushes to Sheets in background
            productManager.logScanAsync(scanRecord, coroutineScope) { result ->
                result.onSuccess { count ->
                    lastSyncedRecordId = scanRecord.id
                    recordHasUnsyncedChanges = false
                }
            }

            // Auto-print if enabled and printer selected (Prevent auto-printing Unrecognized Items)
            val isKnownItem = state.isKnownCatalog &&
                    !state.product.name.startsWith("Unrecognized", ignoreCase = true) &&
                    !state.product.name.startsWith("Item (", ignoreCase = true)

            val activePrinter = connectedDevice ?: selectedPrinter
            val casePack = state.product.casePackQty.coerceAtLeast(1)
            val printQty = if (state.onHandAmount > 0.0) {
                (state.onHandAmount * casePack).toInt()
            } else {
                casePack * settingsManager.defaultLabelQuantity
            }

            if (autoPrint && activePrinter != null && isKnownItem) {
                val zpl = if (settingsManager.department.equals(
                        "Environmental Services",
                        ignoreCase = true
                    ) || settingsManager.department.equals("EVS", ignoreCase = true)
                ) {
                    ZplGenerator.generateShelfLabelZpl(
                        item = state.product,
                        companyHeader = "EVS SHELF LABEL"
                    )
                } else {
                    ZplGenerator.generateLabel(
                        state.product,
                        state.dateCalculation.deliveryDate,
                        staffInitials = settingsManager.staffInitials
                    )
                }
                // Auto-print is strictly hard-capped to 1 label per box scanned
                printerManager.printDirect(activePrinter, zpl, quantity = 1) { success, msg ->
                    coroutineScope.launch(Dispatchers.Main) {
                        statusMessage = msg
                    }
                    if (success) {
                        coroutineScope.launch { productManager.markRecordPrinted(scanRecord.id) }
                    }
                }
            } else if (!isKnownItem) {
                withContext(Dispatchers.Main) {
                    unmappedBarcodeForLearning = normUpc
                    showLearnOnScanSheet = true
                    statusMessage =
                        "⚠️ Unrecognized Item ($normUpc) — Select matching item in Learn on Scan."
                }
            }
        }
    }

    if (showLearnOnScanSheet && unmappedBarcodeForLearning.isNotBlank()) {
        LearnOnScanBottomSheet(
            unrecognizedUpcOrItemNum = unmappedBarcodeForLearning,
            productManager = productManager,
            settingsManager = settingsManager,
            onDismiss = { showLearnOnScanSheet = false },
            onLinkedSuccess = { mappedItem ->
                showLearnOnScanSheet = false
                processUpcScan(mappedItem.syscoUpc)
            }
        )
    }

    if (pendingHighQtyJob != null) {
        val (itemName, pair) = pendingHighQtyJob!!
        val (quantity, zpl) = pair
        AlertDialog(
            onDismissRequest = { pendingHighQtyJob = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("⚠️ High Print Quantity Warning", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You are about to print $quantity labels for '$itemName'. Are you sure this quantity is correct?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activePrinter = connectedDevice ?: selectedPrinter
                        if (activePrinter != null) {
                            printerManager.printDirect(
                                activePrinter,
                                zpl,
                                quantity = quantity
                            ) { _, msg ->
                                statusMessage = msg
                            }
                        }
                        pendingHighQtyJob = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Print $quantity Labels")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHighQtyJob = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val remoteBannerMessage by RemoteConfigManager.bannerMessage.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AdminDrawerContent(
                settingsManager = settingsManager,
                printerState = printerState,
                connectedDevice = connectedDevice,
                pairedPrinters = pairedPrinters,
                selectedPrinter = selectedPrinter,
                onPrinterSelected = {
                    selectedPrinter = it
                    settingsManager.preferredPrinterAddress = it.address
                },
                autoPrint = autoPrint,
                onAutoPrintChanged = {
                    autoPrint = it
                    settingsManager.autoPrint = it
                },
                manualUpcInput = manualUpcInput,
                onManualUpcInputChanged = { manualUpcInput = it },
                onManualUpcSubmit = { upc ->
                    if (upc.isNotBlank()) {
                        processUpcScan(upc)
                        manualUpcInput = ""
                        coroutineScope.launch { drawerState.close() }
                    }
                },
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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

                // Open Admin Drawer Button
                if (settingsManager.currentStaffUser.hasPermission(AppPermission.PRINTER_SETTINGS) ||
                    settingsManager.currentStaffUser.hasPermission(AppPermission.BASIC_ADMIN_SETTINGS)
                ) {
                    Button(
                        onClick = { coroutineScope.launch { drawerState.open() } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Admin Options", fontWeight = FontWeight.Bold)
                    }
                }

                // Action Row 1: Primary Scanner Button (Full Width, Spacious)
                Button(
                    onClick = {
                        isCameraActive = true
                        isScanningEnabled = true
                        scannedState = null
                        currentScanRecord = null
                        statusMessage = "Ready to scan barcode..."
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isCameraActive) Icons.Default.QrCodeScanner else Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCameraActive) "Scan Next Barcode" else "Start Scanner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Action Row 2: Secondary Quick Buttons (Manual Lookup, Truck OTW, Prep Sticker)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val nextShowState = !showManualInputCard
                            showManualInputCard = nextShowState
                            if (nextShowState) {
                                showTruckManifestCard = false
                                isCameraActive = false
                            } else {
                                isCameraActive = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Manual UPC", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val nextShowState = !showTruckManifestCard
                            showTruckManifestCard = nextShowState
                            if (nextShowState) {
                                showManualInputCard = false
                                isCameraActive = false
                            } else {
                                isCameraActive = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (showTruckManifestCard) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Truck OTW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            showPrepStickerDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Prep Sticker", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Manual UPC Search & Live Autocomplete Card
                if (showManualInputCard) {
                    var searchResults by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

                    LaunchedEffect(manualUpcInput) {
                        if (manualUpcInput.isNotBlank()) {
                            searchResults = productManager.searchCatalogItems(manualUpcInput)
                        } else {
                            searchResults = emptyList()
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "🔍 Manual UPC or Sysco Item # Keypad",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            OutlinedTextField(
                                value = manualUpcInput,
                                onValueChange = { manualUpcInput = it },
                                label = { Text("Type UPC, 6-7 Digit Sysco Item #, or Name") },
                                placeholder = { Text("e.g. 4455648 for Quaker Oats or 074865027137") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (manualUpcInput.isNotEmpty()) {
                                        IconButton(onClick = { manualUpcInput = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (manualUpcInput.isNotBlank()) {
                                            processUpcScan(manualUpcInput)
                                            showManualInputCard = false
                                            isCameraActive = true
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (manualUpcInput.isNotBlank()) {
                                        processUpcScan(manualUpcInput)
                                        showManualInputCard = false
                                        isCameraActive = true
                                    }
                                },
                                enabled = manualUpcInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lookup & Process Item", fontWeight = FontWeight.Bold)
                            }

                            if (searchResults.isNotEmpty()) {
                                Text(
                                    "Matching Catalog Items (${searchResults.size}):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    searchResults.forEach { matchItem ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.6f
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    processUpcScan(matchItem.syscoUpc)
                                                    showManualInputCard = false
                                                    isCameraActive = true
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 6.dp
                                                ),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        matchItem.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        "UPC: ${matchItem.syscoUpc} \u2022 ${matchItem.category}",
                                                        fontSize = 10.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.ArrowForward,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (manualUpcInput.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            processUpcScan(manualUpcInput)
                                            showManualInputCard = false
                                            isCameraActive = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lookup '$manualUpcInput'", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Truck OTW Guided Manifest Checklist Card
                if (showTruckManifestCard) {
                    val otwItems = remember(allCatalogItems) {
                        allCatalogItems.filter { item ->
                            item.category.contains("OTW", ignoreCase = true) ||
                            item.vendorName.contains("OTW", ignoreCase = true) ||
                            (item.parLevel > 0 && item.lastOnHandAmount < item.parLevel)
                        }
                    }

                    var manifestFilter by remember { mutableStateOf("All Rooms") }
                    val filteredOtw = remember(otwItems, manifestFilter) {
                        if (manifestFilter == "All Rooms") otwItems else otwItems.filter { ZplGenerator.getStorageLocationForCategory(it.category).contains(manifestFilter, ignoreCase = true) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("🚛 Truck OTW Guided Manifest", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                IconButton(onClick = { showTruckManifestCard = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }

                            Text(
                                "Receive boxes one-by-one as you unload on the dock. Tapping 'Receive 1 Case & Print' increments stock and prints 1 exact label for that box immediately:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Storage Filter Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("All Rooms", "Cooler", "Freezer", "Dry Storage").forEach { room ->
                                    FilterChip(
                                        selected = manifestFilter == room,
                                        onClick = { manifestFilter = room },
                                        label = { Text(room, fontSize = 11.sp) }
                                    )
                                }
                            }

                            if (filteredOtw.isEmpty()) {
                                Text("No pending truck items found for $manifestFilter.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                                ) {
                                    filteredOtw.forEach { item ->
                                        val location = ZplGenerator.getStorageLocationForCategory(item.category)
                                        var isReceived by remember { mutableStateOf(false) }

                                        Surface(
                                            color = if (isReceived) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.displayNameForLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Room: $location • On-Hand: ${item.lastOnHandAmount} ${item.unit}", fontSize = 10.sp, color = Color.Gray)
                                                }

                                                Button(
                                                    onClick = {
                                                        isReceived = true
                                                        val scanRecord = ScanRecord(
                                                            syscoUpc = item.syscoUpc,
                                                            itemName = item.resolvedItemName,
                                                            deliveryDate = LocalDate.now().toString(),
                                                            useByDate = LocalDate.now().plusDays(item.defaultShelfLifeDays.toLong()).toString(),
                                                            category = item.category,
                                                            shelfLifeDays = item.defaultShelfLifeDays,
                                                            unit = item.unit,
                                                            onHandAmount = 1.0,
                                                            receivedBy = currentStaffAttribution
                                                        )

                                                        coroutineScope.launch {
                                                            productManager.repository.addScanRecord(scanRecord)
                                                            productManager.logScanToSheets(scanRecord)

                                                            val activePrinter = connectedDevice ?: selectedPrinter
                                                            if (activePrinter != null) {
                                                                val zpl = ZplGenerator.generateLabel(
                                                                    itemName = item.displayNameForLabel,
                                                                    upc = item.syscoUpc,
                                                                    deliveryDate = LocalDate.now(),
                                                                    useByDate = LocalDate.now().plusDays(item.defaultShelfLifeDays.toLong()),
                                                                    category = item.category,
                                                                    storageLocation = location,
                                                                    staffInitials = settingsManager.staffInitials
                                                                )
                                                                printerManager.printDirect(activePrinter, zpl, quantity = 1) { _, msg ->
                                                                    statusMessage = msg
                                                                }
                                                            } else {
                                                                statusMessage = "Received 1 case of ${item.displayNameForLabel}! (Connect Zebra printer to auto-print label)"
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isReceived) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(if (isReceived) Icons.Default.Check else Icons.Default.Print, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (isReceived) "Rec'd (+1)" else "Receive & Print", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Camera Preview Area with High-Tech Scanner HUD Reticle Overlay
                if (hasCameraPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
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

                            // Futuristic HUD Laser Reticle Overlay
                            ScannerOverlay(
                                isScanningActive = isCameraActive && isScanningEnabled,
                                isCameraActive = isCameraActive,
                                onResumeCamera = {
                                    isCameraActive = true
                                    isScanningEnabled = true
                                    statusMessage = "Ready to scan barcode..."
                                }
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Camera permission required to scan barcodes.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Status Indicator Message Banner
                Surface(
                    color = if (statusMessage.contains(
                            "error",
                            ignoreCase = true
                        ) || statusMessage.contains("failed", ignoreCase = true)
                    )
                        MaterialTheme.colorScheme.errorContainer
                    else if (isSyncing)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = statusMessage,
                            fontSize = 12.sp,
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
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Title & Catalog Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "UPC: ${prod.upc}",
                                            style = MonospaceBarcodeStyle,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    color = if (state.isKnownCatalog) FreshGreen.copy(alpha = 0.15f) else ExpiringSoonAmber.copy(
                                        alpha = 0.15f
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (state.isKnownCatalog) FreshGreen.copy(alpha = 0.4f) else ExpiringSoonAmber.copy(
                                            alpha = 0.4f
                                        )
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 4.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isKnownCatalog) Icons.Default.CheckCircle else Icons.Default.Help,
                                            contentDescription = null,
                                            tint = if (state.isKnownCatalog) FreshGreen else ExpiringSoonAmber,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (state.isKnownCatalog) "CATALOG" else "NEW ITEM",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (state.isKnownCatalog) FreshGreen else ExpiringSoonAmber
                                        )
                                    }
                                }
                            }

                            if (!state.isKnownCatalog) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(10.dp),
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
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Select from catalog dropdown via Edit.",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 11.sp
                                            )
                                        }
                                        TextButton(onClick = { showEditDialog = true }) {
                                            Text(
                                                "Match Catalog",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Metadata row: Category chip + Inline On Hand Quantity Controller
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Pill
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 4.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Category,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = prod.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                // Inline On Hand quantity controller badge with tactile circular buttons
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 3.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val displayOnHand =
                                            if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
                                        Text(
                                            "On Hand:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable(enabled = displayOnHand > 0.5) {
                                                    val current =
                                                        if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
                                                    if (current > 0.5) {
                                                        val newQty =
                                                            (current - 1.0).coerceAtLeast(0.5)
                                                        scannedState =
                                                            state.copy(onHandAmount = newQty)
                                                        currentScanRecord =
                                                            currentScanRecord?.copy(onHandAmount = newQty)
                                                        coroutineScope.launch {
                                                            currentScanRecord?.let { rec ->
                                                                productManager.updateOnHandAmount(
                                                                    rec.id,
                                                                    state.normalizedUpc,
                                                                    newQty
                                                                )
                                                            }
                                                            val cat =
                                                                productManager.getCatalogItem(state.normalizedUpc)
                                                            if (cat.name != "Unrecognized Item") {
                                                                productManager.saveCatalogItem(
                                                                    cat.copy(
                                                                        lastOnHandAmount = newQty
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Remove,
                                                    contentDescription = "Decrease On Hand",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${
                                                if (displayOnHand % 1.0 == 0.0) displayOnHand.toInt()
                                                    .toString() else displayOnHand
                                            } ${prod.unit}",
                                            style = MonospaceQuantityStyle,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable {
                                                    val current =
                                                        if (state.onHandAmount > 0.0) state.onHandAmount else 1.0
                                                    val newQty = current + 1.0
                                                    scannedState = state.copy(onHandAmount = newQty)
                                                    currentScanRecord =
                                                        currentScanRecord?.copy(onHandAmount = newQty)
                                                    coroutineScope.launch {
                                                        currentScanRecord?.let { rec ->
                                                            productManager.updateOnHandAmount(
                                                                rec.id,
                                                                state.normalizedUpc,
                                                                newQty
                                                            )
                                                        }
                                                        val cat =
                                                            productManager.getCatalogItem(state.normalizedUpc)
                                                        if (cat.name != "Unrecognized Item") {
                                                            productManager.saveCatalogItem(
                                                                cat.copy(
                                                                    lastOnHandAmount = newQty
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Increase On Hand",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Side-by-Side Visual Date Boxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Received Date Box
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "RECEIVED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            dateCalc.deliveryDateFormatted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                // Use-By Expiration Box with Freshness Indicator
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Timer,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "USE BY",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }

                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "+${prod.shelfLifeDays}d",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(
                                                        horizontal = 5.dp,
                                                        vertical = 1.dp
                                                    )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = dateCalc.useByDateFormatted,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Package Condition: Unopened vs Opened / In-Use (ServSafe standard: 365d unopened vs 14d opened)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Package Condition:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !isPackageOpened,
                                        onClick = {
                                            if (isPackageOpened) {
                                                isPackageOpened = false
                                                recordHasUnsyncedChanges = true
                                                val unopenedDays =
                                                    DateCalculator.getShelfLifeDaysForCategory(prod.category)
                                                val newCalc = DateCalculator.calculate(
                                                    scanDate = dateCalc.deliveryDate,
                                                    shelfLifeDays = unopenedDays,
                                                    category = prod.category
                                                )
                                                val updatedState = state.copy(
                                                    product = prod.copy(shelfLifeDays = unopenedDays),
                                                    dateCalculation = newCalc
                                                )
                                                scannedState = updatedState
                                                currentScanRecord = currentScanRecord?.copy(
                                                    shelfLifeDays = unopenedDays,
                                                    useByDate = newCalc.useByDateIso
                                                )
                                                coroutineScope.launch {
                                                    currentScanRecord?.let { rec ->
                                                        productManager.addScanRecord(
                                                            rec.copy(
                                                                shelfLifeDays = unopenedDays,
                                                                useByDate = newCalc.useByDateIso
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                "📦 Unopened Commercial (+365d)",
                                                fontSize = 11.sp,
                                                fontWeight = if (!isPackageOpened) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.height(32.dp)
                                    )

                                    FilterChip(
                                        selected = isPackageOpened,
                                        onClick = {
                                            if (!isPackageOpened) {
                                                isPackageOpened = true
                                                recordHasUnsyncedChanges = true
                                                val openedDays = 14
                                                val newCalc = DateCalculator.calculate(
                                                    scanDate = dateCalc.deliveryDate,
                                                    shelfLifeDays = openedDays,
                                                    category = prod.category
                                                )
                                                val updatedState = state.copy(
                                                    product = prod.copy(shelfLifeDays = openedDays),
                                                    dateCalculation = newCalc
                                                )
                                                scannedState = updatedState
                                                currentScanRecord = currentScanRecord?.copy(
                                                    shelfLifeDays = openedDays,
                                                    useByDate = newCalc.useByDateIso
                                                )
                                                coroutineScope.launch {
                                                    currentScanRecord?.let { rec ->
                                                        productManager.addScanRecord(
                                                            rec.copy(
                                                                shelfLifeDays = openedDays,
                                                                useByDate = newCalc.useByDateIso
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                "🔓 Opened / In-Use (+14d)",
                                                fontSize = 11.sp,
                                                fontWeight = if (isPackageOpened) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }

                            // Quick Shelf Life Chips Selector
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Quick Shelf Life Preset:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                                        productManager.addScanRecord(
                                                            rec.copy(
                                                                shelfLifeDays = days,
                                                                useByDate = newCalc.useByDateIso
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                    AssistChip(
                                        onClick = { showCustomDaysDialog = true },
                                        label = {
                                            Text(
                                                "+Custom",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }

                            // Label Quantity Stepper
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Number of Labels:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 3.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable(enabled = labelQuantity > 1) {
                                                    if (labelQuantity > 1) labelQuantity--
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Remove,
                                                    contentDescription = "Decrease labels",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$labelQuantity",
                                            style = MonospaceQuantityStyle,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable {
                                                    labelQuantity++
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Increase labels",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Actions Row: Print Label & Save & Sync
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val activePrinter = connectedDevice ?: selectedPrinter
                                        if (activePrinter != null) {
                                            val zpl = if (settingsManager.department.equals(
                                                    "Environmental Services",
                                                    ignoreCase = true
                                                ) || settingsManager.department.equals(
                                                    "EVS",
                                                    ignoreCase = true
                                                )
                                            ) {
                                                ZplGenerator.generateShelfLabelZpl(
                                                    item = prod,
                                                    companyHeader = "EVS SHELF LABEL"
                                                )
                                            } else {
                                                ZplGenerator.generateLabel(
                                                    itemName = prod.name,
                                                    upc = prod.upc,
                                                    deliveryDate = dateCalc.deliveryDate,
                                                    useByDate = dateCalc.useByDate,
                                                    category = prod.category,
                                                    onHandAmount = state.onHandAmount,
                                                    staffInitials = settingsManager.staffInitials
                                                )
                                            }
                                            printerManager.printDirect(
                                                activePrinter,
                                                zpl,
                                                quantity = labelQuantity
                                            ) { _, msg ->
                                                statusMessage = msg
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Select or connect printer first!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.1f),
                                    contentPadding = PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 6.dp
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Print,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        "Print ($labelQuantity)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val activePrinter = connectedDevice ?: selectedPrinter
                                        if (activePrinter != null) {
                                            val zpl = ZplGenerator.generateUseFirstLabelZpl(
                                                itemName = prod.name,
                                                printDate = LocalDate.now(),
                                                staffInitials = settingsManager.staffInitials
                                            )
                                            printerManager.printDirect(
                                                activePrinter,
                                                zpl,
                                                quantity = labelQuantity
                                            ) { _, msg ->
                                                statusMessage = msg
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Select or connect printer first!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 6.dp
                                    )
                                ) {
                                    Text(
                                        "⚠️ Use First",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Button(
                                    onClick = {
                                        val current = currentScanRecord
                                        if (!recordHasUnsyncedChanges && current != null && current.id == lastSyncedRecordId && current.syncedToSheets) {
                                            Toast.makeText(
                                                context,
                                                "Scan record is already synced to Google Sheets!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            statusMessage = "Already synced to Google Sheets."
                                            return@Button
                                        }
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
                                                isAudit = settingsManager.isAuditMode,
                                                receivedBy = currentStaffAttribution
                                            )).copy(
                                                itemName = state.product.name,
                                                deliveryDate = state.dateCalculation.deliveryDateIso,
                                                useByDate = state.dateCalculation.useByDateIso,
                                                category = state.product.category,
                                                shelfLifeDays = state.product.shelfLifeDays,
                                                unit = state.product.unit,
                                                onHandAmount = state.onHandAmount,
                                                isAudit = settingsManager.isAuditMode,
                                                receivedBy = currentStaffAttribution
                                            )
                                            productManager.addScanRecord(recordToSave)
                                            currentScanRecord = recordToSave

                                            isSyncing = true
                                            statusMessage = "Syncing with Google Sheets..."
                                            productManager.logScanAsync(
                                                recordToSave,
                                                coroutineScope
                                            ) { res ->
                                                isSyncing = false
                                                res.onSuccess {
                                                    lastSyncedRecordId = recordToSave.id
                                                    recordHasUnsyncedChanges = false
                                                    statusMessage =
                                                        "Saved & Synced to Google Sheets!"
                                                    Toast.makeText(
                                                        context,
                                                        "Saved & Synced to Google Sheets!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }.onFailure { err ->
                                                    statusMessage =
                                                        "Saved locally. Sync error: ${err.localizedMessage}"
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.1f),
                                    contentPadding = PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 6.dp
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        "Save & Sync",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(0.8f),
                                    contentPadding = PaddingValues(
                                        horizontal = 6.dp,
                                        vertical = 6.dp
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

            } // End of Column
        } // End of Scaffold
    } // End of ModalNavigationDrawer

    // Manual Edit / Override Dialog
    if (showEditDialog && scannedState != null) {
        val currentState = scannedState!!
        var editName by remember { mutableStateOf(currentState.product.name) }
        var editNickname by remember { mutableStateOf(currentState.product.labelNickname) }
        var editCasePackStr by remember { mutableStateOf(currentState.product.casePackQty.toString()) }
        var editCategory by remember { mutableStateOf(currentState.product.category) }
        var editUnit by remember { mutableStateOf(currentState.product.unit) }
        val initialOnHand = if (currentState.onHandAmount > 0.0) currentState.onHandAmount else 1.0
        var editOnHandStr by remember {
            mutableStateOf(
                if (initialOnHand % 1.0 == 0.0) initialOnHand.toInt()
                    .toString() else initialOnHand.toString()
            )
        }
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
                    Text(
                        "Item Barcode: ${currentState.normalizedUpc}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    var nameDropdownExpanded by remember { mutableStateOf(false) }
                    val filteredNames = allCatalogItems.filter {
                        it.name.contains(
                            editName,
                            ignoreCase = true
                        ) && it.name.isNotBlank() && !it.name.startsWith("Item (")
                    }

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
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("Label Nickname / Short Name (Optional)") },
                        placeholder = { Text("e.g. Cut Green Beans #10") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editCasePackStr,
                        onValueChange = { editCasePackStr = it },
                        label = { Text("Case Pack Qty (Units/Case)") },
                        placeholder = { Text("e.g. 6 or 12") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                            settingsManager.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        editCategory = cat
                                        editDaysStr =
                                            DateCalculator.getShelfLifeDaysForCategory(cat)
                                                .toString()
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
                        Text(
                            "Quick Shelf Life Preset:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
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
                            Text(
                                "Preview Calculated Dates:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Delivery: ${calculatedPreview.deliveryDateFormatted}",
                                fontSize = 13.sp
                            )
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
                        val editOnHand =
                            if (parsedOnHand != null && parsedOnHand > 0.0) parsedOnHand else initialOnHand
                        val scannedUpc =
                            currentState.normalizedUpc.ifBlank { currentState.rawUpc.trim() }
                        val cleanName = editName.ifBlank { "Unrecognized Item" }

                        val editCasePackInt = editCasePackStr.toIntOrNull()?.coerceAtLeast(1) ?: 1

                        // Preserve exact scanned barcode & register alternate barcode mapping if linking to existing catalog item
                        val savedCatalogItem = productManager.linkBarcodeToCatalogItem(
                            scannedUpc = scannedUpc,
                            productName = cleanName,
                            category = editCategory,
                            unit = editUnit,
                            shelfLifeDays = validDays,
                            onHandAmount = editOnHand,
                            casePackQty = editCasePackInt,
                            labelNickname = editNickname.trim()
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
                            alternateBarcodes = savedCatalogItem.alternateBarcodes,
                            casePackQty = editCasePackInt,
                            labelNickname = editNickname.trim()
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
                            isAudit = settingsManager.isAuditMode,
                            receivedBy = currentStaffAttribution
                        )
                        productManager.addScanRecord(updatedScanRecord)
                        currentScanRecord = updatedScanRecord

                        showEditDialog = false
                        statusMessage = "Saved product & date override!"
                        Toast.makeText(
                            context,
                            "Saved product & date override!",
                            Toast.LENGTH_SHORT
                        ).show()

                        isSyncing = true
                        productManager.logScanAsync(updatedScanRecord, coroutineScope) { res ->
                            isSyncing = false
                            res.onSuccess {
                                lastSyncedRecordId = updatedScanRecord.id
                                recordHasUnsyncedChanges = false
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
                    Text(
                        "Set custom retention shelf life in days for ${currentState.product.name}:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
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
                            val validDays = DateCalculator.clampShelfLifeDays(
                                days,
                                currentState.product.category
                            )
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
                                    productManager.addScanRecord(
                                        rec.copy(
                                            shelfLifeDays = validDays,
                                            useByDate = newCalc.useByDateIso
                                        )
                                    )
                                }
                            }
                            showCustomDaysDialog = false
                            customDaysInput = ""
                            Toast.makeText(
                                context,
                                "Set custom shelf life to +${validDays}d!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Enter a valid positive number of days",
                                Toast.LENGTH_SHORT
                            ).show()
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

    if (showPrepStickerDialog) {
        AlertDialog(
            onDismissRequest = {
                showPrepStickerDialog = false
                prepItemNameInput = ""
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = { Text("Kitchen Prep & ServSafe Sticker", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Generate ServSafe compliant prep, thaw, or opened container thermal stickers for Zebra printers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = prepItemNameInput,
                        onValueChange = { prepItemNameInput = it },
                        label = { Text("Prepared Dish / Food Name") },
                        placeholder = { Text("e.g. Tuna Salad, Sliced Turkey, Marinara") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Food Dish Preset Chips
                    Text(
                        "Quick Dish Presets:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Tuna Salad",
                            "Sliced Turkey",
                            "Chicken Salad",
                            "Cooked Soup",
                            "Sliced Cheese",
                            "Cut Fruit",
                            "Portioned Dessert"
                        ).forEach { preset ->
                            FilterChip(
                                selected = prepItemNameInput.equals(preset, ignoreCase = true),
                                onClick = { prepItemNameInput = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Text(
                        "Prep Category & ServSafe Rule:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = prepCategoryInput == "PREPARED",
                            onClick = {
                                prepCategoryInput = "PREPARED"
                                prepRetentionDaysInput = 7
                            },
                            label = {
                                Text(
                                    "🥗 Prepared (+7d TCS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                        FilterChip(
                            selected = prepCategoryInput == "USE_FIRST",
                            onClick = {
                                prepCategoryInput = "USE_FIRST"
                                prepRetentionDaysInput = 0
                            },
                            label = {
                                Text(
                                    "⚠️ Use First",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                        FilterChip(
                            selected = prepCategoryInput == "THAW",
                            onClick = {
                                prepCategoryInput = "THAW"
                                prepRetentionDaysInput = 7
                            },
                            label = { Text("🧊 Pulled to Thaw (+7d)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = prepCategoryInput == "OPENED",
                            onClick = {
                                prepCategoryInput = "OPENED"
                                prepRetentionDaysInput = 7
                            },
                            label = { Text("🔓 Opened Container (+7d)", fontSize = 11.sp) }
                        )
                    }

                    // Retention Days Preset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Retention Days:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 3, 5, 7, 14).forEach { days ->
                                FilterChip(
                                    selected = prepRetentionDaysInput == days,
                                    onClick = { prepRetentionDaysInput = days },
                                    label = { Text("+${days}d", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    val calculatedDiscardDate = remember(prepRetentionDaysInput) {
                        LocalDate.now().plusDays(prepRetentionDaysInput.toLong()).format(
                            DateTimeFormatter.ofPattern("MM/dd/yy")
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Discard Date:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                calculatedDiscardDate,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Quantity selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sticker Quantity:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (prepLabelQuantity > 1) prepLabelQuantity-- },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                "$prepLabelQuantity",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { prepLabelQuantity++ },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activePrinter = connectedDevice ?: selectedPrinter
                        if (prepItemNameInput.isBlank()) {
                            Toast.makeText(
                                context,
                                "Please enter item / dish name",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (activePrinter == null) {
                            Toast.makeText(
                                context,
                                "Pair Zebra printer first in Settings",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }

                        val zpl = when (prepCategoryInput) {
                            "USE_FIRST" -> ZplGenerator.generateUseFirstLabelZpl(
                                itemName = prepItemNameInput,
                                printDate = LocalDate.now(),
                                staffInitials = settingsManager.staffInitials
                            )

                            "THAW" -> ZplGenerator.generateThawLabelZpl(
                                itemName = prepItemNameInput,
                                upc = "000000000000",
                                thawDate = LocalDate.now(),
                                staffInitials = settingsManager.staffInitials
                            )

                            "OPENED" -> ZplGenerator.generateOpenedContainerZpl(
                                itemName = prepItemNameInput,
                                upc = "000000000000",
                                openedDate = LocalDate.now(),
                                staffInitials = settingsManager.staffInitials
                            )

                            else -> ZplGenerator.generatePrepLabelZpl(
                                prepItemName = prepItemNameInput,
                                prepDate = LocalDate.now(),
                                prepType = "PREPARED FOOD",
                                retentionDays = prepRetentionDaysInput,
                                staffInitials = settingsManager.staffInitials,
                                companyHeader = "${settingsManager.companyCode} KITCHEN PREP"
                            )
                        }

                        printerManager.printDirect(
                            activePrinter,
                            zpl,
                            quantity = prepLabelQuantity
                        ) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                showPrepStickerDialog = false
                                prepItemNameInput = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print ($prepLabelQuantity)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrepStickerDialog = false
                        prepItemNameInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
