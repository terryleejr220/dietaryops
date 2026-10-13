package com.dietaryops.manager.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
import com.dietaryops.manager.ProductManager
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.util.SyscoUpcNormalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnOnScanBottomSheet(
    unrecognizedUpcOrItemNum: String,
    productManager: ProductManager,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onLinkedSuccess: (CatalogItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<CatalogItem?>(null) }
    var allCatalogItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var isLinking by remember { mutableStateOf(false) }

    val cleanInput = remember(unrecognizedUpcOrItemNum) {
        SyscoUpcNormalizer.normalize(unrecognizedUpcOrItemNum).ifBlank { unrecognizedUpcOrItemNum.trim() }
    }

    LaunchedEffect(Unit) {
        allCatalogItems = productManager.repository.getAllCatalogItemsList()
    }

    val autocompleteSuggestions = remember(searchQuery, allCatalogItems) {
        if (searchQuery.isBlank()) {
            allCatalogItems.take(8)
        } else {
            allCatalogItems.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.linkedInventoryItem.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }.take(10)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AddLink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = "Learn on Scan — Link Unmapped Item",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Bar Code / Item #: $cleanInput",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "Select the matching kitchen item from 'Inventory Raw' to permanently map this barcode to your master catalog:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Inventory Raw Item Name") },
                placeholder = { Text("Type item name e.g. Pork Loin / Green Beans...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Autocomplete Suggestions List
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(autocompleteSuggestions) { item ->
                        val isSelected = selectedItem?.syscoUpc == item.syscoUpc || selectedItem?.name == item.name
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedItem = item
                                    searchQuery = item.displayNameForLabel
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = item.displayNameForLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Category: ${item.category} • Unit: ${item.unit}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Link & Save Button
            Button(
                onClick = {
                    val targetItem = selectedItem ?: if (searchQuery.isNotBlank()) {
                        CatalogItem(
                            syscoUpc = cleanInput,
                            name = searchQuery.trim(),
                            linkedInventoryItem = searchQuery.trim(),
                            category = "General"
                        )
                    } else null

                    if (targetItem == null) {
                        Toast.makeText(context, "Select or type an item name from Inventory Raw", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLinking = true
                    coroutineScope.launch {
                        val mappedItem = targetItem.copy(
                            syscoUpc = if (targetItem.syscoUpc.isBlank() || targetItem.syscoUpc.startsWith("0748600")) cleanInput else targetItem.syscoUpc,
                            alternateBarcodes = if (targetItem.syscoUpc != cleanInput && targetItem.syscoUpc.isNotBlank()) {
                                if (targetItem.alternateBarcodes.isBlank()) cleanInput else "${targetItem.alternateBarcodes}, $cleanInput"
                            } else targetItem.alternateBarcodes,
                            linkedInventoryItem = targetItem.resolvedItemName
                        )

                        productManager.saveProduct(mappedItem)
                        isLinking = false
                        Toast.makeText(context, "Mapped '$cleanInput' -> '${mappedItem.resolvedItemName}' in Master Catalog!", Toast.LENGTH_LONG).show()
                        onLinkedSuccess(mappedItem)
                    }
                },
                enabled = !isLinking && (selectedItem != null || searchQuery.isNotBlank()),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (isLinking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Link & Complete Receiving", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
