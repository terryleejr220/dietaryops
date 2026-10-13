package com.dietaryops.manager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ZebraPrinterManager
import com.dietaryops.manager.ZplGenerator
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.*
import com.dietaryops.manager.ui.components.HaccpTempDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenDisplayScreen(
    printerManager: ZebraPrinterManager,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showHaccpDialog by remember { mutableStateOf(false) }

    val sampleServices = remember { MealService.defaultSampleServices() }
    val currentPeriod = remember { MealPeriod.current() }
    var selectedMealPeriod by remember { mutableStateOf(currentPeriod) }

    val activeService = remember(selectedMealPeriod) {
        sampleServices.find { it.period == selectedMealPeriod } ?: sampleServices.first()
    }

    var thawTasks by remember { mutableStateOf(ThawTask.defaultSampleTasks()) }

    if (showHaccpDialog) {
        HaccpTempDialog(
            settingsManager = settingsManager,
            onDismiss = { showHaccpDialog = false },
            onSubmitSuccess = { showHaccpDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. STICKY SHIFT ANNOUNCEMENT & DIET CARD BANNER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shift Notice & Diet Card Alert",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Sysco Truck Arrival: 1:30 PM • Verify Mechanical Soft / Pureed diet cards before trayline rolls at ${activeService.cartDepartureTime}.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // --- 2. DYNAMIC ACTIVE MEAL SERVICE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Active Meal Line Board", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showHaccpDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Thermostat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Temp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Meal Period Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedMealPeriod == period,
                            onClick = { selectedMealPeriod = period },
                            label = {
                                Text(
                                    text = if (period == currentPeriod) "${period.displayName} (Active)" else period.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedMealPeriod == period) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Entree & Sides Breakdown
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Main Entree:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = activeService.mainEntree,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Sides & Dessert:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = activeService.sides.joinToString(" • ") + " • " + activeService.dessert,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Portion Scoop & Modification Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Portioning & Texture Mods:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Text("• Portions: ${activeService.portionInstructions}", fontSize = 11.sp)
                        Text("• Mechanical Soft: ${activeService.mechanicalSoftMod}", fontSize = 11.sp)
                        Text("• Pureed: ${activeService.pureedMod}", fontSize = 11.sp)
                    }
                }
            }
        }

        // --- 3. INTERACTIVE SHIFT PREP & THAW PULL CHECKLIST ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
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
                        Icon(Icons.Default.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Shift Thaw Pulls & Prep Schedule", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "${thawTasks.count { it.status == ThawTaskStatus.COMPLETED }}/${thawTasks.size} Done",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    thawTasks.forEachIndexed { index, task ->
                        Surface(
                            color = when (task.status) {
                                ThawTaskStatus.COMPLETED -> Color(0xFF10B981).copy(alpha = 0.12f)
                                ThawTaskStatus.IN_PREP -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ThawTaskStatus.SCHEDULED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(task.itemName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("(${task.batchQty})", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text("Pull Station: ${task.station} • Time: ${task.scheduledTime}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = task.status == ThawTaskStatus.COMPLETED,
                                        onClick = {
                                            val nextStatus = when (task.status) {
                                                ThawTaskStatus.SCHEDULED -> ThawTaskStatus.IN_PREP
                                                ThawTaskStatus.IN_PREP -> ThawTaskStatus.COMPLETED
                                                ThawTaskStatus.COMPLETED -> ThawTaskStatus.SCHEDULED
                                            }
                                            val updated = thawTasks.toMutableList()
                                            updated[index] = task.copy(status = nextStatus)
                                            thawTasks = updated

                                            // Auto-print ServSafe Thaw Label on completion
                                            if (nextStatus == ThawTaskStatus.COMPLETED) {
                                                val activePrinter = printerManager.getPreferredPrinter()
                                                if (activePrinter != null) {
                                                    val zpl = ZplGenerator.generateThawLabelZpl(
                                                        itemName = task.itemName,
                                                        upc = task.upc,
                                                        thawDate = LocalDate.now(),
                                                        staffInitials = settingsManager.staffInitials
                                                    )
                                                    printerManager.printDirect(activePrinter, zpl) { success, msg ->
                                                        Toast.makeText(context, "Printed Thaw Label: $msg", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Thaw Task Completed! (Connect Zebra printer in Settings to auto-print label)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        label = { Text(task.status.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
