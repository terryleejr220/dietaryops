package com.dietaryops.manager.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ProductManager
import com.dietaryops.manager.ZebraPrinterManager
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.ui.screens.InventoryLogsScreen
import com.dietaryops.manager.ui.screens.ReceivingScreen
import com.dietaryops.manager.ui.screens.SettingsScreen

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

    val context = LocalContext.current

    if (showProfileDialog) {
        var profileName by remember { mutableStateOf(settingsManager.staffName) }
        var profileInitials by remember { mutableStateOf(settingsManager.staffInitials) }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Staff User Profile Setup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter staff name and initials. These will populate 'Staff:' / 'BY:' fields on ZPL labels and delivery scan logs.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Staff Name (e.g. Terry)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileInitials,
                        onValueChange = { profileInitials = it },
                        label = { Text("Staff Initials (e.g. TR or DO)") },
                        singleLine = true,
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
                        Toast.makeText(context, "Saved user profile for ${settingsManager.staffName} (${settingsManager.staffInitials})", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Tablet / Large Screen Multi-Pane Adaptive Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail on left
                NavigationRail(
                    header = {
                        Column(
                            modifier = Modifier.padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(onClick = { showProfileDialog = true }) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "User Profile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Dietary", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Ops Manager", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                ) {
                    NavDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title, fontSize = 11.sp) }
                        )
                    }
                }

                VerticalDivider()

                // Multi-Pane Content Area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentDestination) {
                        NavDestination.RECEIVING, NavDestination.INVENTORY -> {
                            // Side-by-side Multi-Pane Receiving & Logs
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    ReceivingScreen(
                                        productManager = productManager,
                                        printerManager = printerManager,
                                        settingsManager = settingsManager
                                    )
                                }

                                VerticalDivider()

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
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
            // Standard Phone Single-Column Layout
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Dietary Ops Manager",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dietary Receiving & Expiration Tracker",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showProfileDialog = true }) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "User Profile Setup",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination == destination,
                                onClick = { currentDestination = destination },
                                icon = { Icon(destination.icon, contentDescription = destination.title) },
                                label = { Text(destination.title) }
                            )
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
