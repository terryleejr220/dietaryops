package com.dietaryops.manager.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.dietaryops.manager.ZplGenerator
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.AppPermission
import com.dietaryops.manager.ui.components.BadgeLoginDialog
import com.dietaryops.manager.ui.components.FloorNoteDialog
import com.dietaryops.manager.ui.screens.InventoryLogsScreen
import com.dietaryops.manager.ui.screens.KitchenDisplayScreen
import com.dietaryops.manager.ui.screens.LiveCanvasScreen
import com.dietaryops.manager.ui.screens.PrepStickersScreen
import com.dietaryops.manager.ui.screens.ReceivingScreen
import com.dietaryops.manager.ui.screens.SettingsScreen
import com.dietaryops.manager.ui.theme.PrimaryGradient

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    RECEIVING("Receiving", Icons.Default.QrCodeScanner),
    PREP_STICKERS("Prep Stickers", Icons.Default.RestaurantMenu),
    KITCHEN_DISPLAY("Kitchen Hub", Icons.Default.Restaurant),
    INVENTORY("Inventory Logs", Icons.Default.Inventory),
    CANVAS_SHEET("Live Canvas", Icons.Default.TableChart),
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
    var showFloorNoteDialog by remember { mutableStateOf(false) }
    var showDashboardDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentStaffUser = settingsManager.currentStaffUser

    fun isDestinationVisible(destination: NavDestination): Boolean {
        return when (destination) {
            NavDestination.RECEIVING -> currentStaffUser.hasPermission(AppPermission.PREP_LABELS) ||
                    currentStaffUser.hasPermission(AppPermission.RECEIVING_SCAN)

            NavDestination.PREP_STICKERS -> currentStaffUser.hasPermission(AppPermission.PREP_LABELS) ||
                    currentStaffUser.hasPermission(AppPermission.RECEIVING_SCAN)

            NavDestination.KITCHEN_DISPLAY -> currentStaffUser.hasPermission(AppPermission.PREP_LABELS) ||
                    currentStaffUser.hasPermission(AppPermission.RECEIVING_SCAN) ||
                    currentStaffUser.hasPermission(AppPermission.VIEW_INVENTORY)

            NavDestination.INVENTORY -> currentStaffUser.hasPermission(AppPermission.VIEW_INVENTORY)
            NavDestination.CANVAS_SHEET -> true
            NavDestination.SETTINGS -> currentStaffUser.hasPermission(AppPermission.PRINTER_SETTINGS) ||
                    currentStaffUser.hasPermission(AppPermission.BASIC_ADMIN_SETTINGS) ||
                    currentStaffUser.hasPermission(AppPermission.ADVANCED_SETTINGS) ||
                    currentStaffUser.hasPermission(AppPermission.BACKEND_SETTINGS)
        }
    }

    LaunchedEffect(currentStaffUser.role) {
        if (!isDestinationVisible(currentDestination)) {
            currentDestination = NavDestination.RECEIVING
        }
    }

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
                        leadingIcon = {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null
                            )
                        },
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
                        Toast.makeText(
                            context,
                            "Saved profile: ${settingsManager.staffName} (${settingsManager.staffInitials})",
                            Toast.LENGTH_SHORT
                        ).show()
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
                Toast.makeText(
                    context,
                    "Operator Active: ${user.displayName} \u2022 ${user.department}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showOperatorSheet) {
        AlertDialog(
            onDismissRequest = { showOperatorSheet = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shift Operator Session", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Facility: ${settingsManager.companyCode} (${settingsManager.companyName})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text("Department: ${settingsManager.department}", fontSize = 13.sp)
                    Text(
                        "Operator: ${settingsManager.staffName} (${settingsManager.employeeId})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Role: ${settingsManager.staffRole}", fontSize = 13.sp)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        "1-Tap Quick Switch Operator:",
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
                            selected = settingsManager.employeeId == "TL01",
                            onClick = {
                                settingsManager.employeeId = "TL01"
                                settingsManager.staffName = "Terry Little Jr."
                                settingsManager.staffInitials = "TL"
                                settingsManager.staffRole = "RECEIVER"
                                settingsManager.department = "Dietary"
                                Toast.makeText(
                                    context,
                                    "Switched operator to Terry Little Jr. (TL01 - Receiver)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("Receiver (TL01)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = settingsManager.employeeId == "AT01",
                            onClick = {
                                settingsManager.employeeId = "AT01"
                                settingsManager.staffName = "Andy Tygart"
                                settingsManager.staffInitials = "AT"
                                settingsManager.staffRole = "MANAGER"
                                settingsManager.department = "Dietary"
                                Toast.makeText(
                                    context,
                                    "Switched operator to Andy Tygart (AT01 - Manager)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("Manager (AT01)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = settingsManager.employeeId == "CK01",
                            onClick = {
                                settingsManager.employeeId = "CK01"
                                settingsManager.staffName = "Kitchen Cook"
                                settingsManager.staffInitials = "CK"
                                settingsManager.staffRole = "COOK"
                                settingsManager.department = "Dietary"
                                Toast.makeText(
                                    context,
                                    "Switched operator to Kitchen Cook (CK01 - Cook)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("Cook (CK01)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = settingsManager.employeeId == "AI01",
                            onClick = {
                                settingsManager.employeeId = "AI01"
                                settingsManager.staffName = "Dietary Aide"
                                settingsManager.staffInitials = "AI"
                                settingsManager.staffRole = "AIDE"
                                settingsManager.department = "Dietary"
                                Toast.makeText(
                                    context,
                                    "Switched operator to Dietary Aide (AI01 - Aide)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("Aide (AI01)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = settingsManager.employeeId == "LS01",
                            onClick = {
                                settingsManager.employeeId = "LS01"
                                settingsManager.staffName = "Lorraine S."
                                settingsManager.staffInitials = "LS"
                                settingsManager.staffRole = "MANAGER"
                                settingsManager.department = "EVS"
                                Toast.makeText(
                                    context,
                                    "Switched operator to Lorraine S. (LS01 - EVS Manager)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("EVS Manager (LS01)", fontSize = 11.sp) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showOperatorSheet = false
                                showProfileDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Profile", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val targetDevice = printerManager.getPreferredPrinter()
                                if (targetDevice != null) {
                                    val zpl = ZplGenerator.generateStaffBadgeZpl(
                                        displayName = settingsManager.staffName,
                                        employeeId = settingsManager.employeeId,
                                        role = settingsManager.staffRole,
                                        companyName = settingsManager.companyCode
                                    )
                                    printerManager.printDirect(targetDevice, zpl) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Pair Zebra printer first in Settings",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print Badge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Lanyard Badge")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showOperatorSheet = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showFloorNoteDialog) {
        FloorNoteDialog(
            settingsManager = settingsManager,
            onDismiss = { showFloorNoteDialog = false },
            onSubmitSuccess = { showFloorNoteDialog = false }
        )
    }

    if (showDashboardDialog) {
        AlertDialog(
            onDismissRequest = { showDashboardDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = { Text("Google Sheets Live Dashboards", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Tap to open your live inventory and kitchen analytics dashboards directly in Google Sheets:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            showDashboardDialog = false
                            settingsManager.openUrl(context, settingsManager.liveDashboardUrl)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Live Inventory Dashboard", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showDashboardDialog = false
                            settingsManager.openUrl(context, settingsManager.kitchenDashboardUrl)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Kitchen Operations Dashboard", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showDashboardDialog = false
                            settingsManager.openUrl(
                                context,
                                "https://docs.google.com/spreadsheets/d/${settingsManager.sheetId}"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full Google Sheet", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDashboardDialog = false }) {
                    Text("Close")
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
                                    text = if (settingsManager.staffInitials.isNotBlank()) settingsManager.staffInitials.take(
                                        2
                                    ).uppercase() else "DO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("DietaryOps", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                "Manager",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    NavDestination.entries.forEach { destination ->
                        if (!isDestinationVisible(destination)) return@forEach

                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.title
                                )
                            },
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
                                VerticalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.5f
                                    )
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    InventoryLogsScreen(
                                        repository = productManager.repository,
                                        printerManager = printerManager,
                                        settingsManager = settingsManager
                                    )
                                }
                            }
                        }

                        NavDestination.PREP_STICKERS -> {
                            PrepStickersScreen(
                                printerManager = printerManager,
                                settingsManager = settingsManager
                            )
                        }

                        NavDestination.KITCHEN_DISPLAY -> {
                            KitchenDisplayScreen(
                                printerManager = printerManager,
                                settingsManager = settingsManager
                            )
                        }

                        NavDestination.CANVAS_SHEET -> {
                            LiveCanvasScreen(
                                settingsManager = settingsManager
                            )
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
                contentWindowInsets = WindowInsets.safeDrawing,
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
                                        text = "${settingsManager.companyName} \u2022 ${settingsManager.department}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Right Action / Profile & Dashboard Indicators
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Dashboard Quick Access Button
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable { showDashboardDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Analytics,
                                            contentDescription = "Live Dashboards",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Floor Note / Idea Report Button
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable { showFloorNoteDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = "Report Floor Note",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Staff Avatar Pill (tap to edit staff name/initials)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    ),
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
                                if (!isDestinationVisible(destination)) return@forEach

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

                        NavDestination.PREP_STICKERS -> PrepStickersScreen(
                            printerManager = printerManager,
                            settingsManager = settingsManager
                        )

                        NavDestination.KITCHEN_DISPLAY -> KitchenDisplayScreen(
                            printerManager = printerManager,
                            settingsManager = settingsManager
                        )

                        NavDestination.INVENTORY -> InventoryLogsScreen(
                            repository = productManager.repository,
                            printerManager = printerManager,
                            settingsManager = settingsManager
                        )

                        NavDestination.CANVAS_SHEET -> LiveCanvasScreen(
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
