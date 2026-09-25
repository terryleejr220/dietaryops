package com.dietaryops.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.local.AppDatabase
import com.dietaryops.manager.data.local.CatalogItemDao
import com.dietaryops.manager.data.local.ExpirationRuleDao
import com.dietaryops.manager.data.local.ScanRecordDao
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ExpirationRule
import com.dietaryops.manager.data.repository.DeliveryRepository
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.ZplGenerator
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL

class DataAuditAndAnalysisTest {

    @Test
    fun executeDataAuditAndAnalysis() {
        setupMockAppDatabase()
        val dummyContext = createMockContext()
        val repository = DeliveryRepository(dummyContext)

        // Retrieve default catalog items via Reflection on field defaultCatalogItems
        val repositoryClass = DeliveryRepository::class.java
        val catalogField = repositoryClass.getDeclaredField("defaultCatalogItems")
        catalogField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val defaultCatalogItems = catalogField.get(repository) as List<CatalogItem>

        val expirationRulesField = repositoryClass.getDeclaredField("defaultExpirationRules")
        expirationRulesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val defaultExpirationRules = expirationRulesField.get(repository) as List<ExpirationRule>

        println("=== DIETARY OPS INVENTORY DATA AUDIT ===")
        println("Total Pre-Seeded Catalog Master Products: ${defaultCatalogItems.size}")

        // 1. Group by Category
        val categories = SettingsManager.DEFAULT_CATEGORIES
        val categoryCounts = defaultCatalogItems.groupBy { it.category }.mapValues { it.value.size }

        println("\n--- Master Catalog Products by Category ---")
        categories.forEach { cat ->
            val count = categoryCounts[cat] ?: 0
            println(" - $cat: $count products")
        }

        // Verify all items belong to one of the 8 canonical categories
        val nonCanonical = defaultCatalogItems.filter { it.category !in categories }
        assertTrue("All catalog items should be mapped to the 8 canonical categories", nonCanonical.isEmpty())

        // 2. Storage Locations & ServSafe Expiration Rules (+7d, +14d, +365d)
        println("\n--- Storage Locations & ServSafe Rule Mappings ---")
        val categoryStorageLocations = mapOf(
            "Dairy & Fresh" to Pair("COOLER", 7),
            "Proteins & Frozen" to Pair("FREEZER", 14),
            "Canned Goods" to Pair("DRY STORAGE", 365),
            "Dry Storage" to Pair("DRY STORAGE", 365),
            "Condiments & Sauces" to Pair("DRY STORAGE", 365),
            "Misc Dry & Cereal" to Pair("DRY STORAGE", 365),
            "Supplement" to Pair("DRY STORAGE", 365),
            "General" to Pair("DRY STORAGE", 365)
        )

        categoryStorageLocations.forEach { (cat, locationAndDays) ->
            val (location, days) = locationAndDays
            val ruleDays = DateCalculator.getShelfLifeDaysForCategory(cat)
            val zplLocation = ZplGenerator.getStorageLocationForCategory(cat)
            println(" - Category: '$cat' | Storage: $zplLocation | ServSafe Days: +${ruleDays}d")
            assertEquals(location, zplLocation)
            assertEquals(days, ruleDays)
        }

        // Verify default expiration rules count
        assertEquals(8, defaultExpirationRules.size)

        // 3. Multi-Vendor Coverage (Sysco SUPCs vs Piazza Item #s vs 12-digit UPCs)
        val upcCount = defaultCatalogItems.count { it.syscoUpc.length == 12 && it.syscoUpc.all { c -> c.isDigit() } }
        val syscoItemNumCount = defaultCatalogItems.count { it.syscoItemNumber.isNotBlank() }
        val piazzaItemNumCount = defaultCatalogItems.count { it.piazzaItemNumber.isNotBlank() }
        val vendorItemNumCount = defaultCatalogItems.count { it.vendorItemNumber.isNotBlank() }
        val altBarcodeCount = defaultCatalogItems.count { it.alternateBarcodes.isNotBlank() }

        println("\n--- Multi-Vendor Item Coverage ---")
        println(" - 12-digit UPCs (GTIN-12): $upcCount items")
        println(" - Sysco SUPCs (Sysco Item #s): $syscoItemNumCount items")
        println(" - 5-digit Piazza Item #s: $piazzaItemNumCount items")
        println(" - Vendor Item #s / SKUs: $vendorItemNumCount items")
        println(" - Alternate Barcodes indexed: $altBarcodeCount items")

        // 4. Verify Google Sheets Endpoints
        val webAppUrl = SettingsManager.DEFAULT_WEB_APP_URL
        val publishedUrl = SettingsManager.DEFAULT_PUBLISHED_WEB_URL

        println("\n--- Sync & Backend Endpoints ---")
        println(" - Apps Script Webhook URL: $webAppUrl")
        println(" - Published Sheet URL: $publishedUrl")
        println(" - Local Room DB: 'dietary_ops_database' with catalog_items, scan_records, expiration_rules")
        println(" - Cloud Firestore: 'catalog' and 'scan_records' collections")

        // 5. Test fetching CSV from Google Sheets backend if reachable
        try {
            val csvUrl = if (publishedUrl.contains("/pubhtml")) publishedUrl.replace("/pubhtml", "/pub?output=csv") else "$publishedUrl&output=csv"
            val conn = URL(csvUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true

            if (conn.responseCode in 200..399) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val csvContent = reader.readText()
                reader.close()
                val parsedDtos = DeliveryRepository.parseCatalogDtosFromCsvString(csvContent)
                println(" - Live Google Sheets Published CSV Endpoint reachable: Parsed ${parsedDtos.size} items!")
            } else {
                println(" - Live Google Sheets Endpoint returned HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            println(" - Live Google Sheets Endpoint fetch notice: ${e.message}")
        }

        // 6. Verify Build Artifacts (skipped if not yet compiled)
        val userDir = System.getProperty("user.dir") ?: "."
        val rootDir = File(userDir).canonicalFile.let { if (it.name == "app") it.parentFile else it }
        val debugApk = File(rootDir, "DietaryOpsManager.apk")
        val releaseApk = File(rootDir, "DietaryOpsManager-Release.apk")
        val releaseAab = File(rootDir, "DietaryOpsManager.aab")

        println("\n--- Build Artifacts Verification ---")
        println(" Root Directory: ${rootDir?.absolutePath}")
        println(" - DietaryOpsManager.apk exists: ${debugApk.exists()} (${debugApk.length()} bytes)")
        println(" - DietaryOpsManager-Release.apk exists: ${releaseApk.exists()} (${releaseApk.length()} bytes)")
        println(" - DietaryOpsManager.aab exists: ${releaseAab.exists()} (${releaseAab.length()} bytes)")

        assumeTrue("DietaryOpsManager.apk exists in root", debugApk.exists())
        assumeTrue("DietaryOpsManager-Release.apk exists in root", releaseApk.exists())
        assumeTrue("DietaryOpsManager.aab exists in root", releaseAab.exists())
    }

    private fun setupMockAppDatabase() {
        val dummyCatalogDao = Proxy.newProxyInstance(
            CatalogItemDao::class.java.classLoader,
            arrayOf(CatalogItemDao::class.java)
        ) { _, _, _ -> null } as CatalogItemDao

        val dummyScanRecordDao = Proxy.newProxyInstance(
            ScanRecordDao::class.java.classLoader,
            arrayOf(ScanRecordDao::class.java)
        ) { _, _, _ -> null } as ScanRecordDao

        val dummyExpirationRuleDao = Proxy.newProxyInstance(
            ExpirationRuleDao::class.java.classLoader,
            arrayOf(ExpirationRuleDao::class.java)
        ) { _, _, _ -> null } as ExpirationRuleDao

        val mockDb = object : AppDatabase() {
            override fun catalogItemDao(): CatalogItemDao = dummyCatalogDao
            override fun scanRecordDao(): ScanRecordDao = dummyScanRecordDao
            override fun expirationRuleDao(): ExpirationRuleDao = dummyExpirationRuleDao
            @Suppress("OVERRIDE_DEPRECATION")
            override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
                throw UnsupportedOperationException("Not implemented for mock DB")
            }
            override fun createInvalidationTracker(): InvalidationTracker {
                return Proxy.newProxyInstance(
                    InvalidationTracker::class.java.classLoader,
                    arrayOf(InvalidationTracker::class.java)
                ) { _, _, _ -> null } as InvalidationTracker
            }
            override fun clearAllTables() {}
        }

        val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, mockDb)
    }

    private fun createMockContext(): Context {
        val dummyPrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> args?.getOrNull(1) ?: ""
                "getBoolean" -> args?.getOrNull(1) ?: false
                "getInt" -> args?.getOrNull(1) ?: 0
                else -> null
            }
        } as SharedPreferences

        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = dummyPrefs
        }
    }
}
