package com.centuryvilla.deliveryscanner.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Size
import android.view.MotionEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.centuryvilla.deliveryscanner.BarcodeAnalyzer
import com.centuryvilla.deliveryscanner.data.model.SyscoProduct
import com.centuryvilla.deliveryscanner.ui.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
@Composable
fun LabelScannerScreen(
    viewModel: MainViewModel,
    hasCameraPermission: Boolean,
    scannedProduct: SyscoProduct?,
    detectedDeliveryDate: LocalDate,
    detectedOpenDate: LocalDate,
    isFromDeliveryLabel: Boolean,
    labelCopies: Int,
    unitCountInput: String,
    autoPrint: Boolean,
    statusMessage: String,
    isAdminMode: Boolean,
    selectedPrinter: BluetoothDevice?,
    onChangePinClick: () -> Unit,
    onWebhookClick: () -> Unit,
    onEditProductClick: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickAddUpcInput by remember { mutableStateOf("") }

    val searchSuggestions = remember(searchQuery) {
        if (searchQuery.isNotBlank()) viewModel.searchProducts(searchQuery) else emptyList()
    }

    val barcodeAnalyzer = remember {
        BarcodeAnalyzer { rawBarcode ->
            viewModel.handleScannedBarcode(rawBarcode)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isAdminMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Manager Tools", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.printAdminBadge() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Print Badge", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onChangePinClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Change PIN", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onWebhookClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sheet Sync", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Printer Settings
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Printer: ${selectedPrinter?.name ?: "No Zebra Paired"}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-print upon scan:", fontSize = 13.sp)
                    Switch(checked = autoPrint, onCheckedChange = { viewModel.setAutoPrint(it) })
                }
            }
        }

        // Manual Search & Quick Add Bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        showSearchResults = it.isNotBlank()
                    },
                    label = { Text("Search UPC, Item #, or Name", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Button(
                    onClick = {
                        quickAddUpcInput = searchQuery.trim()
                        showQuickAddDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("+ Quick Add", fontSize = 11.sp)
                }
            }

            if (showSearchResults && searchSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchSuggestions.take(4).forEach { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectProduct(product)
                                        showSearchResults = false
                                        searchQuery = ""
                                    }
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("UPC: ${product.upc} ${if (product.itemNumber.isNotBlank()) "• Item #${product.itemNumber}" else ""}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(product.getFormattedPackInfo(), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Camera View
        if (hasCameraPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor(), barcodeAnalyzer)
                                }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Text("Camera permission is required to scan barcodes.", color = MaterialTheme.colorScheme.error)
        }

        Text(
            text = statusMessage,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (statusMessage.contains("Unlocked", ignoreCase = true) || statusMessage.contains("success", ignoreCase = true) || statusMessage.contains("tag", ignoreCase = true)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
        )

        // Scanned Product Details Card
        scannedProduct?.let { product ->
            val fmt = DateTimeFormatter.ofPattern("MM/dd/yy")
            val unopenedUseBy = detectedDeliveryDate.plusDays(product.shelfLifeDays.toLong())
            val openedUseBy = detectedOpenDate.plusDays(product.openedShelfLifeDays.toLong())
            val effectiveOpenedUseBy = if (openedUseBy.isBefore(unopenedUseBy)) openedUseBy else unopenedUseBy

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(product.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isFromDeliveryLabel) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("Unit Tag", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Text("UPC: ${product.upc} • Pack: ${product.getFormattedPackInfo()}", fontSize = 12.sp, color = Color.Gray)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivered: ${detectedDeliveryDate.format(fmt)}", fontSize = 13.sp)
                        Text("Unopened Exp: ${unopenedUseBy.format(fmt)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Opened: ${detectedOpenDate.format(fmt)}", fontSize = 13.sp)
                        Text("Opened Use By: ${effectiveOpenedUseBy.format(fmt)} (+${product.openedShelfLifeDays}d)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // UNIT COUNT & LABEL COPIES
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = unitCountInput,
                            onValueChange = { viewModel.setUnitCountInput(it) },
                            label = { Text("Unit Count (Recv)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1.3f).height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Labels: $labelCopies", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    OutlinedButton(
                                        onClick = { if (labelCopies > 1) viewModel.setLabelCopies(labelCopies - 1) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("-", fontSize = 14.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.setLabelCopies(labelCopies + 1) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("+", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Print action buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.printOpenedLabel() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (labelCopies > 1) "Print $labelCopies Opened" else "Print Opened", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { viewModel.printDeliveryLabel() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (labelCopies > 1) "Print $labelCopies Units" else "Print Delivery", fontSize = 12.sp)
                        }
                    }

                    if (isAdminMode) {
                        OutlinedButton(
                            onClick = onEditProductClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Configure Product Rules (Admin)")
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                barcodeAnalyzer.isScanningEnabled = true
                viewModel.setStatusMessage("Ready to scan Sysco UPC, Delivery Tag, or Admin Badge...")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Next Item")
        }
    }

    // Quick Add Product Dialog (Auto-Dismissing on Save)
    if (showQuickAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newUpc by remember { mutableStateOf(quickAddUpcInput) }
        var newShelfLife by remember { mutableStateOf("7") }
        var newOpenedDays by remember { mutableStateOf("7") }
        var newPackSize by remember { mutableStateOf("1") }

        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Quick Add Product") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUpc,
                        onValueChange = { newUpc = it },
                        label = { Text("UPC / Item #") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Product Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Shelf Life Presets (Default: 3 to 14 days):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = newShelfLife == "5",
                            onClick = { newShelfLife = "5"; newOpenedDays = "3" },
                            label = { Text("Meat/Produce (3-5d)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = newShelfLife == "10",
                            onClick = { newShelfLife = "10"; newOpenedDays = "7" },
                            label = { Text("Dairy/Eggs (5-14d)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = newShelfLife == "365",
                            onClick = { newShelfLife = "365"; newOpenedDays = "5" },
                            label = { Text("Canned/Dry (365d)", fontSize = 10.sp) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newShelfLife,
                            onValueChange = { newShelfLife = it },
                            label = { Text("Unopened Days") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newOpenedDays,
                            onValueChange = { newOpenedDays = it },
                            label = { Text("Opened Days") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = newPackSize,
                        onValueChange = { newPackSize = it },
                        label = { Text("Pack Size (Units/Case)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val dDays = newShelfLife.toIntOrNull() ?: 7
                    val oDays = newOpenedDays.toIntOrNull() ?: 7
                    val pSize = newPackSize.toIntOrNull() ?: 1
                    val prod = SyscoProduct(
                        upc = newUpc.ifBlank { "SYSCO-" + System.currentTimeMillis().toString().takeLast(6) },
                        name = newName.ifBlank { "Sysco Item ($newUpc)" },
                        shelfLifeDays = dDays,
                        openedShelfLifeDays = oDays,
                        packSize = pSize
                    )
                    viewModel.saveQuickAddProduct(prod)
                    showQuickAddDialog = false // AUTO-DISMISS DIALOG
                    searchQuery = ""
                    showSearchResults = false
                }) {
                    Text("Save & Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
