package com.dietaryops.manager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepStickersScreen(
    printerManager: ZebraPrinterManager,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedStickerType by remember { mutableStateOf("PREP") } // "PREP", "THAW", "OPENED", "USE_FIRST"

    var itemNameInput by remember { mutableStateOf("") }
    var retentionDaysInput by remember { mutableStateOf(7) }
    var printQuantityInput by remember { mutableStateOf(1) }
    var isPrinting by remember { mutableStateOf(false) }

    val connectedDevice by printerManager.connectedDevice.collectAsState()
    val activePrinter =
        remember(connectedDevice) { connectedDevice ?: printerManager.getPreferredPrinter() }

    val computedDiscardDate = remember(retentionDaysInput) {
        LocalDate.now().plusDays(retentionDaysInput.toLong())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Title Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Prep & Rotation Sticker Studio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Print ServSafe compliant prep, thaw, opened container, & rotation labels directly to Zebra printers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Sticker Type Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Sticker Type:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStickerType == "PREP",
                        onClick = {
                            selectedStickerType = "PREP"
                            retentionDaysInput = 7
                        },
                        label = {
                            Text(
                                "🥗 Cooked / Prep",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedStickerType == "THAW",
                        onClick = {
                            selectedStickerType = "THAW"
                            retentionDaysInput = 7
                        },
                        label = {
                            Text(
                                "🥩 Thaw Pull",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStickerType == "OPENED",
                        onClick = {
                            selectedStickerType = "OPENED"
                            retentionDaysInput = 7
                        },
                        label = {
                            Text(
                                "📦 Opened TCS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedStickerType == "USE_FIRST",
                        onClick = {
                            selectedStickerType = "USE_FIRST"
                            retentionDaysInput = 1
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        label = {
                            Text(
                                "⚠️ USE FIRST",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sticker Customization & Form Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Sticker Details & Retention:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                OutlinedTextField(
                    value = itemNameInput,
                    onValueChange = { itemNameInput = it },
                    label = { Text("Food Item / Dish Name") },
                    placeholder = { Text("e.g. Tuna Salad / Cooked Marinara / Sliced Turkey") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedStickerType != "USE_FIRST") {
                    Text(
                        "ServSafe Shelf-Life Retention:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1, 3, 5, 7, 14).forEach { days ->
                            FilterChip(
                                selected = retentionDaysInput == days,
                                onClick = { retentionDaysInput = days },
                                label = { Text("+$days Days", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Live Preview Badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "STICKER PREVIEW:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = itemNameInput.ifBlank { "Sample Product Name" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Discard Date: ${computedDiscardDate.monthValue}/${computedDiscardDate.dayOfMonth}/${computedDiscardDate.year} • By: ${settingsManager.staffInitials}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quantity & Print Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Copies:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        IconButton(
                            onClick = { if (printQuantityInput > 1) printQuantityInput-- },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text("$printQuantityInput", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        IconButton(
                            onClick = { printQuantityInput++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }

                    Button(
                        onClick = {
                            if (itemNameInput.isBlank() && selectedStickerType != "USE_FIRST") {
                                Toast.makeText(
                                    context,
                                    "Please enter a product/dish name",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            if (activePrinter == null) {
                                Toast.makeText(
                                    context,
                                    "Connect Zebra printer in Settings to print",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }

                            isPrinting = true
                            val zpl = when (selectedStickerType) {
                                "THAW" -> ZplGenerator.generateThawLabelZpl(
                                    itemName = itemNameInput,
                                    staffInitials = settingsManager.staffInitials
                                )

                                "OPENED" -> ZplGenerator.generateOpenedContainerZpl(
                                    itemName = itemNameInput,
                                    staffInitials = settingsManager.staffInitials
                                )

                                "USE_FIRST" -> ZplGenerator.generateUseFirstLabelZpl(
                                    itemName = itemNameInput,
                                    staffInitials = settingsManager.staffInitials
                                )

                                else -> ZplGenerator.generatePrepLabelZpl(
                                    prepItemName = itemNameInput,
                                    retentionDays = retentionDaysInput,
                                    staffInitials = settingsManager.staffInitials
                                )
                            }

                            printerManager.printDirect(
                                activePrinter,
                                zpl,
                                quantity = printQuantityInput
                            ) { success, msg ->
                                isPrinting = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isPrinting,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print Sticker ($printQuantityInput)")
                        }
                    }
                }
            }
        }
    }
}
