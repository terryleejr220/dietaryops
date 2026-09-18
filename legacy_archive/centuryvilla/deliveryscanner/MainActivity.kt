package com.centuryvilla.deliveryscanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centuryvilla.deliveryscanner.ui.MainViewModel
import com.centuryvilla.deliveryscanner.ui.components.ChangePinDialog
import com.centuryvilla.deliveryscanner.ui.components.EditProductDialog
import com.centuryvilla.deliveryscanner.ui.components.PinDialog
import com.centuryvilla.deliveryscanner.ui.components.WebhookDialog
import com.centuryvilla.deliveryscanner.ui.screens.KitchenInventoryScreen
import com.centuryvilla.deliveryscanner.ui.screens.LabelScannerScreen

class MainActivity : ComponentActivity() {

    private lateinit var productManager: ProductManager
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productManager = ProductManager(this)
        viewModel = MainViewModel(productManager)

        setContent {
            MainAppScreen(viewModel)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val scannedProduct by viewModel.scannedProduct.collectAsStateWithLifecycle()
    val detectedDeliveryDate by viewModel.detectedDeliveryDate.collectAsStateWithLifecycle()
    val detectedOpenDate by viewModel.detectedOpenDate.collectAsStateWithLifecycle()
    val isFromDeliveryLabel by viewModel.isFromDeliveryLabel.collectAsStateWithLifecycle()
    val labelCopies by viewModel.labelCopies.collectAsStateWithLifecycle()
    val unitCountInput by viewModel.unitCountInput.collectAsStateWithLifecycle()
    val autoPrint by viewModel.autoPrint.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val inventoryList by viewModel.inventoryList.collectAsStateWithLifecycle()
    val selectedPrinter by viewModel.selectedPrinter.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Camera & Permissions
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        val permsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissionLauncher.launch(permsToRequest.toTypedArray())
    }

    // Bluetooth Paired Printers
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    val pairedDevices = remember {
        mutableStateListOf<BluetoothDevice>().apply {
            bluetoothAdapter?.bondedDevices?.let { addAll(it) }
        }
    }

    LaunchedEffect(pairedDevices) {
        val printer = pairedDevices.firstOrNull { it.name?.contains("QLn", ignoreCase = true) == true || it.name?.contains("Zebra", ignoreCase = true) == true } ?: pairedDevices.firstOrNull()
        viewModel.setSelectedPrinter(printer)
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp, color = if (isAdminMode) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isAdminMode) "Century Villa [ADMIN]" else "Century Villa Dietary",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAdminMode) "Manager Controls Active" else "Staff Mode",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isAdminMode) {
                                FilledTonalButton(
                                    onClick = { viewModel.setAdminMode(false) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)
                                ) {
                                    Text("Lock", fontSize = 11.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showPinDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("PIN", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = if (isAdminMode) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isAdminMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { viewModel.setSelectedTab(0) },
                            text = { Text("Labels & Receiving", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.setSelectedTab(1) },
                            text = { Text("Kitchen Inventory", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab == 0) {
                LabelScannerScreen(
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission,
                    scannedProduct = scannedProduct,
                    detectedDeliveryDate = detectedDeliveryDate,
                    detectedOpenDate = detectedOpenDate,
                    isFromDeliveryLabel = isFromDeliveryLabel,
                    labelCopies = labelCopies,
                    unitCountInput = unitCountInput,
                    autoPrint = autoPrint,
                    statusMessage = statusMessage,
                    isAdminMode = isAdminMode,
                    selectedPrinter = selectedPrinter,
                    onChangePinClick = { showChangePinDialog = true },
                    onWebhookClick = { showWebhookDialog = true },
                    onEditProductClick = { showEditDialog = true }
                )
            } else {
                KitchenInventoryScreen(
                    viewModel = viewModel,
                    inventoryList = inventoryList,
                    statusMessage = statusMessage
                )
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onConfirmPin = { pin ->
                val ok = viewModel.verifyAdminPin(pin)
                if (ok) {
                    showPinDialog = false
                    Toast.makeText(context, "Welcome Admin", Toast.LENGTH_SHORT).show()
                }
                ok
            }
        )
    }

    if (showChangePinDialog && isAdminMode) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onSavePin = { newPin ->
                viewModel.changeAdminPin(newPin)
                showChangePinDialog = false
                Toast.makeText(context, "PIN updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showWebhookDialog && isAdminMode) {
        WebhookDialog(
            currentWebhook = viewModel.getWebhookUrl(),
            onDismiss = { showWebhookDialog = false },
            onSaveWebhook = { url ->
                viewModel.setWebhookUrl(url)
                showWebhookDialog = false
                Toast.makeText(context, "Webhook URL Saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showEditDialog && scannedProduct != null && isAdminMode) {
        EditProductDialog(
            scannedProduct = scannedProduct!!,
            onDismiss = { showEditDialog = false },
            onSaveProduct = { product ->
                viewModel.saveProduct(product)
                showEditDialog = false
                Toast.makeText(context, "Saved product rule!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
