package com.dietaryops.manager.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ConnectionState
import com.dietaryops.manager.ProductManager
import com.dietaryops.manager.ZebraPrinterManager
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.ui.components.BadgeLoginDialog
import com.dietaryops.manager.ui.screens.InventoryLogsScreen
import com.dietaryops.manager.ui.screens.ReceivingScreen
import com.dietaryops.manager.ui.screens.SettingsScreen
import com.dietaryops.manager.ui.theme.PrimaryGradient

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    RECEIVING("Receiving", Icons.Default.QrCodeScanner),
    INVENTORY("Inventory Logs", Icons.Default.Inventory),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveMainLayout(
    productManager: ProductManager,
    printerManager: ZebraPrinterManager,
    settingsManager: SettingsManager
) {
    var currentDestination by remember { mutableStateOf(NavDestination.RECEIVING) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showBadgeLoginDialog by remember { mutableStateOf(false) }
    var showOperatorSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val printerState by printerManager.connectionState.collectAsState()
    val connectedDevice by printerManager.connectedDevice.collectAsState()

    // Staff profile dialog
    if (showProfileDialog) {
        var profileName by remember { mutableStateOf(settingsManager.staffName) }
        var profileInitials by remember { mutableStateOf(settingsManager.staffInitials) }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Badge,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text("Staff Profile & Label Signature", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter staff name and initials. These populate the 'Staff:' and 'BY:' signatures on Zebra ZPL labels and Google Sheets audit logs.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Staff Full Name") },
                        placeholder = { Text("e.g. Terry Lee") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileInitials,
                        onValueChange = { profileInitials = it },
                        label = { Text("Staff Initials") },
                        placeholder = { Text("e.g. TR or DO") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsManager.staffName = profileName
                        settingsManager.staffInitials = profileInitials
                        showProfileDialog = false
                        Toast.makeText(context, "Saved profile: ${settingsManager.staffName} (${settingsManager.staffInitials})", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Profile", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBadgeLoginDialog) {
        BadgeLoginDialog(
            settingsManager = settingsManager,
            onDismiss = { showBadgeLoginDialog = false },
            onLoginSuccess = { user ->
                showBadgeLoginDialog = false
                Toast.makeText(context, "Operator Active: ${user.displayName} • ${user.department}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showOperatorSheet) {
        AlertDialog(
            onDismissRequest = { showOperatorSheet = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shift Operator Session", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Facility: ${settingsManager.companyCode} (${settingsManager.companyName})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Department: ${settingsManager.department}", fontSize = 13.sp)
                    Text("Operator: ${settingsManager.staffName} (${settingsManager.employeeId})", fontSize = 13.sp)
                    Text("Role: ${settingsManager.staffRole}", fontSize = 13.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Staff identity is stored locally and synchronized with the company backend. No email or phone is required.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOperatorSheet = false
                        showBadgeLoginDialog = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Badge / Switch")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showOperatorSheet = false
                        showProfileDialog = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Manual Signature")
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Tablet / Large Screen Multi-Pane Adaptive Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail on left with elevated styling
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Column(
                            modifier = Modifier.padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Branded App Squircle
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryGradient)
                                    .clickable { showOperatorSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (settingsManager.staffInitials.isNotBlank()) settingsManager.staffInitials.take(2).uppercase() else "DO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("DietaryOps", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Manager", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                ) {
                    NavDestination.entries.forEach { destination ->
                        if (destination == NavDestination.SETTINGS && settingsManager.staffRole == "OPERATOR") return@forEach
                        
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title, fontSize = 11.sp) }
                        )
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Multi-Pane Content Area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentDestination) {
                        NavDestination.RECEIVING, NavDestination.INVENTORY -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    ReceivingScreen(
                                        productManager = productManager,
                                        printerManager = printerManager,
                                        settingsManager = settingsManager
                                    )
                                }
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    InventoryLogsScreen(
                                        repository = productManager.repository,
                                        printerManager = printerManager,
                                        settingsManager = settingsManager
                                    )
                                }
                            }
                        }
                        NavDestination.SETTINGS -> {
                            SettingsScreen(
                                repository = productManager.repository,
                                printerManager = printerManager,
                                settingsManager = settingsManager
                            )
                        }
                    }
                }
            }
        } else {
            // Standard Phone Single-Column Layout with High-Tech Header & Bottom Bar
            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Branded Left Title Area
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PrimaryGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "DietaryOps Manager",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${settingsManager.companyName} • ${settingsManager.department}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Right Action / Profile & Printer Indicators
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Live Compact Printer Pill
                                val (statusDotColor, statusLabel) = when (printerState) {
                                    ConnectionState.CONNECTED -> Pair(Color(0xFF10B981), connectedDevice?.let { try { it.name } catch (_: SecurityException) { null } } ?: "Zebra")
                                    ConnectionState.CONNECTING -> Pair(Color(0xFFF59E0B), "Pairing...")
                                    ConnectionState.ERROR -> Pair(MaterialTheme.colorScheme.error, "Err")
                                    ConnectionState.DISCONNECTED -> Pair(Color(0xFF64748B), "Offline")
                                }

                                Surface(
                                    color = statusDotColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, statusDotColor.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(statusDotColor)
                                        )
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = null,
                                            tint = statusDotColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = statusLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusDotColor
                                        )
                                    }
                                }

                                // Staff Avatar Pill (tap to edit staff name/initials)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable { showOperatorSheet = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val initials = settingsManager.staffInitials.ifBlank {
                                            settingsManager.staffName.take(2)
                                        }.ifBlank { "TR" }.take(2).uppercase()

                                        Text(
                                            text = initials,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            NavDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentDestination == destination,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = destination.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            destination.title,
                                            fontWeight = if (currentDestination == destination) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (currentDestination) {
                        NavDestination.RECEIVING -> ReceivingScreen(
                            productManager = productManager,
                            printerManager = printerManager,
                            settingsManager = settingsManager
                        )
                        NavDestination.INVENTORY -> InventoryLogsScreen(
                            repository = productManager.repository,
                            printerManager = printerManager,
                            settingsManager = settingsManager
                        )
                        NavDestination.SETTINGS -> SettingsScreen(
                            repository = productManager.repository,
                            printerManager = printerManager,
                            settingsManager = settingsManager
                        )
                    }
                }
            }
        }
    }
}
