package com.centuryvilla.deliveryscanner

import android.content.Context
import android.content.SharedPreferences
import com.centuryvilla.deliveryscanner.data.model.InventoryItem
import com.centuryvilla.deliveryscanner.data.model.SyscoProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ProductManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sysco_products", Context.MODE_PRIVATE)
    private val invPrefs: SharedPreferences = context.getSharedPreferences("cv_inventory", Context.MODE_PRIVATE)

    companion object {
        const val ADMIN_BADGE_CODE = "CV-ADMIN-AUTH"
        const val DEFAULT_PIN = "2200"
        const val SPREADSHEET_URL = "https://docs.google.com/spreadsheets/d/16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY/edit"
    }

    // Pre-seeded Sysco dietary items
    private val defaultProducts = mapOf(
        "074865123401" to SyscoProduct("074865123401", "Sysco Sliced Roast Beef", 5, 3, 4, "123401", "4 / 5# CS", "COOLER"),
        "074865123402" to SyscoProduct("074865123402", "Sysco Classic Liquid Eggs", 5, 3, 2, "123402", "2 / 20# CS", "COOLER"),
        "074865123403" to SyscoProduct("074865123403", "Sysco Imperial Cheddar Cheese", 14, 7, 4, "123403", "4 / 5# CS", "COOLER"),
        "074865123404" to SyscoProduct("074865123404", "Sysco Classic Romaine Lettuce", 4, 3, 1, "123404", "24# CS", "COOLER"),
        "074865123405" to SyscoProduct("074865123405", "Sysco Sliced Turkey Breast", 5, 3, 4, "123405", "4 / 5# CS", "COOLER"),
        "074865123406" to SyscoProduct("074865123406", "Sysco Heavy Whipping Cream", 10, 7, 12, "123406", "12 / 1 QT CS", "COOLER"),
        "074865123407" to SyscoProduct("074865123407", "Sysco White Whole Milk (Gallon)", 7, 7, 4, "123407", "4 / 1 GAL CS", "COOLER"),
        "074865123408" to SyscoProduct("074865123408", "Sysco Classic Tomato Sauce #10", 365, 5, 6, "123408", "6 / #10 CS (6 Cans/Case)", "DRY STORAGE"),
        "074865123409" to SyscoProduct("074865123409", "Sysco Diced Peaches #10", 365, 5, 6, "123409", "6 / #10 CS (6 Cans/Case)", "DRY STORAGE"),
        "074865123410" to SyscoProduct("074865123410", "Sysco Green Beans #10", 365, 5, 6, "123410", "6 / #10 CS (6 Cans/Case)", "DRY STORAGE")
    )

    fun normalizeBarcode(raw: String): String {
        val clean = raw.trim()
        if (clean.length == 14 && clean.all { it.isDigit() }) {
            val innerUpc = clean.substring(1, 13)
            if (prefs.contains(innerUpc) || defaultProducts.containsKey(innerUpc)) {
                return innerUpc
            }
        }
        return clean
    }

    fun getProduct(upc: String): SyscoProduct {
        val cleanUpc = normalizeBarcode(upc)
        val customJson = prefs.getString(cleanUpc, null)
        if (customJson != null) {
            val json = JSONObject(customJson)
            return SyscoProduct(
                upc = cleanUpc,
                name = json.getString("name"),
                shelfLifeDays = json.getInt("shelfLifeDays"),
                openedShelfLifeDays = json.optInt("openedShelfLifeDays", 7),
                packSize = json.optInt("packSize", 1),
                itemNumber = json.optString("itemNumber", ""),
                packInfo = json.optString("packInfo", ""),
                location = json.optString("location", "COOLER")
            )
        }
        val match = defaultProducts[cleanUpc] ?: defaultProducts.values.firstOrNull { it.itemNumber == cleanUpc }
        return match ?: SyscoProduct(
            upc = cleanUpc,
            name = "Sysco Item ($cleanUpc)",
            shelfLifeDays = 7,
            openedShelfLifeDays = 7,
            packSize = 1
        )
    }

    fun saveProduct(product: SyscoProduct) {
        val json = JSONObject().apply {
            put("name", product.name)
            put("shelfLifeDays", product.shelfLifeDays)
            put("openedShelfLifeDays", product.openedShelfLifeDays)
            put("packSize", product.packSize)
            put("itemNumber", product.itemNumber)
            put("packInfo", product.packInfo)
            put("location", product.location)
        }
        prefs.edit().putString(product.upc.trim(), json.toString()).apply()
    }

    fun searchProducts(query: String): List<SyscoProduct> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()

        val allProducts = mutableListOf<SyscoProduct>()
        allProducts.addAll(defaultProducts.values)

        for (key in prefs.all.keys) {
            if (key == "admin_pin" || key == "webhook_url") continue
            val jsonStr = prefs.getString(key, null) ?: continue
            try {
                val json = JSONObject(jsonStr)
                allProducts.add(
                    SyscoProduct(
                        upc = key,
                        name = json.getString("name"),
                        shelfLifeDays = json.getInt("shelfLifeDays"),
                        openedShelfLifeDays = json.optInt("openedShelfLifeDays", 7),
                        packSize = json.optInt("packSize", 1),
                        itemNumber = json.optString("itemNumber", ""),
                        packInfo = json.optString("packInfo", ""),
                        location = json.optString("location", "COOLER")
                    )
                )
            } catch (e: Exception) {
                // ignore invalid entries
            }
        }

        return allProducts.distinctBy { it.upc }.filter { prod ->
            prod.name.lowercase().contains(q) ||
            prod.upc.lowercase().contains(q) ||
            prod.itemNumber.lowercase().contains(q)
        }
    }

    fun getAdminPin(): String {
        return prefs.getString("admin_pin", DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun setAdminPin(pin: String) {
        prefs.edit().putString("admin_pin", pin.trim()).apply()
    }

    fun verifyPin(input: String): Boolean {
        return input.trim() == getAdminPin()
    }

    fun getWebhookUrl(): String {
        return prefs.getString("webhook_url", "") ?: ""
    }

    fun setWebhookUrl(url: String) {
        prefs.edit().putString("webhook_url", url.trim()).apply()
    }

    // --- Inventory Section Data Management ---
    private val baselineItems = listOf(
        // Canned Goods
        InventoryItem("Canned Goods", "V8", "23", "0"),
        InventoryItem("Canned Goods", "Chicken Noodle", "0", "4"),
        InventoryItem("Canned Goods", "Tomato Soup", "0", "0"),
        InventoryItem("Canned Goods", "Cream of Mushroom", "6", "8"),
        InventoryItem("Canned Goods", "Ketchup", "0", "0"),
        InventoryItem("Canned Goods", "RG Whole Toms", "6", "6"),
        InventoryItem("Canned Goods", "RG Diced", "3", "7"),
        InventoryItem("Canned Goods", "RG Pizza Sauce", "6", "3"),
        InventoryItem("Canned Goods", "RG Marinara", "3", "6"),
        InventoryItem("Canned Goods", "Green Beans", "1", "7"),
        InventoryItem("Canned Goods", "Corn", "5", "0"),
        InventoryItem("Canned Goods", "3 Bean Salad", "4", "7"),
        InventoryItem("Canned Goods", "Baked Beans", "3", "9"),
        InventoryItem("Canned Goods", "Kidney Beans", "1", "1"),
        InventoryItem("Canned Goods", "Northern Beans", "3", "4"),
        InventoryItem("Canned Goods", "Spinach", "0", "5"),
        InventoryItem("Canned Goods", "Cherry Pie Filling", "0", "0"),
        InventoryItem("Canned Goods", "Apple Pie Filling", "0", "0"),
        InventoryItem("Canned Goods", "Strawberry Filling", "2", "4"),
        InventoryItem("Canned Goods", "Applesauce", "6", "3"),
        InventoryItem("Canned Goods", "Pineapple Tidbits", "3", "9"),
        InventoryItem("Canned Goods", "Butterscotch Pudding", "4", "5"),
        InventoryItem("Canned Goods", "Banana Pudding", "3", "5"),
        InventoryItem("Canned Goods", "Diced Pears", "3", "3"),
        InventoryItem("Canned Goods", "Diced Peaches", "0", "8"),
        InventoryItem("Canned Goods", "Diced Fruit", "6", "5"),
        InventoryItem("Canned Goods", "Tropical Fruit Mix", "4", "4"),
        InventoryItem("Canned Goods", "Fruit Cocktail", "4", "1"),
        InventoryItem("Canned Goods", "Cranberry Sauce", "1", "1"),
        InventoryItem("Canned Goods", "Mandarin Oranges", "8", "4"),
        InventoryItem("Canned Goods", "Pear Halves", "5", "5"),
        InventoryItem("Canned Goods", "Beets", "2", "5"),
        InventoryItem("Canned Goods", "Cheddar Cheese", "6", "0"),
        InventoryItem("Canned Goods", "Sauerkraut", "6", "6"),
        InventoryItem("Canned Goods", "Sweet Potatoes", "0", "2"),
        InventoryItem("Canned Goods", "Refried Beans", "3", "0", true),
        InventoryItem("Canned Goods", "Black Beans", "1/2 box", "0", true),
        InventoryItem("Canned Goods", "Clam Chowder", "6", "6"),
        InventoryItem("Canned Goods", "Lentil Soup", "3", "3", true),
        InventoryItem("Canned Goods", "Risotto", "1 b", "2 b", true),

        // Dry Storage
        InventoryItem("Dry Storage", "Brown Sugar", "1/4 c", "1 c"),
        InventoryItem("Dry Storage", "Salt", "1 c", "1 c"),
        InventoryItem("Dry Storage", "Lady Fingers", "4 b", "1/2 c"),
        InventoryItem("Dry Storage", "Jiffy Corn", "3 b", "3 b"),
        InventoryItem("Dry Storage", "Muffin Mix", "3 1/2 b", "4 b"),
        InventoryItem("Dry Storage", "Yellow Cake", "3", "4"),
        InventoryItem("Dry Storage", "Devil Food Cake", "3", "3"),
        InventoryItem("Dry Storage", "Brownie", "3", "4"),
        InventoryItem("Dry Storage", "White Cake", "5", "5"),
        InventoryItem("Dry Storage", "Fryer Shortening", "3/4", "1"),
        InventoryItem("Dry Storage", "Veg Oil", "1/2 j", "3/4 j"),
        InventoryItem("Dry Storage", "Confection Sugar", "7#", "8#"),
        InventoryItem("Dry Storage", "Corn Starch", "1 c", "1.25 c"),
        InventoryItem("Dry Storage", "Scallop", "1 b", "3 b"),
        InventoryItem("Dry Storage", "Par Boil Rice", "1/4 c", "1/4 c"),
        InventoryItem("Dry Storage", "Long Grain Rice", "6 b", "6"),
        InventoryItem("Dry Storage", "Rice Pilaf", "0", "0"),
        InventoryItem("Dry Storage", "Spanish Rice", "5", "5"),
        InventoryItem("Dry Storage", "Penne Pasta", "1/4", "1/2"),
        InventoryItem("Dry Storage", "Rotini", "1 b", "1"),
        InventoryItem("Dry Storage", "Elbow", "0", "1"),
        InventoryItem("Dry Storage", "Egg Noodles Dry", "2 b", "1"),
        InventoryItem("Dry Storage", "Spaghetti", "1.5 b", "1/2 b"),
        InventoryItem("Dry Storage", "Lasagna", "13", "13 b"),
        InventoryItem("Dry Storage", "Mash Potato", "6 b", "6 b"),
        InventoryItem("Dry Storage", "All Purpose Spray", "1 c", "1 c"),
        InventoryItem("Dry Storage", "Peanut Butter", "3 con", "1 c"),
        InventoryItem("Dry Storage", "Gherkin Jar", "1 j", "1 j"),
        InventoryItem("Dry Storage", "Relish", "1/2", "0"),
        InventoryItem("Dry Storage", "Alfredo", "3/4 c", "1 c"),
        InventoryItem("Dry Storage", "Turkey Gravy", "1/4 b", "1"),
        InventoryItem("Dry Storage", "Pepper Gravy", "2.5 b", "1"),
        InventoryItem("Dry Storage", "Beef Gravy", "2 b", "1"),
        InventoryItem("Dry Storage", "Chicken Gravy", "5 b", "1"),
        InventoryItem("Dry Storage", "Pork Roast", "1.5 b", "1/2 c"),
        InventoryItem("Dry Storage", "Honey Packets", "1/2 c", "1/2 c"),
        InventoryItem("Dry Storage", "Bread Crumbs", "1/4 c", "1/4 c"),

        // Thickened Beverages
        InventoryItem("Thickened Beverages", "HT OJ", "5", "0", true),
        InventoryItem("Thickened Beverages", "HT Apple", "0", "0", true),
        InventoryItem("Thickened Beverages", "HT Water", "2", "4", true),
        InventoryItem("Thickened Beverages", "HT Cran", "4", "0", true),
        InventoryItem("Thickened Beverages", "NT Water", "6", "6", true),
        InventoryItem("Thickened Beverages", "NT OJ", "4", "4", true),
        InventoryItem("Thickened Beverages", "NT Apple", "16", "16", true),
        InventoryItem("Thickened Beverages", "NT Tea", "5", "5", true),
        InventoryItem("Thickened Beverages", "NT Cran", "5", "6", true),

        // Proteins & Frozen
        InventoryItem("Proteins & Frozen", "Pork Loin CC BL", "5", "5#"),
        InventoryItem("Proteins & Frozen", "Potato Pollock", "2", "1 c"),
        InventoryItem("Proteins & Frozen", "Cordon Bleu", "0", "0"),
        InventoryItem("Proteins & Frozen", "Egg Patties", "0", "2 c"),
        InventoryItem("Proteins & Frozen", "Scramble Egg (Omelete)", "0", "3/4 c"),
        InventoryItem("Proteins & Frozen", "Potato Wedges", "1/2 c", "1.5 c"),
        InventoryItem("Proteins & Frozen", "Sausage Links", "1 c", "1/4 c"),
        InventoryItem("Proteins & Frozen", "Beef Franks", "0", "1 c"),
        InventoryItem("Proteins & Frozen", "Peach Cobbler", "0", "0"),
        InventoryItem("Proteins & Frozen", "Mac n Cheese", "0", "0"),
        InventoryItem("Proteins & Frozen", "Italian Veg Blend", "1/2 c", "1 b"),
        InventoryItem("Proteins & Frozen", "Bacon", "1", "1 c"),
        InventoryItem("Proteins & Frozen", "French Toast", "1", "0"),
        InventoryItem("Proteins & Frozen", "Bulk Sausage Roll", "1 c", "1 c"),
        InventoryItem("Proteins & Frozen", "Veg Sausage Patty", "1 c", "0"),
        InventoryItem("Proteins & Frozen", "Hamburger Patty", "0", "1/2 c"),
        InventoryItem("Proteins & Frozen", "Vanilla Ice Cream", "2", "3"),
        InventoryItem("Proteins & Frozen", "Fried Chk", "3 c", "1/4"),
        InventoryItem("Proteins & Frozen", "Polish SSG", "1 c", "2 c"),
        InventoryItem("Proteins & Frozen", "Pancakes", "2 c", "3"),
        InventoryItem("Proteins & Frozen", "Ground Beef 10#", "10#", "20"),
        InventoryItem("Proteins & Frozen", "Boneless Chick Breast", "2 c", "3 c"),
        InventoryItem("Proteins & Frozen", "Diced Chicken", "1 c", "0"),
        InventoryItem("Proteins & Frozen", "Turkey (Whole)", "2", "3"),
        InventoryItem("Proteins & Frozen", "California Blend", "1", "0", true),
        InventoryItem("Proteins & Frozen", "Cajun Blend", "1", "0", true),
        InventoryItem("Proteins & Frozen", "Broccoli Florets", "1/2", "0", true),
        InventoryItem("Proteins & Frozen", "Lima Beans", "1/2", "1 c", true),
        InventoryItem("Proteins & Frozen", "Frozen Green Beans", "1/2 c", "1/2 c", true),
        InventoryItem("Proteins & Frozen", "Stir Fry", "1 b", "0", true),
        InventoryItem("Proteins & Frozen", "Italian Blend", "3 b", "0", true),
        InventoryItem("Proteins & Frozen", "Onion / Peppers", "4 b", "3 b", true),
        InventoryItem("Proteins & Frozen", "Bahama Blend", "1", "0", true)
    )

    fun getInventoryList(): List<InventoryItem> {
        val list = mutableListOf<InventoryItem>()
        for (item in baselineItems) {
            val savedCount = invPrefs.getString(item.name, null)
            list.add(
                InventoryItem(
                    category = item.category,
                    name = item.name,
                    onHand = savedCount ?: item.onHand,
                    previousCount = item.previousCount,
                    isNew = item.isNew
                )
            )
        }
        return list
    }

    fun updateCount(name: String, newCount: String) {
        invPrefs.edit().putString(name, newCount.trim()).apply()
    }

    /**
     * Real-time sync to Google Sheets via Apps Script Webhook using Coroutines.
     * Pushes all updated items or single item to the spreadsheet in real time.
     */
    suspend fun syncToGoogleSheetsAsync(items: List<InventoryItem>): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val webhook = getWebhookUrl()
            if (webhook.isBlank()) {
                return@withContext Pair(false, "No Google Sheets Webhook configured in Admin.")
            }

            try {
                val url = URL(webhook)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val jsonArray = JSONArray()
                for (item in items) {
                    val obj = JSONObject().apply {
                        put("name", item.name)
                        put("category", item.category)
                        put("onHand", item.onHand)
                    }
                    jsonArray.put(obj)
                }

                val payload = JSONObject().apply {
                    put("action", "update_inventory")
                    put("items", jsonArray)
                }.toString()

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload)
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    Pair(true, "Synced ${items.size} items to Google Sheets live!")
                } else {
                    Pair(false, "Sync failed (HTTP $responseCode)")
                }
            } catch (e: Exception) {
                Pair(false, e.localizedMessage ?: "Network connection error")
            }
        }

    fun syncToGoogleSheets(
        items: List<InventoryItem>,
        onResult: (Boolean, String) -> Unit
    ) {
        val webhook = getWebhookUrl()
        if (webhook.isBlank()) {
            onResult(false, "No Google Sheets Webhook configured in Admin.")
            return
        }

        Thread {
            try {
                val url = URL(webhook)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val jsonArray = JSONArray()
                for (item in items) {
                    val obj = JSONObject().apply {
                        put("name", item.name)
                        put("category", item.category)
                        put("onHand", item.onHand)
                    }
                    jsonArray.put(obj)
                }

                val payload = JSONObject().apply {
                    put("action", "update_inventory")
                    put("items", jsonArray)
                }.toString()

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload)
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    onResult(true, "Synced ${items.size} items to Google Sheets live!")
                } else {
                    onResult(false, "Sync failed (HTTP $responseCode)")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Network connection error")
            }
        }.start()
    }
}
