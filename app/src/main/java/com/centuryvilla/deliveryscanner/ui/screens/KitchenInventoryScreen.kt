package com.centuryvilla.deliveryscanner.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centuryvilla.deliveryscanner.ProductManager
import com.centuryvilla.deliveryscanner.data.model.InventoryItem
import com.centuryvilla.deliveryscanner.ui.MainViewModel

@Composable
fun KitchenInventoryScreen(
    viewModel: MainViewModel,
    inventoryList: List<InventoryItem>,
    statusMessage: String
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Canned Goods", "Dry Storage", "Thickened Beverages", "Proteins & Frozen")

    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var newCountInput by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery, selectedCategory, inventoryList) {
        inventoryList.filter { item ->
            (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) || item.category.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action Bar: Sync to Google Sheets & Open in Drive
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.syncInventoryToSheets() },
                modifier = Modifier.weight(1.2f)
            ) {
                Text("Sync Live to Sheet", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ProductManager.SPREADSHEET_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Open Sheet", fontSize = 12.sp)
            }
        }

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (statusMessage.contains("live", ignoreCase = true) || statusMessage.contains("Synced", ignoreCase = true) || statusMessage.contains("success", ignoreCase = true)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 262 inventory items...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.take(4).forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 11.sp) }
                )
            }
        }

        // Product Count List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredList) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingItem = item
                            newCountInput = item.onHand
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (item.isNew) {
                                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(3.dp)) {
                                        Text("NEW", color = Color(0xFF2E7D32), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Text("${item.category} • Prev 8/24: ${if (item.previousCount.isBlank()) "—" else item.previousCount}", fontSize = 11.sp, color = Color.Gray)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "On Hand: ${item.onHand}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Item Count Dialog
    editingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Update On-Hand Count") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Category: ${item.category}", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = newCountInput,
                        onValueChange = { newCountInput = it },
                        label = { Text("Current On-Hand (9/1)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateInventoryCount(item.name, newCountInput)
                    editingItem = null
                    Toast.makeText(context, "Count updated!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save Count")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
