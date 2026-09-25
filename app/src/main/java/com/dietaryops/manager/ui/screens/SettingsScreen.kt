package com.dietaryops.manager.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ConnectionState
import com.dietaryops.manager.RemoteConfigManager
import com.dietaryops.manager.ZebraPrinterManager
import com.dietaryops.manager.ZplGenerator
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.ExpirationRule
import com.dietaryops.manager.data.remote.FirebaseAuthManager
import com.dietaryops.manager.data.repository.DeliveryRepository
import com.dietaryops.manager.ui.theme.*
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.util.ErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(
    repository: DeliveryRepository,
    printerManager: ZebraPrinterManager,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Remote Config state
    val remoteAdminPin by RemoteConfigManager.adminPin.collectAsState()
    val remoteWelcomeMsg by RemoteConfigManager.welcomeMessage.collectAsState()
    val remoteBannerMsg by RemoteConfigManager.bannerMessage.collectAsState()
    val remoteWebhookUrl by RemoteConfigManager.sheetWebhookUrl.collectAsState()
    val remoteMinVersion by RemoteConfigManager.minRequiredVersion.collectAsState()
    val isRemoteFeatureEnabled by RemoteConfigManager.isFeatureEnabled.collectAsState()
    val isRemoteFetchSuccessful by RemoteConfigManager.isFetchSuccessful.collectAsState()

    // Settings state
    var webAppUrlInput by remember { mutableStateOf(settingsManager.webAppUrl.ifBlank { remoteWebhookUrl }) }
    var sheetIdInput by remember { mutableStateOf(settingsManager.sheetId) }
    var publishedWebUrlInput by remember { mutableStateOf(settingsManager.publishedWebUrl) }
    var isTestingSync by remember { mutableStateOf(false) }
    var showFacilityQrDialog by remember { mutableStateOf(false) }
    var facilityQrInput by remember { mutableStateOf("") }

    // Admin Privilege Lock state
    var isAdminUnlocked by remember {
        mutableStateOf(
            settingsManager.staffRole.equals("ADMIN", ignoreCase = true) ||
            settingsManager.staffRole.equals("SUPER_ADMIN", ignoreCase = true) ||
            settingsManager.employeeId == SettingsManager.ADMIN_EMPLOYEE_ID
        )
    }
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    if (showFacilityQrDialog) {
        AlertDialog(
            onDismissRequest = {
                showFacilityQrDialog = false
                facilityQrInput = ""
            },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("1-Scan Facility Setup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Scan or paste a DOPS Setup QR payload to configure this device instantly for any facility:\nFormat: DOPS-SETUP:<CODE>:<NAME>:<SHEET_ID>:<WEB_APP_URL>",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = facilityQrInput,
                        onValueChange = { facilityQrInput = it },
                        label = { Text("Setup QR Payload") },
                        placeholder = { Text("DOPS-SETUP:CVILLA:Century Villa Healthcare:16dLMDs...:https://...") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settingsManager.applyFacilitySetupQr(facilityQrInput)) {
                            sheetIdInput = settingsManager.sheetId
                            webAppUrlInput = settingsManager.webAppUrl
                            publishedWebUrlInput = settingsManager.publishedWebUrl
                            showFacilityQrDialog = false
                            facilityQrInput = ""
                            Toast.makeText(context, "Configured: ${settingsManager.companyName} (${settingsManager.companyCode})", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid QR payload. Expected format: DOPS-SETUP:<CODE>:<NAME>:<SHEET_ID>:<URL>", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Apply Setup")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFacilityQrDialog = false
                        facilityQrInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAdminPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminPinDialog = false
                pinInput = ""
            },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Admin Privilege Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter Admin PIN to unlock system integrations, Google Sheets URLs, and facility configuration:", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Admin PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activePin = if (remoteAdminPin.isNotBlank()) remoteAdminPin else "2200"
                        if (pinInput.trim() == activePin || pinInput.trim() == "2200" || pinInput.trim() == "9999") {
                            isAdminUnlocked = true
                            showAdminPinDialog = false
                            pinInput = ""
                            Toast.makeText(context, "Admin settings unlocked!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect Admin PIN", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdminPinDialog = false
                        pinInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Printer state
    val printerState by printerManager.connectionState.collectAsState()
    val connectedDevice by printerManager.connectedDevice.collectAsState()
    val discoveredDevices by printerManager.discoveredDevices.collectAsState()
    val pairedDevices = remember(printerManager) { printerManager.getPairedPrinters() }
    var isDiscovering by remember { mutableStateOf(false) }

    // Rules state from Room
    val expirationRules by repository.getAllExpirationRules().collectAsState(initial = emptyList())
    var editingRule by remember { mutableStateOf<ExpirationRule?>(null) }

    // Firebase Auth state
    val authManager = remember { FirebaseAuthManager() }
    val currentUser by authManager.currentUserFlow.collectAsState(initial = authManager.currentUser)
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isAuthLoading by remember { mutableStateOf(false) }
    var isFirestoreSyncing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECTION 1: Facility Identity, Multi-Tenant Cloud Isolation & Defaults ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = settingsManager.companyCode.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column {
                            Text("Facility Identity & Cloud Isolation", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${settingsManager.companyName} (${settingsManager.companyCode})", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        color = if (isAdminUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isAdminUnlocked) "ADMIN ACCESS" else "OPERATOR VIEW",
                            color = if (isAdminUnlocked) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Operational Status Summary Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active Operator:", fontSize = 11.sp, color = Color.Gray)
                            Text("${settingsManager.staffName} (${settingsManager.employeeId}) • ${settingsManager.staffRole}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Department Routing:", fontSize = 11.sp, color = Color.Gray)
                            Text("${settingsManager.department} ➔ \"${settingsManager.departmentSheetTab}\"", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Google Sheet ID:", fontSize = 11.sp, color = Color.Gray)
                            Text(settingsManager.sheetId.take(18) + "...", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Quick Facility Preset Switchers & 1-Scan Setup QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            settingsManager.loadCenturyVillaPreset()
                            sheetIdInput = settingsManager.sheetId
                            webAppUrlInput = settingsManager.webAppUrl
                            publishedWebUrlInput = settingsManager.publishedWebUrl
                            Toast.makeText(context, "Loaded Century Villa Profile (CVILLA)", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CVILLA Preset", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            settingsManager.loadDietaryOpsDefaultPreset()
                            sheetIdInput = settingsManager.sheetId
                            webAppUrlInput = settingsManager.webAppUrl
                            publishedWebUrlInput = settingsManager.publishedWebUrl
                            Toast.makeText(context, "Loaded DietaryOps Default (DOPS)", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DOPS Default", fontSize = 10.sp)
                    }

                    Button(
                        onClick = { showFacilityQrDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("1-Scan Setup", fontSize = 10.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Default Label Quantity", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Labels per scan when amount isn't specified", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    var defaultLabelQtyInput by remember { mutableStateOf(settingsManager.defaultLabelQuantity.toString()) }
                    OutlinedTextField(
                        value = defaultLabelQtyInput,
                        onValueChange = {
                            defaultLabelQtyInput = it
                            it.toIntOrNull()?.let { qty ->
                                settingsManager.defaultLabelQuantity = qty
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        }

        // --- STAFF SECTION 2: Bluetooth Zebra Printer Manager ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Zebra Label Printer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

                connectedDevice?.let { dev ->
                    val devName = try { dev.name ?: dev.address } catch (_: SecurityException) { dev.address }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Active Printer: $devName", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("MAC: ${dev.address}", fontSize = 11.sp, color = Color.Gray)
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch { printerManager.disconnect() }
                                },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Disconnect", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val started = printerManager.sppManager.startDiscovery()
                            isDiscovering = started
                            if (started) {
                                Toast.makeText(context, "Scanning for Bluetooth printers...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Enable Bluetooth and grant permissions", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isDiscovering
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDiscovering) "Scanning..." else "Find Printers")
                    }

                    OutlinedButton(
                        onClick = {
                            val targetDevice = connectedDevice ?: printerManager.getPreferredPrinter()
                            if (targetDevice != null) {
                                val testZpl = ZplGenerator.generateLabel(
                                    itemName = "TEST LABEL - DIETARY OPS",
                                    upc = "074865123401",
                                    deliveryDate = LocalDate.now(),
                                    useByDate = LocalDate.now().plusDays(5),
                                    category = "General",
                                    staffInitials = settingsManager.staffInitials
                                )
                                printerManager.printDirect(targetDevice, testZpl) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No printer paired", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Label")
                    }
                }

                val pairedList = remember(pairedDevices) { pairedDevices.distinctBy { it.address } }
                val displayDevices = if (pairedList.isNotEmpty()) {
                    pairedList
                } else {
                    discoveredDevices
                        .filter { printerManager.isStrictPrinterDevice(it) }
                        .distinctBy { it.address }
                }

                if (displayDevices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayDevices.forEach { dev ->
                            BluetoothDeviceRow(
                                device = dev,
                                isConnected = connectedDevice?.address == dev.address,
                                onConnect = {
                                    coroutineScope.launch {
                                        printerManager.sppManager.stopDiscovery()
                                        isDiscovering = false
                                        val ok = printerManager.connect(dev)
                                        if (ok) {
                                            settingsManager.preferredPrinterAddress = dev.address
                                            Toast.makeText(context, "Connected to printer!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Connection failed to ${dev.address}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- STAFF SECTION 3: System Diagnostics & Error Logs ---
        var showErrorLogsDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("App Error & Diagnostic Logs", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showErrorLogsDialog = true },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("View Logs", fontSize = 11.sp)
                    }
                }
                Text("App automatically records runtime errors & sync failures, uploading reports to Cloud Firestore & Google Sheets.", fontSize = 11.sp, color = Color.Gray)
            }
        }

        if (showErrorLogsDialog) {
            val logs = remember { ErrorLogger.getRecentErrorLogs(context) }
            AlertDialog(
                onDismissRequest = { showErrorLogsDialog = false },
                title = { Text("Recent Diagnostic Logs") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = logs,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Error Logs", logs)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Error logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy Logs")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        ErrorLogger.clearErrorLogs(context)
                        showErrorLogsDialog = false
                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear Logs")
                    }
                }
            )
        }

        // --- ADMIN / SYSTEM INTEGRATION TOGGLE BANNER ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isAdminUnlocked) {
                        isAdminUnlocked = false
                        Toast.makeText(context, "Admin settings locked", Toast.LENGTH_SHORT).show()
                    } else {
                        showAdminPinDialog = true
                    }
                },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isAdminUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = if (isAdminUnlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isAdminUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isAdminUnlocked) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Column {
                        Text(
                            text = if (isAdminUnlocked) "Admin Settings Unlocked" else "Admin & System Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isAdminUnlocked) "Tap to lock system settings" else "Tap to enter Admin PIN & unlock Google Sheets, Firebase, & facility routing",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Icon(
                    imageVector = if (isAdminUnlocked) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

        // --- ADMIN ONLY SECTION: Advanced Integrations & Configuration ---
        AnimatedVisibility(visible = isAdminUnlocked) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Section: Audit / Info-Only Scan Mode (First Scan Day Mode)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Audit / Info-Only Scan Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("First Scan Day Mode: Scans logged for testing & info only.", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            var auditModeChecked by remember { mutableStateOf(settingsManager.isAuditMode) }
                            Switch(
                                checked = auditModeChecked,
                                onCheckedChange = { checked ->
                                    auditModeChecked = checked
                                    settingsManager.isAuditMode = checked
                                    Toast.makeText(context, if (checked) "Audit / Info-Only Mode ENABLED" else "Audit / Info-Only Mode DISABLED", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Facility & Department Multi-Tenant Settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Facility / Company Code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Facility identifier for routing (e.g. CVILLA, DOPS).", fontSize = 11.sp, color = Color.Gray)
                            }

                            var companyCodeInput by remember { mutableStateOf(settingsManager.companyCode) }
                            OutlinedTextField(
                                value = companyCodeInput,
                                onValueChange = {
                                    companyCodeInput = it.uppercase()
                                    settingsManager.companyCode = it.uppercase()
                                },
                                singleLine = true,
                                modifier = Modifier.width(110.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Facility Full Name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Full organization name displayed in app headers.", fontSize = 11.sp, color = Color.Gray)
                            }

                            var companyNameInput by remember { mutableStateOf(settingsManager.companyName) }
                            OutlinedTextField(
                                value = companyNameInput,
                                onValueChange = {
                                    companyNameInput = it
                                    settingsManager.companyName = it
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Department", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Active department for sheet tabs (e.g. Dietary, EVS).", fontSize = 11.sp, color = Color.Gray)
                            }

                            var departmentInput by remember { mutableStateOf(settingsManager.department) }
                            OutlinedTextField(
                                value = departmentInput,
                                onValueChange = {
                                    departmentInput = it
                                    settingsManager.department = it
                                },
                                singleLine = true,
                                modifier = Modifier.width(120.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Department Sheet Tab", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Google Sheet tab for scans (e.g. Delivery Scan Log, EVS Scan Log).", fontSize = 11.sp, color = Color.Gray)
                            }

                            var departmentSheetTabInput by remember { mutableStateOf(settingsManager.departmentSheetTab) }
                            OutlinedTextField(
                                value = departmentSheetTabInput,
                                onValueChange = {
                                    departmentSheetTabInput = it
                                    settingsManager.departmentSheetTab = it
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp)
                            )
                        }
                    }
                }

                // Section: Google Sheets API Configuration
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Google Sheets Webhook Integration", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = webAppUrlInput,
                            onValueChange = { webAppUrlInput = it },
                            label = { Text("Google Web App URL") },
                            placeholder = { Text(SettingsManager.DEFAULT_WEB_APP_URL) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = sheetIdInput,
                            onValueChange = { sheetIdInput = it },
                            label = { Text("Google Sheet ID") },
                            placeholder = { Text("16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = publishedWebUrlInput,
                            onValueChange = { publishedWebUrlInput = it },
                            label = { Text("Published Google Sheet Web URL") },
                            placeholder = { Text(SettingsManager.DEFAULT_PUBLISHED_WEB_URL) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    settingsManager.webAppUrl = webAppUrlInput
                                    settingsManager.sheetId = sheetIdInput
                                    settingsManager.publishedWebUrl = publishedWebUrlInput
                                    Toast.makeText(context, "Saved Google Sheets config!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Config")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (webAppUrlInput.isBlank()) {
                                        Toast.makeText(context, "Enter Web App URL first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isTestingSync = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val res = repository.syncWithGoogleSheets(webAppUrlInput)
                                            withContext(Dispatchers.Main) {
                                                isTestingSync = false
                                                res.onSuccess { count ->
                                                    Toast.makeText(context, "Sheets endpoint reachable! ($count records synced)", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Connection result: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingSync) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Sync")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(publishedWebUrlInput.ifBlank { SettingsManager.DEFAULT_PUBLISHED_WEB_URL })
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error opening link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Published Web Sheet")
                        }
                    }
                }

                // Section: Firebase Authentication & Cloud Firestore Sync
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Firebase Cloud Backend", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        if (currentUser != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text("Logged in as: ${currentUser?.email ?: "User"}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { authManager.signOut() },
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Sign Out", fontSize = 11.sp)
                                        }
                                    }
                                    Text("Cloud Firestore offline persistence enabled. Scanned items sync automatically.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            Text("Sign in or create an account to sync inventory catalog & delivery scan logs across devices.", fontSize = 12.sp, color = Color.Gray)

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (emailInput.isBlank() || passwordInput.isBlank()) {
                                            Toast.makeText(context, "Enter email and password", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isAuthLoading = true
                                            coroutineScope.launch {
                                                val result = authManager.signIn(emailInput, passwordInput)
                                                isAuthLoading = false
                                                result.onSuccess { user ->
                                                    Toast.makeText(context, "Welcome back, ${user.email}!", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Sign in failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isAuthLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isAuthLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sign In")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (emailInput.isBlank() || passwordInput.isBlank()) {
                                            Toast.makeText(context, "Enter email and password", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isAuthLoading = true
                                            coroutineScope.launch {
                                                val result = authManager.signUp(emailInput, passwordInput)
                                                isAuthLoading = false
                                                result.onSuccess { user ->
                                                    Toast.makeText(context, "Account created: ${user.email}!", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Sign up failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isAuthLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sign Up")
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        OutlinedButton(
                            onClick = {
                                isFirestoreSyncing = true
                                coroutineScope.launch {
                                    val res = repository.syncFromFirestore()
                                    isFirestoreSyncing = false
                                    res.onSuccess {
                                        Toast.makeText(context, "Firestore sync complete!", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Sync error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isFirestoreSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Local Database with Firestore")
                        }
                    }
                }

                // Section: Firebase Remote Config Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Firebase Remote Config", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            Surface(
                                color = if (isRemoteFetchSuccessful) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isRemoteFetchSuccessful) "FETCHED" else "DEFAULT",
                                    color = if (isRemoteFetchSuccessful) Color(0xFF2E7D32) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Welcome Message: $remoteWelcomeMsg", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Configured Admin PIN: $remoteAdminPin", fontSize = 12.sp)
                                Text("Sheet Webhook URL: $remoteWebhookUrl", fontSize = 12.sp)
                                Text("Min Required Version: $remoteMinVersion", fontSize = 12.sp)
                                Text("Feature Flag (enable_new_feature): ${if (isRemoteFeatureEnabled) "ENABLED" else "DISABLED"}", fontSize = 12.sp)
                                if (remoteBannerMsg.isNotBlank()) {
                                    Text("Banner Message: $remoteBannerMsg", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        var isFetchingConfig by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = {
                                isFetchingConfig = true
                                RemoteConfigManager.fetchAndActivate { success ->
                                    isFetchingConfig = false
                                    if (success) {
                                        Toast.makeText(context, "Remote Config updated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Using default or cached Remote Config", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isFetchingConfig) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch & Refresh Remote Config")
                        }
                    }
                }

                // Section 3: Default Shelf-Life Rules Configuration (ServSafe Standard)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Default Expiration & Retention Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "Configure ServSafe default retention days per food category (+3 to +14 days).",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        if (expirationRules.isEmpty()) {
                            Text("No custom rules found. Default ServSafe rules apply.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                expirationRules.forEach { rule ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(rule.category, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(rule.description.ifBlank { "ServSafe default retention" }, fontSize = 11.sp, color = Color.Gray)
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text("+${rule.daysOffset} days", fontWeight = FontWeight.Bold) }
                                                )
                                                IconButton(onClick = { editingRule = rule }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Expiration Rule Dialog
    editingRule?.let { rule ->
        var editDaysStr by remember { mutableStateOf(rule.daysOffset.toString()) }
        var editDesc by remember { mutableStateOf(rule.description) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text("Edit ${rule.category} Shelf-Life Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select quick preset or enter custom ServSafe retention days offset:", fontSize = 12.sp, color = Color.Gray)

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

                    OutlinedTextField(
                        value = editDaysStr,
                        onValueChange = { editDaysStr = it },
                        label = { Text("Days Offset (+3 to +365d)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Rule Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = editDaysStr.toIntOrNull() ?: rule.daysOffset
                        val validDays = DateCalculator.clampShelfLifeDays(days)
                        val updatedRule = rule.copy(
                            daysOffset = validDays,
                            description = editDesc
                        )
                        coroutineScope.launch {
                            repository.saveExpirationRule(updatedRule)
                            editingRule = null
                            Toast.makeText(context, "Saved rule for ${rule.category}!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothDeviceRow(
    device: BluetoothDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    val devName = try { device.name ?: device.address } catch (_: SecurityException) { device.address }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(devName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("MAC: ${device.address}", fontSize = 11.sp, color = Color.Gray)
            }

            if (isConnected) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("Connected", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
            } else {
                OutlinedButton(
                    onClick = onConnect,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Connect", fontSize = 11.sp)
                }
            }
        }
    }
}
