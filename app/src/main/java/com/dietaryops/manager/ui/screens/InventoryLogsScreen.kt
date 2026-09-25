package com.dietaryops.manager.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ProductManager
import com.dietaryops.manager.data.SettingsManager.Companion.DEFAULT_CATEGORIES
import com.dietaryops.manager.ZebraPrinterManager
import com.dietaryops.manager.ZplGenerator
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ScanRecord
import com.dietaryops.manager.data.repository.DeliveryRepository
import com.dietaryops.manager.ui.theme.*
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.util.SyscoUpcNormalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.dietaryops.manager.data.remote.SheetScanRecordDto
import com.dietaryops.manager.data.remote.SheetSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InventoryLogsScreen(
    repository: DeliveryRepository,
    printerManager: ZebraPrinterManager,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val productManager = remember(context) { ProductManager(context) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Scan Logs, 1 = Master Catalog

    val scanRecords by repository.getAllScanRecords().collectAsState(initial = emptyList())
    val catalogItems by repository.getAllCatalogItems().collectAsState(initial = emptyList())

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedSyncFilter by remember { mutableStateOf("All") } // "All", "Synced", "Pending"
    var selectedCatalogFilter by remember { mutableStateOf("All") } // "All", "Low Stock", "Recently Scanned"

    var isSyncing by remember { mutableStateOf(false) }
    var isImportingCatalog by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showAddCatalogDialog by remember { mutableStateOf(false) }
    var showMassPrintDialog by remember { mutableStateOf(false) }

    // Auto-sync catalog from published Google Sheet if empty or default
    LaunchedEffect(Unit) {
        if (catalogItems.size <= 7) {
            coroutineScope.launch(Dispatchers.IO) {
                repository.importCatalogFromPublishedCsv(settingsManager.publishedWebUrl)
            }
        }
    }

    val unsyncedCount = remember(scanRecords) {
        scanRecords.count { !it.syncedToSheets }
    }

    // Filtered scan records list
    val filteredScanRecords = remember(scanRecords, searchQuery, selectedCategoryFilter, selectedSyncFilter) {
        scanRecords.filter { record ->
            val matchesSearch = searchQuery.isBlank() ||
                    record.itemName.contains(searchQuery, ignoreCase = true) ||
                    record.syscoUpc.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategoryFilter == "All" ||
                    record.category.equals(selectedCategoryFilter, ignoreCase = true)

            val matchesSync = when (selectedSyncFilter) {
                "Synced" -> record.syncedToSheets
                "Pending" -> !record.syncedToSheets
                else -> true
            }

            matchesSearch && matchesCategory && matchesSync
        }
    }

    val recentlyScannedUpcs = remember(scanRecords) {
        scanRecords.map { it.syscoUpc }.toSet()
    }

    var sortMode by remember { mutableStateOf("Shelf Layout") } // "Shelf Layout", "Alphabetical", "Category"

    // Filtered master catalog items list
    val filteredCatalogItems = remember(catalogItems, searchQuery, activeTab, sortMode, selectedCatalogFilter, recentlyScannedUpcs) {
        val filtered = catalogItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.syscoUpc.contains(searchQuery, ignoreCase = true) ||
                    item.syscoItemNumber.contains(searchQuery, ignoreCase = true) ||
                    item.piazzaItemNumber.contains(searchQuery, ignoreCase = true)
                    
            val matchesCatalogFilter = when (selectedCatalogFilter) {
                "Low Stock" -> item.parLevel > 0 && item.lastOnHandAmount < item.parLevel
                "Recently Scanned" -> recentlyScannedUpcs.contains(item.syscoUpc)
                else -> true
            }
            
            matchesSearch && matchesCatalogFilter
        }

        // Apply sorting: Always push zero'd items to the bottom unless specifically searching or filtering
        val sorted = when (sortMode) {
            "Alphabetical" -> filtered.sortedBy { it.name }
            "Category" -> filtered.sortedWith(compareBy({ it.category }, { it.name }))
            else -> {
                filtered.sortedWith(
                    compareBy<CatalogItem> { item ->
                        when (ZplGenerator.getStorageLocationForCategory(item.category)) {
                            "COOLER" -> 0
                            "FREEZER" -> 1
                            "DRY STORAGE" -> 2
                            else -> 3
                        }
                    }.thenBy { it.category }
                     .thenBy { it.name }
                )
            }
        }
        
        // Push items with 0 on hand to the bottom if we aren't heavily filtering
        if (searchQuery.isBlank() && selectedCatalogFilter == "All") {
            sorted.sortedBy { it.lastOnHandAmount <= 0 }
        } else {
            sorted
        }
    }

    // Manual Sync function
    val triggerManualSync: () -> Unit = {
        val webAppUrl = settingsManager.webAppUrl
        if (webAppUrl.isBlank()) {
            Toast.makeText(context, "Configure Google Sheets Web App URL in Settings!", Toast.LENGTH_LONG).show()
        } else {
            isSyncing = true
            syncStatusText = "Syncing $unsyncedCount pending records..."
            coroutineScope.launch(Dispatchers.IO) {
                val result = repository.syncWithGoogleSheets(webAppUrl)
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    result.onSuccess { count ->
                        syncStatusText = "Successfully synced $count records!"
                        Toast.makeText(context, "Synced $count records to Google Sheets!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        syncStatusText = "Sync failed: ${err.localizedMessage}"
                        Toast.makeText(context, "Sync error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Executive KPI Summary Dashboard Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Metric 1: Total Scans
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            "SCANS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${scanRecords.size}",
                        style = MonospaceQuantityStyle,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Metric 2: Pending Sync
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (unsyncedCount > 0) ExpiringSoonAmber.copy(alpha = 0.2f) else FreshGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (unsyncedCount > 0) Icons.Default.CloudSync else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (unsyncedCount > 0) ExpiringSoonAmber else FreshGreen
                                )
                            }
                        }
                        Text(
                            "PENDING",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$unsyncedCount",
                        style = MonospaceQuantityStyle,
                        fontSize = 18.sp,
                        color = if (unsyncedCount > 0) ExpiringSoonAmber else FreshGreen
                    )
                }
            }

            // Metric 3: Master Catalog
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Inventory,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            "CATALOG",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${catalogItems.size}",
                        style = MonospaceQuantityStyle,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Tab Navigation Bar (Scan Logs vs Master Catalog vs Count Sheet Walk)
        Surface(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface
        ) {
            PrimaryTabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Scan Logs (${scanRecords.size})", fontSize = 11.sp, fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Master Catalog (${catalogItems.size})", fontSize = 11.sp, fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Count Sheet Walk", fontSize = 11.sp, fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium) }
                )
            }
        }

        // Search Bar (Full Width Rounded Pill)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (activeTab == 0) "Search log item or barcode..." else "Search catalog item or barcode...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Action & Header Control Row
        if (activeTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Delivery Scans (${filteredScanRecords.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$unsyncedCount pending sync", fontSize = 11.sp, color = if (unsyncedCount > 0) SyncPendingColor else SyncSuccessColor)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = triggerManualSync,
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (unsyncedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(settingsManager.publishedWebUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error opening web sheet: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "View Published Web Sheet", tint = MaterialTheme.colorScheme.primary)
                    }

                    if (scanRecords.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Category Filter Chips for Scan Logs only
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == "All",
                        onClick = { selectedCategoryFilter = "All" },
                        label = { Text("All") }
                    )
                }
                items(DEFAULT_CATEGORIES) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Catalog Items (${filteredCatalogItems.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showMassPrintDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mass Print", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val pubUrl = settingsManager.publishedWebUrl
                            if (pubUrl.isBlank()) {
                                Toast.makeText(context, "Configure Published Web Sheet URL in Settings first!", Toast.LENGTH_LONG).show()
                            } else {
                                isImportingCatalog = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val res = repository.importCatalogFromPublishedCsv(pubUrl)
                                    withContext(Dispatchers.Main) {
                                        isImportingCatalog = false
                                        res.onSuccess { count ->
                                            Toast.makeText(context, "Synced $count catalog items!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Sync error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isImportingCatalog,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isImportingCatalog) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Sheet", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showAddCatalogDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }
            }

            // Compact Sort Row for Master Catalog (Streamlined single clean row)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item { Text("Sort:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray) }
                items(listOf("Shelf Layout", "Alphabetical", "Category")) { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(mode, fontSize = 11.sp) }
                    )
                }
                item {
                    VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
                }
                item { Text("Filter:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray) }
                items(listOf("All", "Low Stock", "Recently Scanned")) { filter ->
                    FilterChip(
                        selected = selectedCatalogFilter == filter,
                        onClick = { selectedCatalogFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        colors = if (filter == "Low Stock" && selectedCatalogFilter == filter) {
                            FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.errorContainer, labelColor = MaterialTheme.colorScheme.onErrorContainer)
                        } else {
                            FilterChipDefaults.filterChipColors()
                        }
                    )
                }
            }
        }

        if (activeTab == 0) {
            // Sync Filter Chips for Scan Logs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sync Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                listOf("All", "Synced", "Pending").forEach { filter ->
                    FilterChip(
                        selected = selectedSyncFilter == filter,
                        onClick = { selectedSyncFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Sync Status Banner
            syncStatusText?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { syncStatusText = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Record Count Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Logged Delivery Scans (${filteredScanRecords.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "$unsyncedCount pending sync",
                        fontSize = 12.sp,
                        color = if (unsyncedCount > 0) SyncPendingColor else SyncSuccessColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (scanRecords.isNotEmpty()) {
                    TextButton(onClick = { showClearAllConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            // Scan Records List
            if (filteredScanRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No scan log entries found", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredScanRecords, key = { it.id }) { record ->
                        ScanRecordCard(
                            record = record,
                            printerManager = printerManager,
                            onUpdate = { updatedRecord ->
                                coroutineScope.launch {
                                    repository.addScanRecord(updatedRecord)
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    repository.deleteScanRecord(record.id)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Master Catalog Tab Content
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Dietary Catalog Items (${filteredCatalogItems.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Master Database",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            if (filteredCatalogItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No catalog items match search", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sortMode == "Shelf Layout") {
                        val grouped = filteredCatalogItems.groupBy { ZplGenerator.getStorageLocationForCategory(it.category) }
                        val orderedLocations = listOf("COOLER", "FREEZER", "DRY STORAGE")
                        val allLocs = orderedLocations + grouped.keys.filter { it !in orderedLocations }

                        for (loc in allLocs) {
                            val itemsInLoc = grouped[loc] ?: emptyList()
                            if (itemsInLoc.isNotEmpty()) {
                                item(key = "header_$loc") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = "📦 $loc (${itemsInLoc.size} items)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                items(itemsInLoc, key = { it.syscoUpc }) { catalogItem ->
                                    CatalogItemCard(
                                        item = catalogItem,
                                        printerManager = printerManager,
                                        onLogDelivery = { itemToLog ->
                                            coroutineScope.launch {
                                                val calc = DateCalculator.calculate(
                                                    scanDate = LocalDate.now(),
                                                    shelfLifeDays = itemToLog.defaultShelfLifeDays
                                                )
                                                val effectiveOnHand = if (itemToLog.lastOnHandAmount > 0.0) itemToLog.lastOnHandAmount else 1.0
                                                val record = ScanRecord(
                                                    syscoUpc = itemToLog.syscoUpc,
                                                    itemName = itemToLog.name,
                                                    deliveryDate = calc.deliveryDateIso,
                                                    useByDate = calc.useByDateIso,
                                                    category = itemToLog.category,
                                                    shelfLifeDays = itemToLog.defaultShelfLifeDays,
                                                    unit = itemToLog.unit,
                                                    onHandAmount = effectiveOnHand
                                                )
                                                repository.addScanRecord(record)
                                                Toast.makeText(context, "Logged delivery scan for '${itemToLog.name}'!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onUpdate = { updatedItem ->
                                            coroutineScope.launch {
                                                repository.saveCatalogItem(updatedItem)
                                                Toast.makeText(context, "Catalog item saved!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDelete = {
                                            coroutineScope.launch {
                                                repository.deleteCatalogItem(catalogItem.syscoUpc)
                                                Toast.makeText(context, "Deleted from catalog", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredCatalogItems, key = { it.syscoUpc }) { catalogItem ->
                            CatalogItemCard(
                                item = catalogItem,
                                printerManager = printerManager,
                                onLogDelivery = { itemToLog ->
                                    coroutineScope.launch {
                                        val calc = DateCalculator.calculate(
                                            scanDate = LocalDate.now(),
                                            shelfLifeDays = itemToLog.defaultShelfLifeDays
                                        )
                                        val effectiveOnHand = if (itemToLog.lastOnHandAmount > 0.0) itemToLog.lastOnHandAmount else 1.0
                                        val record = ScanRecord(
                                            syscoUpc = itemToLog.syscoUpc,
                                            itemName = itemToLog.name,
                                            deliveryDate = calc.deliveryDateIso,
                                            useByDate = calc.useByDateIso,
                                            category = itemToLog.category,
                                            shelfLifeDays = itemToLog.defaultShelfLifeDays,
                                            unit = itemToLog.unit,
                                            onHandAmount = effectiveOnHand
                                        )
                                        repository.addScanRecord(record)
                                        Toast.makeText(context, "Logged delivery scan for '${itemToLog.name}'!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onUpdate = { updatedItem ->
                                    coroutineScope.launch {
                                        repository.saveCatalogItem(updatedItem)
                                        Toast.makeText(context, "Catalog item saved!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        repository.deleteCatalogItem(catalogItem.syscoUpc)
                                        Toast.makeText(context, "Deleted from catalog", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (activeTab == 2) {
            // Count Sheet Top Action Banner
            var isSyncingCountSheet by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📋 Floor Count Walk (Zero Handwriting)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Tap + / - to adjust on-hand counts. Sync populates Inventory Raw on Google Sheets.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Button(
                        onClick = {
                            val webAppUrl = settingsManager.webAppUrl
                            if (webAppUrl.isBlank()) {
                                Toast.makeText(context, "Configure Google Sheets Web App URL in Settings!", Toast.LENGTH_LONG).show()
                            } else {
                                isSyncingCountSheet = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val countDtos = catalogItems.map {
                                        SheetScanRecordDto(
                                            id = "COUNT-${it.syscoUpc}",
                                            upc = it.syscoUpc,
                                            itemNumber = it.syscoItemNumber,
                                            name = it.name,
                                            deliveryDate = LocalDate.now().toString(),
                                            useByDate = LocalDate.now().plusDays(it.defaultShelfLifeDays.toLong()).toString(),
                                            storageArea = ZplGenerator.getStorageLocationForCategory(it.category),
                                            receivedBy = settingsManager.staffName,
                                            onHandQty = it.lastOnHandAmount,
                                            category = it.category,
                                            shelfLifeDays = it.defaultShelfLifeDays,
                                            unit = it.unit,
                                            scanTimestamp = System.currentTimeMillis()
                                        )
                                    }
                                    val payload = SheetSyncPayload(
                                        spreadsheetId = settingsManager.sheetId,
                                        sheetTab = "Inventory Raw",
                                        companyCode = settingsManager.companyCode,
                                        records = countDtos
                                    )
                                    val res = repository.syncCatalogWithPayload(webAppUrl, payload)
                                    withContext(Dispatchers.Main) {
                                        isSyncingCountSheet = false
                                        res.onSuccess {
                                            Toast.makeText(context, "Populated Inventory Raw count sheet on Google Sheets!", Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Sync error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isSyncingCountSheet,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSyncingCountSheet) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync to Sheets", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Category Filter Row for Count Walk
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == "All",
                        onClick = { selectedCategoryFilter = "All" },
                        label = { Text("All (${filteredCatalogItems.size})", fontSize = 11.sp) }
                    )
                }
                items(DEFAULT_CATEGORIES) { cat ->
                    val catCount = catalogItems.count { it.category.equals(cat, ignoreCase = true) }
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text("$cat ($catCount)", fontSize = 11.sp) }
                    )
                }
            }

            // Inventory Count Cards List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val walkItems = filteredCatalogItems.filter {
                    selectedCategoryFilter == "All" || it.category.equals(selectedCategoryFilter, ignoreCase = true)
                }

                items(walkItems, key = { it.syscoUpc }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${item.category} • UPC: ${item.syscoUpc}", fontSize = 11.sp, color = Color.Gray)
                                Text("Storage: ${ZplGenerator.getStorageLocationForCategory(item.category)} • Unit: ${item.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedIconButton(
                                    onClick = {
                                        val newQty = (item.lastOnHandAmount - 1.0).coerceAtLeast(0.0)
                                        val updated = item.copy(lastOnHandAmount = newQty)
                                        coroutineScope.launch(Dispatchers.IO) { repository.saveCatalogItem(updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = if (item.lastOnHandAmount % 1.0 == 0.0) "${item.lastOnHandAmount.toInt()}" else "${item.lastOnHandAmount}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                OutlinedIconButton(
                                    onClick = {
                                        val newQty = item.lastOnHandAmount + 1.0
                                        val updated = item.copy(lastOnHandAmount = newQty)
                                        coroutineScope.launch(Dispatchers.IO) { repository.saveCatalogItem(updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Mass Shelf Label Print Dialog
    if (showMassPrintDialog) {
        var selectedCategory by remember { mutableStateOf("All") }
        var selectedUpcs by remember { mutableStateOf(catalogItems.map { it.syscoUpc }.toSet()) }

        val dialogItems = remember(catalogItems, selectedCategory) {
            if (selectedCategory == "All") catalogItems else catalogItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showMassPrintDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mass Print Shelf Labels", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select category or items to stream ZPL shelf labels to Zebra printer:", fontSize = 12.sp, color = Color.Gray)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == "All",
                                onClick = {
                                    selectedCategory = "All"
                                    selectedUpcs = catalogItems.map { it.syscoUpc }.toSet()
                                },
                                label = { Text("All (${catalogItems.size})", fontSize = 10.sp) }
                            )
                        }
                        items(DEFAULT_CATEGORIES) { cat ->
                            val count = catalogItems.count { it.category.equals(cat, ignoreCase = true) }
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = cat
                                    selectedUpcs = catalogItems.filter { it.category.equals(cat, ignoreCase = true) }.map { it.syscoUpc }.toSet()
                                },
                                label = { Text("$cat ($count)", fontSize = 10.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selected: ${selectedUpcs.size} item(s)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = {
                            if (selectedUpcs.size == dialogItems.size) {
                                selectedUpcs = emptySet()
                            } else {
                                selectedUpcs = dialogItems.map { it.syscoUpc }.toSet()
                            }
                        }) {
                            Text(if (selectedUpcs.size == dialogItems.size) "Deselect All" else "Select All", fontSize = 11.sp)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(dialogItems) { item ->
                            val isChecked = selectedUpcs.contains(item.syscoUpc)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedUpcs = if (isChecked) selectedUpcs - item.syscoUpc else selectedUpcs + item.syscoUpc
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedUpcs = if (checked == true) selectedUpcs + item.syscoUpc else selectedUpcs - item.syscoUpc
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("${item.category} • ${item.syscoUpc}", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetPrinter = printerManager.getPreferredPrinter()
                        if (targetPrinter == null) {
                            Toast.makeText(context, "Pair or connect a Zebra printer first!", Toast.LENGTH_LONG).show()
                        } else if (selectedUpcs.isEmpty()) {
                            Toast.makeText(context, "Select at least one item to print", Toast.LENGTH_SHORT).show()
                        } else {
                            val itemsToPrint = catalogItems.filter { selectedUpcs.contains(it.syscoUpc) }
                            showMassPrintDialog = false
                            Toast.makeText(context, "Streaming ${itemsToPrint.size} shelf label(s) to Zebra...", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch(Dispatchers.IO) {
                                itemsToPrint.forEach { catItem ->
                                    val zpl = ZplGenerator.generateShelfLabelZpl(catItem)
                                    printerManager.printDirect(targetPrinter, zpl) { _, _ -> }
                                    delay(400)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Printed ${itemsToPrint.size} shelf label(s)!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print ${selectedUpcs.size} Labels", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMassPrintDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Logs Confirmation Dialog
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Inventory Logs?") },
            text = { Text("Are you sure you want to delete all ${scanRecords.size} scan log entries from local storage?") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        repository.clearAllScanRecords()
                        showClearAllConfirm = false
                        Toast.makeText(context, "Cleared all inventory logs", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add New Catalog Item Dialog
    if (showAddCatalogDialog) {
        var newUpc by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("General") }
        var newUnit by remember { mutableStateOf("EA") }
        var newDaysStr by remember { mutableStateOf("7") }
        var newOnHandStr by remember { mutableStateOf("1.0") }

        var isCategoryExpanded by remember { mutableStateOf(false) }
        var isUnitExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddCatalogDialog = false },
            title = { Text("Add Master Catalog Item") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newUpc,
                        onValueChange = { newUpc = it },
                        label = { Text("Item Barcode (UPC)") },
                        placeholder = { Text("e.g. 074865123408") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Product Name") },
                        placeholder = { Text("e.g. Diced Peaches") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newOnHandStr,
                        onValueChange = { newOnHandStr = it },
                        label = { Text("Initial On Hand Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Selector
                    ExposedDropdownMenuBox(
                        expanded = isCategoryExpanded,
                        onExpandedChange = { isCategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isCategoryExpanded,
                            onDismissRequest = { isCategoryExpanded = false }
                        ) {
                            DEFAULT_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        newCategory = cat
                                        newDaysStr = DateCalculator.getShelfLifeDaysForCategory(cat).toString()
                                        isCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit Selector
                    ExposedDropdownMenuBox(
                        expanded = isUnitExpanded,
                        onExpandedChange = { isUnitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = newUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit of Measure") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isUnitExpanded,
                            onDismissRequest = { isUnitExpanded = false }
                        ) {
                            listOf("EA", "LB", "CS", "CTN", "GAL", "BLK", "BOX", "PK").forEach { unitOpt ->
                                DropdownMenuItem(
                                    text = { Text(unitOpt) },
                                    onClick = {
                                        newUnit = unitOpt
                                        isUnitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newDaysStr,
                        onValueChange = { newDaysStr = it },
                        label = { Text("Default Shelf-Life Days (+3 to +14)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newUpc.isBlank() || newName.isBlank()) {
                        Toast.makeText(context, "Enter UPC and Product Name", Toast.LENGTH_SHORT).show()
                    } else {
                        val cleanUpc = SyscoUpcNormalizer.normalize(newUpc)
                        val daysInt = newDaysStr.toIntOrNull() ?: 7
                        val onHandVal = newOnHandStr.toDoubleOrNull() ?: 1.0
                        val item = CatalogItem(
                            syscoUpc = cleanUpc,
                            name = newName,
                            category = newCategory,
                            defaultShelfLifeDays = DateCalculator.clampShelfLifeDays(daysInt),
                            unit = newUnit,
                            lastOnHandAmount = onHandVal
                        )
                        coroutineScope.launch {
                            repository.saveCatalogItem(item)
                            showAddCatalogDialog = false
                            Toast.makeText(context, "Added '${item.name}' to catalog!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Add to Catalog")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCatalogDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ScanRecordCard(
    record: ScanRecord,
    printerManager: ZebraPrinterManager,
    onUpdate: (ScanRecord) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.itemName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "UPC: ${record.syscoUpc}",
                            style = MonospaceBarcodeStyle,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (record.isAudit) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("AUDIT / INFO-ONLY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }

                // Sync Badge
                Surface(
                    color = if (record.syncedToSheets) SyncSuccessColor.copy(alpha = 0.15f) else SyncPendingColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (record.syncedToSheets) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (record.syncedToSheets) SyncSuccessColor else SyncPendingColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (record.syncedToSheets) "Synced" else "Pending Sync",
                            color = if (record.syncedToSheets) SyncSuccessColor else SyncPendingColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Compact Metadata Row: Category chip, Shelf Life chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(record.category, fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )

                AssistChip(
                    onClick = {},
                    label = { Text("+${record.shelfLifeDays}d", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text("Delivered: ${record.deliveryDate}", fontSize = 10.sp, color = Color.Gray)
                    Text("Use By: ${record.useByDate}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Bottom Action & Qty Row: On-Hand quantity control on left, Print, Edit, Delete action buttons on right on the exact same row!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // On-Hand number and +/- quantity controls
                val displayOnHand = if (record.onHandAmount > 0.0) record.onHandAmount else 1.0
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Qty:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        IconButton(
                            onClick = {
                                if (displayOnHand > 0.5) {
                                    val newQty = (displayOnHand - 1.0).coerceAtLeast(0.5)
                                    onUpdate(record.copy(onHandAmount = newQty, syncedToSheets = false))
                                }
                            },
                            modifier = Modifier.size(28.dp),
                            enabled = displayOnHand > 0.5
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = "${if (displayOnHand % 1.0 == 0.0) displayOnHand.toInt().toString() else displayOnHand} ${record.unit}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                val newQty = displayOnHand + 1.0
                                onUpdate(record.copy(onHandAmount = newQty, syncedToSheets = false))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Edit, Delete and Print action buttons on the exact same row
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val printer = printerManager.connectedDevice.value ?: printerManager.getPreferredPrinter()
                            if (printer != null) {
                                val parsedDeliv = DateCalculator.parseDate(record.deliveryDate) ?: LocalDate.now()
                                val parsedUseBy = DateCalculator.parseDate(record.useByDate) ?: parsedDeliv.plusDays(record.shelfLifeDays.toLong())
                                val staffInitials = SettingsManager(context).staffInitials
                                val zpl = ZplGenerator.generateLabel(
                                    itemName = record.itemName,
                                    upc = record.syscoUpc,
                                    deliveryDate = parsedDeliv,
                                    useByDate = parsedUseBy,
                                    category = record.category,
                                    onHandAmount = record.onHandAmount,
                                    staffInitials = staffInitials
                                )
                                printerManager.printDirect(printer, zpl, quantity = displayOnHand.toInt().coerceAtLeast(1)) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No printer connected", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Re-print Label", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Record", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Record", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Scan Log?") },
            text = { Text("Are you sure you want to delete '${record.itemName}' log entry?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        var editName by remember { mutableStateOf(record.itemName) }
        var editCategory by remember { mutableStateOf(record.category) }
        var editUnit by remember { mutableStateOf(record.unit) }
        val initialOnHand = if (record.onHandAmount > 0.0) record.onHandAmount else 1.0
        var editOnHandStr by remember { mutableStateOf(if (initialOnHand % 1.0 == 0.0) initialOnHand.toInt().toString() else initialOnHand.toString()) }
        var editDaysStr by remember { mutableStateOf(record.shelfLifeDays.toString()) }
        var editDeliveryDateStr by remember { mutableStateOf(record.deliveryDate) }

        var isCategoryExpanded by remember { mutableStateOf(false) }
        var isUnitExpanded by remember { mutableStateOf(false) }

        val editDaysInt = editDaysStr.toIntOrNull() ?: record.shelfLifeDays
        val parsedDeliveryDate = DateCalculator.parseDate(editDeliveryDateStr) ?: LocalDate.now()
        val calculatedPreview = DateCalculator.calculate(
            scanDate = parsedDeliveryDate,
            shelfLifeDays = editDaysInt
        )

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Log Entry") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Item Barcode: ${record.syscoUpc}", fontSize = 12.sp, color = Color.Gray)

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Product Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editOnHandStr,
                        onValueChange = { editOnHandStr = it },
                        label = { Text("On Hand Amount / Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Selector
                    ExposedDropdownMenuBox(
                        expanded = isCategoryExpanded,
                        onExpandedChange = { isCategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isCategoryExpanded,
                            onDismissRequest = { isCategoryExpanded = false }
                        ) {
                            DEFAULT_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        editCategory = cat
                                        editDaysStr = DateCalculator.getShelfLifeDaysForCategory(cat).toString()
                                        isCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit Selector
                    ExposedDropdownMenuBox(
                        expanded = isUnitExpanded,
                        onExpandedChange = { isUnitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit of Measure") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isUnitExpanded,
                            onDismissRequest = { isUnitExpanded = false }
                        ) {
                            listOf("EA", "LB", "CS", "CTN", "GAL", "BLK", "BOX", "PK").forEach { unitOpt ->
                                DropdownMenuItem(
                                    text = { Text(unitOpt) },
                                    onClick = {
                                        editUnit = unitOpt
                                        isUnitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editDeliveryDateStr,
                        onValueChange = { editDeliveryDateStr = it },
                        label = { Text("Delivery Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDaysStr,
                        onValueChange = { editDaysStr = it },
                        label = { Text("Shelf Life Days (+3 to +14 days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Preview Calculated Dates:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Delivery: ${calculatedPreview.deliveryDateFormatted}", fontSize = 13.sp)
                            Text(
                                "Use By: ${calculatedPreview.useByDateFormatted} (+${calculatedPreview.shelfLifeDays}d)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val validDays = DateCalculator.clampShelfLifeDays(editDaysInt)
                    val parsedOnHand = editOnHandStr.toDoubleOrNull()
                    val editOnHand = if (parsedOnHand != null && parsedOnHand > 0.0) parsedOnHand else initialOnHand
                    val updatedRecord = record.copy(
                        itemName = editName.ifBlank { record.itemName },
                        category = editCategory,
                        unit = editUnit,
                        onHandAmount = editOnHand,
                        deliveryDate = calculatedPreview.deliveryDateIso,
                        useByDate = calculatedPreview.useByDateIso,
                        shelfLifeDays = validDays,
                        syncedToSheets = false // Mark unsynced so edit can re-sync to sheets
                    )
                    onUpdate(updatedRecord)
                    showEditDialog = false
                    Toast.makeText(context, "Log entry updated!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogItemCard(
    item: CatalogItem,
    printerManager: ZebraPrinterManager,
    onLogDelivery: (CatalogItem) -> Unit,
    onUpdate: (CatalogItem) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val productManager = remember(context) { ProductManager(context) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "UPC: ${item.syscoUpc}",
                            style = MonospaceBarcodeStyle,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { onLogDelivery(item) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Delivery", fontSize = 12.sp)
                }
            }

            // Compact Metadata Row: Category chip, Inline On Hand quantity control badge, Shelf-life chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.category, fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )

                // Inline On Hand quantity control badge alongside category chip
                val displayOnHand = if (item.lastOnHandAmount > 0.0) item.lastOnHandAmount else 1.0
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("On Hand:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        IconButton(
                            onClick = {
                                if (displayOnHand > 0.5) {
                                    val newQty = (displayOnHand - 1.0).coerceAtLeast(0.5)
                                    onUpdate(item.copy(lastOnHandAmount = newQty))
                                }
                            },
                            modifier = Modifier.size(28.dp),
                            enabled = displayOnHand > 0.5
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${if (displayOnHand % 1.0 == 0.0) displayOnHand.toInt().toString() else displayOnHand} ${item.unit}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                val newQty = displayOnHand + 1.0
                                onUpdate(item.copy(lastOnHandAmount = newQty))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                AssistChip(
                    onClick = {},
                    label = { Text("+${item.defaultShelfLifeDays}d", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val printer = printerManager.connectedDevice.value ?: printerManager.getPreferredPrinter()
                        if (printer != null) {
                            coroutineScope.launch {
                                productManager.saveProduct(item)
                                val scanRecord = ScanRecord(
                                    syscoUpc = item.syscoUpc,
                                    itemName = "Shelf Label: ${item.name}",
                                    deliveryDate = LocalDate.now().toString(),
                                    useByDate = LocalDate.now().plusDays(item.defaultShelfLifeDays.toLong()).toString(),
                                    category = item.category,
                                    shelfLifeDays = item.defaultShelfLifeDays,
                                    unit = item.unit,
                                    onHandAmount = item.lastOnHandAmount
                                )
                                productManager.logScanToSheets(scanRecord)
                            }
                            val zpl = ZplGenerator.generateShelfLabelZpl(item)
                            printerManager.printDirect(printer, zpl, quantity = 1) { success, msg ->
                                if (success) {
                                    Toast.makeText(context, "Printed shelf label for ${item.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Print failed: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "No printer connected", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Shelf Label", fontSize = 12.sp)
                }

                TextButton(
                    onClick = { showEditDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Item", fontSize = 12.sp)
                }

                TextButton(
                    onClick = { showDeleteConfirm = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Catalog Item?") },
            text = { Text("Are you sure you want to remove '${item.name}' from the master catalog?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        var editName by remember { mutableStateOf(item.name) }
        var editCategory by remember { mutableStateOf(item.category) }
        var editUnit by remember { mutableStateOf(item.unit) }
        val initialOnHand = if (item.lastOnHandAmount > 0.0) item.lastOnHandAmount else 1.0
        var editOnHandStr by remember { mutableStateOf(if (initialOnHand % 1.0 == 0.0) initialOnHand.toInt().toString() else initialOnHand.toString()) }
        var editDaysStr by remember { mutableStateOf(item.defaultShelfLifeDays.toString()) }

        var isCategoryExpanded by remember { mutableStateOf(false) }
        var isUnitExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Catalog Item") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("UPC: ${item.syscoUpc}", fontSize = 12.sp, color = Color.Gray)

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Product Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editOnHandStr,
                        onValueChange = { editOnHandStr = it },
                        label = { Text("Last On Hand Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Selector
                    ExposedDropdownMenuBox(
                        expanded = isCategoryExpanded,
                        onExpandedChange = { isCategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isCategoryExpanded,
                            onDismissRequest = { isCategoryExpanded = false }
                        ) {
                            DEFAULT_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        editCategory = cat
                                        editDaysStr = DateCalculator.getShelfLifeDaysForCategory(cat).toString()
                                        isCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit Selector
                    ExposedDropdownMenuBox(
                        expanded = isUnitExpanded,
                        onExpandedChange = { isUnitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit of Measure") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isUnitExpanded,
                            onDismissRequest = { isUnitExpanded = false }
                        ) {
                            listOf("EA", "LB", "CS", "CTN", "GAL", "BLK", "BOX", "PK").forEach { unitOpt ->
                                DropdownMenuItem(
                                    text = { Text(unitOpt) },
                                    onClick = {
                                        editUnit = unitOpt
                                        isUnitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editDaysStr,
                        onValueChange = { editDaysStr = it },
                        label = { Text("Default Shelf-Life Days (+3 to +14)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val daysInt = editDaysStr.toIntOrNull() ?: item.defaultShelfLifeDays
                    val parsedOnHand = editOnHandStr.toDoubleOrNull()
                    val onHandVal = if (parsedOnHand != null && parsedOnHand > 0.0) parsedOnHand else initialOnHand
                    val updatedItem = item.copy(
                        name = editName.ifBlank { item.name },
                        category = editCategory,
                        unit = editUnit,
                        defaultShelfLifeDays = DateCalculator.clampShelfLifeDays(daysInt),
                        lastOnHandAmount = onHandVal
                    )
                    onUpdate(updatedItem)
                    showEditDialog = false
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
