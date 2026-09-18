package com.dietaryops.manager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ConnectionState
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.ui.theme.StatusErrorColor
import com.dietaryops.manager.ui.theme.SyncPendingColor
import com.dietaryops.manager.ui.theme.SyncSuccessColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDrawerContent(
    settingsManager: SettingsManager,
    printerState: ConnectionState,
    connectedDevice: android.bluetooth.BluetoothDevice?,
    pairedPrinters: List<android.bluetooth.BluetoothDevice>,
    selectedPrinter: android.bluetooth.BluetoothDevice?,
    onPrinterSelected: (android.bluetooth.BluetoothDevice) -> Unit,
    autoPrint: Boolean,
    onAutoPrintChanged: (Boolean) -> Unit,
    manualUpcInput: String,
    onManualUpcInputChanged: (String) -> Unit,
    onManualUpcSubmit: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Admin & Diagnostics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider()

            // Printer selector
            Text("Printer Connection", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            var showDropdown by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showDropdown = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayName = (connectedDevice ?: selectedPrinter)?.let {
                            try { it.name ?: it.address } catch (_: SecurityException) { it.address }
                        } ?: "No Printer Selected"
                        Text(displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    pairedPrinters.forEach { device ->
                        val devName = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                        DropdownMenuItem(
                            text = { Text(devName) },
                            onClick = {
                                onPrinterSelected(device)
                                showDropdown = false
                            }
                        )
                    }
                }
            }

            // Printer State
            val (stateColor, stateText) = when (printerState) {
                ConnectionState.CONNECTED -> Pair(SyncSuccessColor, "CONNECTED")
                ConnectionState.CONNECTING -> Pair(SyncPendingColor, "PAIRING...")
                ConnectionState.ERROR -> Pair(StatusErrorColor, "ERROR")
                ConnectionState.DISCONNECTED -> Pair(Color(0xFF64748B), "OFFLINE")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(stateColor))
                Text(stateText, color = stateColor, fontWeight = FontWeight.Bold)
            }

            // Auto-print
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-print upon scan:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Switch(checked = autoPrint, onCheckedChange = onAutoPrintChanged)
            }
            
            Divider()
            
            // Manual UPC
            Text("Manual UPC Entry", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = manualUpcInput,
                onValueChange = onManualUpcInputChanged,
                label = { Text("12-digit UPC") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onManualUpcSubmit(manualUpcInput) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Lookup Item")
            }
        }
    }
}
