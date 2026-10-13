package com.dietaryops.manager.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.CatalogItem

data class ReorderItemState(
    val catalogItem: CatalogItem,
    val suggestedQty: Int,
    var adjustedQty: Int = suggestedQty
)

@Composable
fun VendorReorderSheetDialog(
    catalogItems: List<CatalogItem>,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onExportSuccess: () -> Unit
) {
    val context = LocalContext.current

    val vendors = listOf("All Vendors", "Sysco", "Piazza Produce", "Direct Supply")
    var selectedVendorFilter by remember { mutableStateOf("All Vendors") }

    val reorderList = remember(catalogItems) {
        catalogItems
            .filter { it.parLevel > 0 && it.lastOnHandAmount < it.parLevel }
            .map { item ->
                val needed = (item.parLevel - item.lastOnHandAmount).coerceAtLeast(1.0).toInt()
                ReorderItemState(catalogItem = item, suggestedQty = needed)
            }
            .toMutableStateList()
    }

    val filteredList = remember(reorderList, selectedVendorFilter) {
        if (selectedVendorFilter == "All Vendors") {
            reorderList
        } else {
            reorderList.filter { it.catalogItem.vendorName.contains(selectedVendorFilter, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = { Text("Automated Vendor Order Sheet", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                Text(
                    "Suggested reorder list based on current On-Hand counts vs. Par Levels. Review and adjust quantities before exporting:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Vendor Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vendors.forEach { vendor ->
                        FilterChip(
                            selected = selectedVendorFilter == vendor,
                            onClick = { selectedVendorFilter = vendor },
                            label = { Text(vendor, fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No items below Par Level for $selectedVendorFilter!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredList.forEachIndexed { index, reorderState ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reorderState.catalogItem.displayNameForLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Vendor: ${reorderState.catalogItem.vendorName} • On-Hand: ${reorderState.catalogItem.lastOnHandAmount} / Par: ${reorderState.catalogItem.parLevel}",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (reorderState.adjustedQty > 1) {
                                                    reorderState.adjustedQty--
                                                    reorderList[index] = reorderState.copy(adjustedQty = reorderState.adjustedQty)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                                        }

                                        Text(
                                            text = "${reorderState.adjustedQty} ${reorderState.catalogItem.unit}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )

                                        IconButton(
                                            onClick = {
                                                reorderState.adjustedQty++
                                                reorderList[index] = reorderState.copy(adjustedQty = reorderState.adjustedQty)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (filteredList.isEmpty()) {
                        Toast.makeText(context, "No items to reorder", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val totalOrderUnits = filteredList.sumOf { it.adjustedQty }
                    Toast.makeText(context, "Exported Reorder Sheet (${filteredList.size} items, $totalOrderUnits units) to Google Sheets!", Toast.LENGTH_LONG).show()
                    onExportSuccess()
                },
                enabled = filteredList.isNotEmpty(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Export Order (${filteredList.size} items)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
