package com.dietaryops.manager.data.repository

import android.content.Context
import com.dietaryops.manager.ZplGenerator
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.local.AppDatabase
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ExpirationRule
import com.dietaryops.manager.data.model.ScanRecord
import com.dietaryops.manager.data.remote.CatalogSyncDto
import com.dietaryops.manager.data.remote.FirestoreRepository
import com.dietaryops.manager.data.remote.GoogleSheetsApiService
import com.dietaryops.manager.data.remote.SheetScanRecordDto
import com.dietaryops.manager.data.remote.SheetSyncPayload
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.util.ErrorLogger
import com.dietaryops.manager.util.SyscoUpcNormalizer
import com.dietaryops.manager.util.toTitleCase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import kotlin.math.abs

class DeliveryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val settingsManager = SettingsManager(appContext)
    private val db = AppDatabase.getDatabase(appContext)
    private val catalogDao = db.catalogItemDao()
    private val scanRecordDao = db.scanRecordDao()
    private val expirationRuleDao = db.expirationRuleDao()
    val firestoreRepository = FirestoreRepository()

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://script.google.com/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val sheetsApiService = retrofit.create(GoogleSheetsApiService::class.java)

    // Complete Pre-seeded Catalog List matching inventory Google Sheet linkable categories
    private val defaultCatalogItems = listOf(
        // Low Inventory Dry Storage -> Category: Dry Storage
        CatalogItem("074861000001", "Brown Sugar", "Dry Storage", 365, "CS"),
        CatalogItem("074861000002", "Salt", "Dry Storage", 365, "CS"),
        CatalogItem("074861000003", "Lady Fingers", "Dry Storage", 365, "CS"),
        CatalogItem("074861000004", "Jiffy Corn", "Dry Storage", 365, "CS"),
        CatalogItem("074861000005", "Muffin Mix", "Dry Storage", 365, "CS"),
        CatalogItem("074861000006", "Yellow Cake", "Dry Storage", 365, "CS"),
        CatalogItem("074861000007", "Devil Food Cake", "Dry Storage", 365, "CS"),
        CatalogItem("074861000008", "Brownie", "Dry Storage", 365, "CS"),
        CatalogItem("074861000009", "White Cake", "Dry Storage", 365, "CS"),
        CatalogItem("074861000010", "Fryer Shortening", "Dry Storage", 365, "EA"),
        CatalogItem("074861000011", "Veg Oil", "Dry Storage", 365, "CS"),
        CatalogItem("074861000012", "Confection Sugar", "Dry Storage", 365, "EA"),
        CatalogItem("074861000013", "Corn Starch", "Dry Storage", 365, "CS"),
        CatalogItem("074861000014", "Scallop", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074861000015", "Par Boil Rice", "Dry Storage", 365, "CS"),
        CatalogItem("074861000016", "Long Grain Rice", "Dry Storage", 365, "CS"),
        CatalogItem("074861000017", "Spanish Rice", "Dry Storage", 365, "CS"),
        CatalogItem("074861000018", "Penne Pasta", "Dry Storage", 365, "CS"),
        CatalogItem("074861000019", "Rotini", "Dry Storage", 365, "CS"),
        CatalogItem("074861000020", "Elbow Pasta", "Dry Storage", 365, "CS"),
        CatalogItem("074861000021", "Egg Noodles Dry", "Dry Storage", 365, "CS"),
        CatalogItem("074861000022", "Spaghetti", "Dry Storage", 365, "CS"),
        CatalogItem("074861000023", "Lasagna", "Dry Storage", 365, "CS"),
        CatalogItem("074861000024", "Mash Potato", "Dry Storage", 365, "CS"),
        CatalogItem("074861000025", "All Purpose Spray", "General", 14, "CS"),
        CatalogItem("074861000026", "Peanut Butter", "Dry Storage", 365, "CS"),
        CatalogItem("074861000027", "Gherkin Jar", "Dry Storage", 365, "EA"),
        CatalogItem("074861000028", "Relish", "Dry Storage", 365, "EA"),
        CatalogItem("074861000029", "Turkey Gravy", "Dry Storage", 365, "CS"),
        CatalogItem("074861000030", "Pepper Gravy", "Dry Storage", 365, "CS"),
        CatalogItem("074861000031", "Beef Gravy", "Dry Storage", 365, "CS"),
        CatalogItem("074861000032", "Chicken Gravy", "Dry Storage", 365, "CS"),
        CatalogItem("074861000033", "Pork Roast", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074861000034", "Honey Packets", "Dry Storage", 365, "CS"),
        CatalogItem("074861000035", "Rice Pilaf", "Dry Storage", 365, "CS"),
        CatalogItem("074861000036", "Alfredo Sauce", "Dry Storage", 365, "CS"),
        CatalogItem("074861000037", "Bread Crumbs", "Dry Storage", 365, "CS"),

        // Housekeeping / EVS
        CatalogItem("074863000001", "Vinyl Gloves Large", "General", 365, "CS"),
        CatalogItem("074863000002", "Depends Adult Briefs L", "General", 365, "CS"),
        CatalogItem("074863000003", "Toilet Bowl Cleaner", "General", 365, "CS"),
        CatalogItem("074863000004", "Clorox Bleach", "General", 365, "EA"),
        CatalogItem("074863000005", "Paper Towels Roll", "General", 365, "CS"),
        CatalogItem("074863000006", "Trash Bags 33 Gal", "General", 365, "CS"),

        // Proteins & Frozen
        CatalogItem("074862000001", "Pork Loin CC BL", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000002", "Mascarpone", "Dairy & Fresh", 7, "EA"),
        CatalogItem("074862000003", "Potato Pollock", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000004", "Chicken Cordon Bleu", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000005", "Egg Patties", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000006", "Scrambled Eggs", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000007", "Potato Wedges", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000008", "Sausage Links", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000009", "Beef Franks", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000010", "Peach Cobbler", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000011", "Mac n Cheese", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000012", "Italian Veg Blend", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000013", "Bacon", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000014", "French Toast", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000015", "Bulk Sausage Roll", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000016", "Veg Sausage Patty", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000017", "Hamburger Patty", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000018", "Strawberry Ice Cream", "Proteins & Frozen", 14, "CTN"),
        CatalogItem("074862000019", "Vanilla Ice Cream", "Proteins & Frozen", 14, "CTN"),
        CatalogItem("074862000020", "Onion Pearls", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000021", "Cheese Tortellini", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000022", "Texas Toast", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000023", "Brussels Sprouts", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000024", "Chocolate Ice Cream", "Proteins & Frozen", 14, "CTN"),
        CatalogItem("074862000025", "Fried Chicken", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000026", "Polish Sausage", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000027", "Grape Tomatoes", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000028", "Lettuce", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000029", "Baby Carrots", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000030", "Apple Sauce Berry", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000031", "Green Grapes", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000032", "Fresh Blueberries", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000033", "Celery", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000034", "American Cheese", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074862000035", "Hash Rounds", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000036", "Pancakes", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000037", "Waffles", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000038", "Ground Beef 10#", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000039", "Boneless Chicken Breast", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000040", "Diced Chicken", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000041", "Apple Crisp", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000042", "Magic Cup (Choc.)", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000043", "Magic Cup (Berry)", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000044", "Buffet Ham", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000045", "Whole Turkey", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000046", "California Blend Veg", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000047", "Cajun Blend Veg", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000048", "Broccoli Florets", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000049", "Lima Beans", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000050", "Green Beans", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000051", "Stir Fry Veg", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000052", "Italian Blend Veg", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000053", "Onion / Peppers", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000054", "Chef Blend Veg", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000055", "Bahama Blend Veg", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074862000056", "5 Way Blend Veg", "Proteins & Frozen", 14, "CS"),

        // Condiments & Sauces
        CatalogItem("074863000001", "Mayonnaise", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000002", "Tartar Sauce", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000003", "Mustard", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000004", "Ketchup", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000005", "Malt Vinegar", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000006", "Soy Sauce", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000007", "Franks Hot Sauce", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000008", "Sugar", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000009", "Yellow Sucralose", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000010", "Pink Sugar Sweetener", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000011", "Coffee Creamer", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074863000012", "Pepper", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000013", "Assorted Jelly", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000014", "Gallon Tea", "Supplement", 365, "CS"),
        CatalogItem("074863000015", "Hot Bags", "General", 14, "BOX"),
        CatalogItem("074863000016", "Coffee", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000017", "No Sugar Hot Cocoa", "Condiments & Sauces", 14, "BOX"),
        CatalogItem("074863000018", "Instant Coffee", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000019", "Creamy Hot Chocolate", "Condiments & Sauces", 14, "BOX"),
        CatalogItem("074863000020", "Chicken Bouillon", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000021", "Ranch Dips", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000022", "Ranch Mix", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000023", "Golden Italian Dressing", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000024", "French Dressing Pack", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000025", "Ranch Dressing Pack", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000026", "Vanilla Pudding", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000027", "Chocolate Pudding", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000028", "BBQ Sauce", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000029", "Worcestershire Sauce", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000030", "Salsa", "Condiments & Sauces", 14, "CTN"),
        CatalogItem("074863000031", "Sweet & Sour Sauce", "Condiments & Sauces", 14, "CTN"),
        CatalogItem("074863000032", "Mayonnaise Gallon", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074863000033", "Chicken Soup Can", "Canned Goods", 365, "CS"),
        CatalogItem("074863000034", "Tomato Soup Can", "Canned Goods", 365, "CS"),
        CatalogItem("074863000035", "Vegetable Soup Can", "Canned Goods", 365, "CS"),
        CatalogItem("074863000036", "Green Chilis", "Canned Goods", 365, "CS"),
        CatalogItem("074863000037", "Red Pepper Can", "Canned Goods", 365, "CS"),
        CatalogItem("074863000038", "Oatmeal", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074863000039", "Pistachio Pudding", "Condiments & Sauces", 14, "CS"),

        // Dairy & Fresh
        CatalogItem("074864000001", "Cheddar Jack Cheese", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000002", "Shredded Parmesan", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000003", "Whole Milk (Gallon)", "Dairy & Fresh", 7, "GAL"),
        CatalogItem("074864000004", "Frozen Peas", "Proteins & Frozen", 14, "CS"),
        CatalogItem("074864000005", "Peach Yogurt", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000006", "Shredded Mozzarella", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000007", "Sour Cream Packets", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000008", "Butter Portions", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000009", "Cream Cheese Packets", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000010", "Apple Slices Fresh", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000011", "Broccolini", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000012", "Zucchini", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000013", "Diced Potato", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000014", "Potato Slices", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000015", "Heavy Whipping Cream", "Dairy & Fresh", 7, "CTN"),
        CatalogItem("074864000016", "Butter Milk", "Dairy & Fresh", 7, "CTN"),
        CatalogItem("074864000017", "Beef Base", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074864000018", "Demi Glace", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074864000019", "Sour Cream 5# Tub", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000020", "Unsalted Butter", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000021", "Salted Butter", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000022", "Orange Juice Concentrate", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000023", "Cranberry Juice Concentrate", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000024", "Apple Juice Concentrate", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000025", "Green Bell Peppers", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000026", "Fresh Lemons", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000027", "Red Onions", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000028", "Garlic In Water", "Dairy & Fresh", 7, "EA"),
        CatalogItem("074864000029", "Yellow Squash", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000030", "Liquid Eggs Carton", "Dairy & Fresh", 7, "CTN"),
        CatalogItem("074864000031", "Hard Boiled Eggs", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000032", "Whole Mushrooms", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000033", "Lemonade Gallon", "Supplement", 365, "CS"),
        CatalogItem("074864000034", "Cottage Cheese 5# Tub", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074864000035", "Honeydew Melon", "Dairy & Fresh", 7, "CS"),

        // Misc Dry & Cereal
        CatalogItem("074865000001", "Bananas", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074865000002", "Prune Juice", "Supplement", 365, "CS"),
        CatalogItem("074865000003", "Lemon Juice", "Supplement", 365, "CS"),
        CatalogItem("074865000004", "Coconut Milk", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074865000005", "Strawberry Gelatin", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000006", "Potato Chips Big Bag", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000007", "Croutons", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000008", "Grits", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000009", "White Wine Cooking", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000010", "Cheerios Cereal", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000011", "Raisin Bran Cereal", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000012", "Cinnamon Ground", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000013", "Sugar Free Syrup", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000014", "Cream of Wheat", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000015", "Pancake Syrup", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000016", "Yellow Onions 50#", "Dry Storage", 365, "CS"),
        CatalogItem("074865000017", "Red Potatoes 50#", "Dry Storage", 365, "CS"),
        CatalogItem("074865000018", "Idaho Potatoes 100 ct", "Dry Storage", 365, "CS"),
        CatalogItem("074865000019", "Tuna Pack", "Canned Goods", 365, "CS"),
        CatalogItem("074865000020", "Cocktail Sauce", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074865000021", "Marinara Packets", "Condiments & Sauces", 14, "CS"),
        CatalogItem("074865000022", "Citrus Gelatin", "Misc Dry & Cereal", 14, "CS"),
        CatalogItem("074865000023", "Rice Krispies Cereal", "Misc Dry & Cereal", 14, "CS"),

        // Canned Goods
        CatalogItem("074866000001", "Whole Kernel Corn Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000002", "Marinara Sauce Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000003", "Baked Beans Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000004", "Green Beans Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000005", "Cheddar Cheese Can", "Dairy & Fresh", 7, "CS"),
        CatalogItem("074866000006", "Whole Tomatoes Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000007", "Diced Tomatoes Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000008", "Pizza Sauce Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000009", "Northern Beans Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000010", "3 Bean Salad Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000011", "Diced Peaches Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000012", "Diced Pears Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000013", "Mandarin Oranges Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000014", "Pineapple Tidbits Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000015", "Fruit Cocktail Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000016", "Banana Pudding Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000017", "Diced Fruit Mix Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000018", "Cranberry Sauce Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000019", "Spinach Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000020", "Kidney Beans Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000021", "Clam Chowder Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000022", "Sauerkraut Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000023", "Applesauce Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000024", "Diced Beets Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000025", "Cream of Mushroom Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000026", "Strawberry Filling Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000027", "Butterscotch Pudding Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000028", "Pear Halves Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000029", "Refried Beans Can", "Canned Goods", 365, "CS"),
        CatalogItem("074866000030", "Lentil Soup Can", "Canned Goods", 365, "CS"),

        // Thickened Beverages (Dysphagia) / Supplement
        CatalogItem("074867000001", "Honey Thick OJ", "Supplement", 365, "CS"),
        CatalogItem("074867000002", "Honey Thick Apple Juice", "Supplement", 365, "CS"),
        CatalogItem("074867000003", "Honey Thick Water", "Supplement", 365, "CS"),
        CatalogItem("074867000004", "Honey Thick Cranberry", "Supplement", 365, "CS"),
        CatalogItem("074867000005", "Nectar Thick Water", "Supplement", 365, "CS"),
        CatalogItem("074867000006", "Nectar Thick OJ", "Supplement", 365, "CS"),
        CatalogItem("074867000007", "Nectar Thick Apple Juice", "Supplement", 365, "CS"),
        CatalogItem("074867000008", "Nectar Thick Tea", "Supplement", 365, "CS"),
        CatalogItem("074867000009", "Nectar Thick Cranberry", "Supplement", 365, "CS")
    )

    private val defaultExpirationRules = listOf(
        ExpirationRule("Canned Goods", 365, "Commercial Shelf-Stable / Unopened canned goods (#10 cans, applesauce, marinara)"),
        ExpirationRule("Dry Storage", 365, "Commercial Shelf-Stable / Unopened dry goods & bulk root vegetables"),
        ExpirationRule("Condiments & Sauces", 365, "Commercial Shelf-Stable / Unopened condiment pails/jugs & sauces"),
        ExpirationRule("Misc Dry & Cereal", 365, "Commercial Shelf-Stable / Unopened misc dry goods & cereals"),
        ExpirationRule("Dairy & Fresh", 7, "Cooler / TCS / Fresh: sliced deli meats, liquid eggs, shredded cheeses, fluid milk, fresh produce"),
        ExpirationRule("Proteins & Frozen", 14, "Freezer: Frozen raw/prepared proteins (JUST Egg patties, sausage links)"),
        ExpirationRule("Supplement", 365, "Commercial Shelf-Stable / Unopened dietary supplements & beverages"),
        ExpirationRule("General", 365, "Commercial Shelf-Stable / Unopened stock standard")
    )

    private val okHttpClient = OkHttpClient.Builder().build()

    private fun getPublishedCsvUrl(rawUrl: String): String {
        val clean = rawUrl.trim()
        return if (clean.contains("/pubhtml")) {
            val base = clean.replace("/pubhtml", "/pub")
            if (base.contains("output=csv")) base else "$base&output=csv"
        } else if (clean.contains("/pub?") && !clean.contains("output=csv")) {
            "$clean&output=csv"
        } else {
            clean
        }
    }

    companion object {
        private val gson = Gson()

        fun normalizeCategory(rawCategory: String?): String {
            if (rawCategory.isNullOrBlank()) return "General"
            val trimmed = rawCategory.trim()

            val exactMatch = SettingsManager.DEFAULT_CATEGORIES.firstOrNull {
                it.equals(trimmed, ignoreCase = true)
            }
            if (exactMatch != null) return exactMatch

            val normalizedAmpersand = trimmed.replace(" and ", " & ", ignoreCase = true)
            val ampersandMatch = SettingsManager.DEFAULT_CATEGORIES.firstOrNull {
                it.equals(normalizedAmpersand, ignoreCase = true)
            }
            if (ampersandMatch != null) return ampersandMatch

            val lower = trimmed.lowercase()
            return when {
                lower.contains("protein") || lower.contains("frozen") || lower.contains("meat") -> "Proteins & Frozen"
                lower.contains("dairy") || lower.contains("fresh") -> "Dairy & Fresh"
                lower.contains("condiment") || lower.contains("sauce") -> "Condiments & Sauces"
                lower.contains("cereal") || lower.contains("misc dry") -> "Misc Dry & Cereal"
                lower.contains("canned") -> "Canned Goods"
                lower.contains("dry storage") || lower.contains("dry") -> "Dry Storage"
                lower.contains("supplement") || lower.contains("beverage") || lower.contains("thickened") || lower.contains("dysphagia") -> "Supplement"
                lower.contains("general") -> "General"
                else -> trimmed
            }
        }

        fun parseCatalogDtosFromRawString(bodyStr: String): List<CatalogSyncDto> {
            val cleanStr = bodyStr.trim()
            if (cleanStr.isBlank()) return emptyList()

            val list = mutableListOf<CatalogSyncDto>()

            try {
                val element = JsonParser.parseString(cleanStr)
                if (element.isJsonArray) {
                    val arr = element.asJsonArray
                    for (item in arr) {
                        if (item.isJsonObject) {
                            val obj = item.asJsonObject
                            val dto = parseCatalogDtoFromJson(obj)
                            if (dto != null && (!dto.syscoUpc.isNullOrBlank() || !dto.name.isNullOrBlank() || !dto.syscoItemNumber.isNullOrBlank() || !dto.piazzaItemNumber.isNullOrBlank() || !dto.vendorItemNumber.isNullOrBlank() || !dto.vendorSku.isNullOrBlank() || !dto.alternateBarcodes.isNullOrBlank())) {
                                list.add(dto)
                            }
                        }
                    }
                    if (list.isNotEmpty()) return list
                } else if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    val keys = listOf("catalog", "items", "data", "products", "records", "list")
                    for (key in keys) {
                        val jsonElem = obj.get(key)
                        if (jsonElem != null && jsonElem.isJsonArray) {
                            val arr = jsonElem.asJsonArray
                            for (item in arr) {
                                if (item.isJsonObject) {
                                    val subObj = item.asJsonObject
                                    val dto = parseCatalogDtoFromJson(subObj)
                                    if (dto != null && (!dto.syscoUpc.isNullOrBlank() || !dto.name.isNullOrBlank() || !dto.syscoItemNumber.isNullOrBlank() || !dto.piazzaItemNumber.isNullOrBlank() || !dto.vendorItemNumber.isNullOrBlank() || !dto.vendorSku.isNullOrBlank() || !dto.alternateBarcodes.isNullOrBlank())) {
                                        list.add(dto)
                                    }
                                }
                            }
                            if (list.isNotEmpty()) return list
                        }
                    }
                }
            } catch (_: Exception) {
            }

            return parseCatalogDtosFromCsvString(cleanStr)
        }

        fun isValidOnHandHeader(header: String): Boolean {
            val lower = header.lowercase().replace("_", "").replace(" ", "")
            val rawLower = header.lowercase()
            val hasRequired = lower.contains("previous") ||
                              lower.contains("prev") ||
                              lower.contains("printable") ||
                              lower.contains("count") ||
                              lower.contains("onhand") ||
                              rawLower.contains("on hand") ||
                              lower.contains("qty") ||
                              lower.contains("quantity") ||
                              lower.contains("amount") ||
                              lower.contains("last")
            val hasForbidden = lower.contains("menu") ||
                               lower.contains("week") ||
                               lower.contains("w1") ||
                               lower.contains("w2") ||
                               lower.contains("w3") ||
                               lower.contains("w4") ||
                               lower.contains("recipe")
            return hasRequired && !hasForbidden
        }

        fun isValidCategoryHeader(header: String): Boolean {
            val lower = header.lowercase().replace("_", "").replace(" ", "")
            val hasRequired = lower.contains("location") ||
                              lower.contains("category") ||
                              lower.contains("storage") ||
                              lower.contains("area") ||
                              lower.contains("cat")
            val hasForbidden = lower.contains("menu") ||
                               lower.contains("week") ||
                               lower.contains("w1") ||
                               lower.contains("w2") ||
                               lower.contains("w3") ||
                               lower.contains("w4") ||
                               lower.contains("recipe")
            return hasRequired && !hasForbidden
        }

        private fun parseCatalogDtoFromJson(obj: JsonObject): CatalogSyncDto {
            fun getString(vararg keys: String, validator: ((String) -> Boolean)? = null): String? {
                for (key in keys) {
                    if (obj.has(key) && !obj.get(key).isJsonNull) {
                        if (validator == null || validator(key)) {
                            val valStr = obj.get(key).asString?.trim()
                            if (!valStr.isNullOrBlank()) return valStr
                        }
                    }
                    for (entry in obj.entrySet()) {
                        if (entry.key.equals(key, ignoreCase = true) && !entry.value.isJsonNull) {
                            if (validator == null || validator(entry.key)) {
                                val valStr = entry.value.asString?.trim()
                                if (!valStr.isNullOrBlank()) return valStr
                            }
                        }
                    }
                }
                if (validator != null) {
                    for (entry in obj.entrySet()) {
                        if (validator(entry.key) && !entry.value.isJsonNull) {
                            val valStr = entry.value.asString?.trim()
                            if (!valStr.isNullOrBlank()) return valStr
                        }
                    }
                }
                return null
            }

            fun getInt(vararg keys: String): Int? {
                for (key in keys) {
                    val jsonElem = if (obj.has(key) && !obj.get(key).isJsonNull) {
                        obj.get(key)
                    } else {
                        var found: JsonElement? = null
                        for (entry in obj.entrySet()) {
                            if (entry.key.equals(key, ignoreCase = true) && !entry.value.isJsonNull) {
                                found = entry.value
                                break
                            }
                        }
                        found
                    }
                    if (jsonElem != null) {
                        try {
                            val intVal = jsonElem.asInt
                            if (intVal != 0) return intVal
                        } catch (_: Exception) {}
                        val valStr = jsonElem.asString?.trim()?.toIntOrNull()
                        if (valStr != null) return valStr
                    }
                }
                return null
            }

            fun getDouble(vararg keys: String, validator: ((String) -> Boolean)? = null): Double? {
                for (key in keys) {
                    val jsonElem = if (obj.has(key) && !obj.get(key).isJsonNull && (validator == null || validator(key))) {
                        obj.get(key)
                    } else {
                        var found: JsonElement? = null
                        for (entry in obj.entrySet()) {
                            if (entry.key.equals(key, ignoreCase = true) && !entry.value.isJsonNull && (validator == null || validator(entry.key))) {
                                found = entry.value
                                break
                            }
                        }
                        found
                    }
                    if (jsonElem != null) {
                        val valStr = jsonElem.asString?.trim()?.toDoubleOrNull()
                        if (valStr != null) return valStr
                        try {
                            val dVal = jsonElem.asDouble
                            if (dVal != 0.0) return dVal
                        } catch (_: Exception) {}
                    }
                }
                if (validator != null) {
                    for (entry in obj.entrySet()) {
                        if (validator(entry.key) && !entry.value.isJsonNull) {
                            val jsonElem = entry.value
                            val valStr = jsonElem.asString?.trim()?.toDoubleOrNull()
                            if (valStr != null) return valStr
                            try {
                                val dVal = jsonElem.asDouble
                                if (dVal != 0.0) return dVal
                            } catch (_: Exception) {}
                        }
                    }
                }
                return null
            }

            val upc = getString("syscoUpc", "upc", "syscoUPC", "UPC", "barcode", "Barcode", "itemUpc", "sysco_upc", "code", "Code", "Sysco UPC")
            val itemNum = getString("syscoItemNumber", "Sysco Item #", "Sysco Item #:", "SUPC", "sysco_item_number", "itemNumber", "item_number", "item#")
            val piazzaItemNum = getString("piazzaItemNumber", "Piazza Item #", "Piazza #", "piazza_item_number", "piazza#")
            val vendorItemNum = getString("vendorItemNumber", "Vendor Item #", "Vendor Item", "vendor_item_number", "vendorItem", "Vendor #", "Vendor Number")
            val vendorSku = getString("vendorSku", "Vendor SKU", "Vendor Sku", "vendor_sku", "SKU", "sku")
            val altBarcodes = getString("alternateBarcodes", "Alternate Barcodes", "Alternate UPC", "Alternate Barcode", "Alt UPC", "Barcodes", "Bar Code", "Alternate Bar Codes", "alternate_barcodes")
            val name = getString("name", "itemName", "item_name", "productName", "product_name", "title", "product", "Product", "Item", "Product / Item", "Product Name", "Description")
            val category = getString("category", "Category", "item_category", "itemCategory", "cat", "CATEGORY", "Location", validator = { isValidCategoryHeader(it) })
            val days = getInt("defaultShelfLifeDays", "shelfLifeDays", "shelfLife", "shelf_life_days", "default_shelf_life_days", "daysOffset", "days", "Shelf Life", "shelf_life")
            val unit = getString("unit", "Unit", "unitOfMeasure", "uom", "UNIT")
            val qty = getDouble("lastOnHandAmount", "onHandAmount", "quantity", "onHand", "quantityOnHand", "on_hand", "lastOnHand", "last_on_hand", "qty", "Amount", "OnHand", "QTY", "Quantity", "previousCount", "previous_count", "prevCount", "prev_count", "lastCount", "last_count", "count", "printable", "printableCount", "previous", "On Hand", "Previous Count", "Prev Count", "Last Count", "Printable", "Printable Count", "Previous", "Count", validator = { isValidOnHandHeader(it) })

            return CatalogSyncDto(
                syscoUpc = upc,
                syscoItemNumber = itemNum,
                piazzaItemNumber = piazzaItemNum,
                vendorItemNumber = vendorItemNum,
                vendorSku = vendorSku,
                alternateBarcodes = altBarcodes,
                name = name,
                category = category,
                defaultShelfLifeDays = days,
                unit = unit,
                lastOnHandAmount = qty
            )
        }

        private fun parseCsvLine(line: String): List<String> {
            val tokens = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            for (i in line.indices) {
                val c = line[i]
                when {
                    c == '"' -> {
                        inQuotes = !inQuotes
                        sb.append(c)
                    }
                    c == ',' && !inQuotes -> {
                        tokens.add(sb.toString().trim().removeSurrounding("\""))
                        sb.clear()
                    }
                    else -> {
                        sb.append(c)
                    }
                }
            }
            tokens.add(sb.toString().trim().removeSurrounding("\""))
            return tokens
        }

        fun parseCatalogDtosFromCsvString(csvStr: String): List<CatalogSyncDto> {
            val lines = csvStr.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()

            val list = mutableListOf<CatalogSyncDto>()
            var currentCategory = "General"

            var upcIdx = -1
            var itemNumIdx = -1
            var piazzaItemNumIdx = -1
            var vendorItemNumIdx = -1
            var vendorSkuIdx = -1
            var altBarcodesIdx = -1
            var nameIdx = -1
            var catIdx = -1
            var daysIdx = -1
            var unitIdx = -1
            var qtyIdx = -1
            var isHeaderMapped = false

            for (line in lines) {
                val tokens = parseCsvLine(line)
                if (tokens.isEmpty()) continue
                val firstCol = tokens[0]

                val lineLower = line.lowercase()
                val isHeaderRow = !isHeaderMapped && (
                    firstCol.equals("upc", ignoreCase = true) ||
                    firstCol.equals("sysco upc", ignoreCase = true) ||
                    firstCol.equals("sysco item #", ignoreCase = true) ||
                    firstCol.equals("sysco item #:", ignoreCase = true) ||
                    firstCol.equals("supc", ignoreCase = true) ||
                    firstCol.equals("piazza item #", ignoreCase = true) ||
                    firstCol.equals("vendor item #", ignoreCase = true) ||
                    firstCol.equals("vendor sku", ignoreCase = true) ||
                    firstCol.equals("product", ignoreCase = true) ||
                    firstCol.equals("item", ignoreCase = true) ||
                    firstCol.equals("item name", ignoreCase = true) ||
                    firstCol.equals("product name", ignoreCase = true) ||
                    firstCol.equals("code", ignoreCase = true) ||
                    firstCol.equals("barcode", ignoreCase = true) ||
                    firstCol.equals("category", ignoreCase = true) ||
                    firstCol.equals("description", ignoreCase = true) ||
                    firstCol.equals("name", ignoreCase = true) ||
                    ((lineLower.contains("product") || lineLower.contains("item") || lineLower.contains("upc") || lineLower.contains("code") || lineLower.contains("supc") || lineLower.contains("piazza") || lineLower.contains("vendor") || lineLower.contains("sku")) &&
                     (lineLower.contains("count") || lineLower.contains("qty") || lineLower.contains("quantity") || lineLower.contains("on hand") || lineLower.contains("printable") || lineLower.contains("previous") || lineLower.contains("prev") || lineLower.contains("last") || lineLower.contains("shelf") || lineLower.contains("item") || lineLower.contains("upc") || lineLower.contains("barcodes")))
                )

                if (isHeaderRow) {
                    for ((idx, tok) in tokens.withIndex()) {
                        val tokClean = tok.trim()
                        val tokLower = tokClean.lowercase()
                        if (tokClean.equals("Sysco UPC", ignoreCase = true) ||
                            tokClean.equals("syscoUpc", ignoreCase = true) ||
                            tokClean.equals("UPC", ignoreCase = true) ||
                            (tokClean.equals("Barcode", ignoreCase = true) && !tokLower.contains("alternate")) ||
                            tokClean.equals("code", ignoreCase = true) ||
                            (tokLower.contains("upc") && !tokLower.contains("alternate") && !tokLower.contains("alt")) || 
                            (tokLower.contains("barcode") && !tokLower.contains("alternate") && !tokLower.contains("alt"))) {
                            if (upcIdx < 0) upcIdx = idx
                        } else if (tokLower.contains("alternate") || tokLower.contains("alt upc") || tokLower.contains("barcodes")) {
                            if (altBarcodesIdx < 0) altBarcodesIdx = idx
                        } else if (tokClean.equals("Piazza Item #", ignoreCase = true) ||
                                   tokClean.equals("Piazza #", ignoreCase = true) ||
                                   tokClean.equals("piazzaItemNumber", ignoreCase = true) ||
                                   tokLower.contains("piazza")) {
                            if (piazzaItemNumIdx < 0) piazzaItemNumIdx = idx
                        } else if (tokClean.equals("Vendor Item #", ignoreCase = true) ||
                                   tokClean.equals("Vendor Item", ignoreCase = true) ||
                                   tokClean.equals("Vendor #", ignoreCase = true) ||
                                   tokClean.equals("vendorItemNumber", ignoreCase = true) ||
                                   tokLower.contains("vendor item") || tokLower.contains("vendor #")) {
                            if (vendorItemNumIdx < 0) vendorItemNumIdx = idx
                        } else if (tokClean.equals("Vendor SKU", ignoreCase = true) ||
                                   tokClean.equals("Vendor Sku", ignoreCase = true) ||
                                   tokClean.equals("vendorSku", ignoreCase = true) ||
                                   tokClean.equals("SKU", ignoreCase = true) ||
                                   tokClean.equals("sku", ignoreCase = true) ||
                                   tokLower.contains("sku")) {
                            if (vendorSkuIdx < 0) vendorSkuIdx = idx
                        } else if (tokClean.equals("Sysco Item #", ignoreCase = true) ||
                                   tokClean.equals("Sysco Item #:", ignoreCase = true) ||
                                   tokClean.equals("SUPC", ignoreCase = true) ||
                                   tokClean.equals("syscoItemNumber", ignoreCase = true) ||
                                   tokClean.equals("itemNumber", ignoreCase = true) ||
                                   tokClean.equals("item #", ignoreCase = true) ||
                                   tokClean.equals("item#", ignoreCase = true) ||
                                   ((tokLower.contains("item #") || tokLower.contains("item#") || tokLower.contains("supc")) && !tokLower.contains("piazza") && !tokLower.contains("vendor"))) {
                            if (itemNumIdx < 0) itemNumIdx = idx
                            if (tokClean.equals("SUPC", ignoreCase = true) && upcIdx < 0) {
                                upcIdx = idx
                            }
                        } else if (isValidCategoryHeader(tokClean)) {
                            catIdx = idx
                        } else if (tokLower.contains("shelf") || tokLower.contains("days")) {
                            daysIdx = idx
                        } else if (tokLower.contains("unit") || tokLower.contains("uom") || tokLower.contains("par")) {
                            unitIdx = idx
                        } else if (isValidOnHandHeader(tokClean)) {
                            qtyIdx = idx
                        } else if (tokLower.contains("product") || tokLower.contains("item") || tokLower.contains("name") || tokLower.contains("title") || tokLower.contains("description")) {
                            if (nameIdx < 0 && !tokLower.contains("item #") && !tokLower.contains("item#") && !tokLower.contains("supc") && !tokLower.contains("piazza") && !tokLower.contains("vendor")) nameIdx = idx
                        }
                    }
                    isHeaderMapped = true
                    continue
                }

                val isSectionHeader = tokens.drop(1).all { it.isBlank() }
                if (isSectionHeader) {
                    val normCat = normalizeCategory(firstCol)
                    if (normCat != "General" || firstCol.contains("General", ignoreCase = true)) {
                        currentCategory = normCat
                        continue
                    }
                }

                val upcVal = if (upcIdx >= 0 && upcIdx < tokens.size) tokens[upcIdx] else if (firstCol.all { it.isDigit() } && firstCol.length >= 8) firstCol else ""
                val itemNumVal = if (itemNumIdx >= 0 && itemNumIdx < tokens.size) tokens[itemNumIdx] else ""
                val piazzaItemNumVal = if (piazzaItemNumIdx >= 0 && piazzaItemNumIdx < tokens.size) tokens[piazzaItemNumIdx] else ""
                val vendorItemNumVal = if (vendorItemNumIdx >= 0 && vendorItemNumIdx < tokens.size) tokens[vendorItemNumIdx] else ""
                val vendorSkuVal = if (vendorSkuIdx >= 0 && vendorSkuIdx < tokens.size) tokens[vendorSkuIdx] else ""
                val altBarcodesVal = if (altBarcodesIdx >= 0 && altBarcodesIdx < tokens.size) tokens[altBarcodesIdx] else ""

                val nameVal = if (nameIdx >= 0 && nameIdx < tokens.size) tokens[nameIdx] else if (upcVal.isBlank() && itemNumVal.isBlank() && piazzaItemNumVal.isBlank() && vendorItemNumVal.isBlank() && vendorSkuVal.isBlank() && altBarcodesVal.isBlank()) firstCol else tokens.getOrNull(1) ?: ""
                val catVal = if (catIdx >= 0 && catIdx < tokens.size) tokens[catIdx] else currentCategory
                val daysVal = if (daysIdx >= 0 && daysIdx < tokens.size) tokens[daysIdx].toIntOrNull() else null
                val parsedQty = if (qtyIdx >= 0 && qtyIdx < tokens.size) tokens[qtyIdx].toDoubleOrNull() else null
                val qtyVal = if (parsedQty != null && parsedQty > 0.0) parsedQty else 1.0

                val parCol = if (unitIdx >= 0 && unitIdx < tokens.size) tokens[unitIdx] else tokens.getOrNull(4) ?: ""
                val unitVal = when {
                    parCol.contains("cs", ignoreCase = true) -> "CS"
                    parCol.contains("gal", ignoreCase = true) -> "GAL"
                    parCol.contains("bag", ignoreCase = true) || parCol.contains("b", ignoreCase = true) -> "CS"
                    parCol.contains("jug", ignoreCase = true) -> "CTN"
                    parCol.contains("jar", ignoreCase = true) -> "EA"
                    parCol.contains("#", ignoreCase = true) || parCol.contains("lb", ignoreCase = true) -> "LB"
                    else -> parCol.ifBlank { "EA" }
                }

                if (nameVal.isBlank() && upcVal.isBlank() && itemNumVal.isBlank() && piazzaItemNumVal.isBlank() && vendorItemNumVal.isBlank() && vendorSkuVal.isBlank() && altBarcodesVal.isBlank()) continue

                list.add(
                    CatalogSyncDto(
                        syscoUpc = upcVal.ifBlank { null },
                        syscoItemNumber = itemNumVal.ifBlank { null },
                        piazzaItemNumber = piazzaItemNumVal.ifBlank { null },
                        vendorItemNumber = vendorItemNumVal.ifBlank { null },
                        vendorSku = vendorSkuVal.ifBlank { null },
                        alternateBarcodes = altBarcodesVal.ifBlank { null },
                        name = nameVal.ifBlank { null },
                        category = catVal.ifBlank { currentCategory },
                        defaultShelfLifeDays = daysVal,
                        unit = unitVal,
                        lastOnHandAmount = qtyVal
                    )
                )
            }

            return list
        }
    }

    suspend fun processAndSaveCatalogDtos(dtos: List<CatalogSyncDto>): List<CatalogItem> = withContext(Dispatchers.IO) {
        val existingItems = catalogDao.getAllCatalogItemsList()
        val nameToExistingMap = existingItems.associateBy { it.name.lowercase().removePrefix("sysco ").trim() }

        val itemsToSave = mutableListOf<CatalogItem>()

        for (dto in dtos) {
            val rawName = dto.name ?: ""
            val cleanName = rawName.removePrefix("Sysco ").removePrefix("SYSCO ").removePrefix("sysco ").trim()
            val rawUpc = dto.syscoUpc?.trim() ?: ""
            val rawItemNum = dto.syscoItemNumber?.trim() ?: ""
            val rawPiazzaItemNum = dto.piazzaItemNumber?.trim() ?: ""
            val rawVendorItemNum = dto.vendorItemNumber?.trim() ?: ""
            val rawVendorSku = dto.vendorSku?.trim() ?: ""
            val rawAltBarcodes = dto.alternateBarcodes?.trim() ?: ""

            if (cleanName.isBlank() && rawUpc.isBlank() && rawItemNum.isBlank() && rawPiazzaItemNum.isBlank() && rawVendorItemNum.isBlank() && rawVendorSku.isBlank() && rawAltBarcodes.isBlank()) continue

            val effectiveUpc = if (rawUpc.isNotBlank()) {
                rawUpc.split(",").map { 
                    val trimmed = it.trim()
                    if (trimmed.all { c -> c.isDigit() }) SyscoUpcNormalizer.normalize(trimmed) else trimmed
                }.joinToString(", ")
            } else if (rawItemNum.isNotBlank()) {
                rawItemNum
            } else if (rawPiazzaItemNum.isNotBlank()) {
                rawPiazzaItemNum
            } else if (rawVendorItemNum.isNotBlank()) {
                rawVendorItemNum
            } else if (rawVendorSku.isNotBlank()) {
                rawVendorSku
            } else if (nameToExistingMap.containsKey(cleanName.lowercase())) {
                nameToExistingMap[cleanName.lowercase()]!!.syscoUpc
            } else {
                val hashVal = abs(cleanName.lowercase().hashCode()) % 10000007
                "07486" + String.format(Locale.US, "%07d", hashVal)
            }

            val effectiveAltBarcodes = if (rawAltBarcodes.isNotBlank()) {
                rawAltBarcodes.split(",").map { 
                    val trimmed = it.trim()
                    if (trimmed.all { c -> c.isDigit() }) SyscoUpcNormalizer.normalize(trimmed) else trimmed
                }.joinToString(", ")
            } else if (nameToExistingMap.containsKey(cleanName.lowercase())) {
                nameToExistingMap[cleanName.lowercase()]!!.alternateBarcodes
            } else {
                ""
            }

            val effectiveName = if (cleanName.isNotBlank()) {
                cleanName
            } else {
                "Unrecognized Item"
            }

            val category = normalizeCategory(dto.category)

            val shelfLifeDays = if (dto.defaultShelfLifeDays != null && dto.defaultShelfLifeDays > 0) {
                DateCalculator.clampShelfLifeDays(dto.defaultShelfLifeDays)
            } else if (nameToExistingMap.containsKey(cleanName.lowercase())) {
                nameToExistingMap[cleanName.lowercase()]!!.defaultShelfLifeDays
            } else {
                DateCalculator.getShelfLifeDaysForCategory(category)
            }

            val unit = dto.unit?.trim()?.uppercase()?.ifBlank { "EA" } ?: "EA"
            val rawOnHand = dto.lastOnHandAmount
            val onHand = if (rawOnHand != null && rawOnHand > 0.0) rawOnHand else 1.0

            val item = CatalogItem(
                syscoUpc = effectiveUpc,
                syscoItemNumber = rawItemNum,
                piazzaItemNumber = rawPiazzaItemNum,
                vendorItemNumber = rawVendorItemNum,
                vendorSku = rawVendorSku,
                alternateBarcodes = effectiveAltBarcodes,
                name = effectiveName,
                category = category,
                defaultShelfLifeDays = shelfLifeDays,
                unit = unit,
                lastOnHandAmount = onHand
            )
            itemsToSave.add(item)
        }

        if (itemsToSave.isNotEmpty()) {
            catalogDao.insertAll(itemsToSave)
            itemsToSave.forEach { item ->
                try { firestoreRepository.saveCatalogItem(item) } catch (_: Exception) {}
            }
        }
        itemsToSave
    }

    suspend fun fetchCatalogFromSheets(webAppUrl: String = settingsManager.webAppUrl): Result<List<CatalogItem>> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = webAppUrl.ifBlank { SettingsManager.DEFAULT_WEB_APP_URL }
            val cleanUrl = targetUrl.trim()

            if (cleanUrl.contains("/pubhtml") || cleanUrl.contains("/pub?") || cleanUrl.contains("output=csv")) {
                val csvResult = importCatalogFromPublishedCsv(cleanUrl)
                if (csvResult.isSuccess) {
                    val allItems = catalogDao.getAllCatalogItemsList()
                    return@withContext Result.success(allItems)
                } else {
                    return@withContext Result.failure(csvResult.exceptionOrNull() ?: Exception("CSV import failed"))
                }
            }

            var dtoList: List<CatalogSyncDto>? = null

            try {
                val response = sheetsApiService.fetchCatalog(cleanUrl)
                if (response.isSuccessful && response.body() != null) {
                    dtoList = response.body()
                }
            } catch (_: Exception) {
            }

            if (dtoList.isNullOrEmpty()) {
                val request = Request.Builder().url(cleanUrl).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code} fetching catalog"))
                }
                val bodyStr = response.body?.string() ?: ""
                dtoList = parseCatalogDtosFromRawString(bodyStr)
            }

            if (dtoList.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No catalog items returned from sheet"))
            }

            val savedItems = processAndSaveCatalogDtos(dtoList)
            Result.success(savedItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshCatalogFromSheets(webAppUrl: String = settingsManager.webAppUrl): Result<Int> = withContext(Dispatchers.IO) {
        val res = fetchCatalogFromSheets(webAppUrl)
        if (res.isSuccess) {
            Result.success(res.getOrNull()?.size ?: 0)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to refresh catalog"))
        }
    }

    suspend fun seedDefaultsIfEmpty(publishedUrl: String = settingsManager.publishedWebUrl) = withContext(Dispatchers.IO) {
        catalogDao.insertAll(defaultCatalogItems)
        defaultExpirationRules.forEach { rule ->
            if (expirationRuleDao.getRuleByCategory(rule.category) == null) {
                expirationRuleDao.insertRule(rule)
            }
        }
        if (publishedUrl.isNotBlank()) {
            importCatalogFromPublishedCsv(publishedUrl)
        }
    }

    suspend fun importCatalogFromPublishedCsv(publishedWebUrl: String = settingsManager.publishedWebUrl): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val csvUrl = getPublishedCsvUrl(publishedWebUrl)
            val request = Request.Builder().url(csvUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code} fetching CSV"))
            }

            val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty CSV response"))

            val dtos = parseCatalogDtosFromCsvString(bodyString)
            if (dtos.isEmpty()) {
                return@withContext Result.failure(Exception("No catalog items parsed from CSV"))
            }

            val savedItems = processAndSaveCatalogDtos(dtos)
            Result.success(savedItems.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCatalogItem(query: String): CatalogItem = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val normalizedQuery = SyscoUpcNormalizer.normalize(cleanQuery)

        // 1. Direct getByUpc match
        val foundByUpc = catalogDao.getByUpc(normalizedQuery) ?: catalogDao.getByUpc(cleanQuery)
        if (foundByUpc != null) {
            return@withContext checkAndReturn(foundByUpc)
        }

        // 2. Iterate all catalog items to check dual-key indexing across all fields:
        // - syscoUpc (comma separated, normalized, raw)
        // - alternateBarcodes (comma separated, normalized, raw)
        // - syscoItemNumber
        // - piazzaItemNumber
        // - vendorItemNumber
        // - vendorSku
        val allItems = catalogDao.getAllCatalogItemsList()
        for (item in allItems) {
            // Check syscoUpc (could be comma-separated)
            if (item.syscoUpc.isNotBlank()) {
                val upcParts = item.syscoUpc.split(",").map { it.trim() }
                for (part in upcParts) {
                    val normPart = SyscoUpcNormalizer.normalize(part)
                    if (normPart.equals(normalizedQuery, ignoreCase = true) ||
                        normPart.equals(cleanQuery, ignoreCase = true) ||
                        part.equals(cleanQuery, ignoreCase = true) ||
                        part.equals(normalizedQuery, ignoreCase = true)) {
                        return@withContext checkAndReturn(item)
                    }
                }
            }

            // Check alternateBarcodes (could be comma-separated)
            if (item.alternateBarcodes.isNotBlank()) {
                val altParts = item.alternateBarcodes.split(",").map { it.trim() }
                for (part in altParts) {
                    val normPart = SyscoUpcNormalizer.normalize(part)
                    if (normPart.equals(normalizedQuery, ignoreCase = true) ||
                        normPart.equals(cleanQuery, ignoreCase = true) ||
                        part.equals(cleanQuery, ignoreCase = true) ||
                        part.equals(normalizedQuery, ignoreCase = true)) {
                        return@withContext checkAndReturn(item)
                    }
                }
            }

            // Check item numbers and SKUs (syscoItemNumber, piazzaItemNumber, vendorItemNumber, vendorSku)
            val indexKeys = listOf(
                item.syscoItemNumber,
                item.piazzaItemNumber,
                item.vendorItemNumber,
                item.vendorSku
            )
            for (key in indexKeys) {
                if (key.isNotBlank()) {
                    val cleanKey = key.trim()
                    val normKey = SyscoUpcNormalizer.normalize(cleanKey)
                    if (cleanKey.equals(cleanQuery, ignoreCase = true) ||
                        cleanKey.equals(normalizedQuery, ignoreCase = true) ||
                        normKey.equals(normalizedQuery, ignoreCase = true) ||
                        normKey.equals(cleanQuery, ignoreCase = true)) {
                        return@withContext checkAndReturn(item)
                    }
                }
            }
        }

        // 3. Fallback for unrecognized items: return fallback CatalogItem with name "Unrecognized Item"
        val fallback = CatalogItem(
            syscoUpc = normalizedQuery.ifBlank { cleanQuery },
            name = "Unrecognized Item",
            category = "General",
            defaultShelfLifeDays = 365,
            unit = "EA",
            lastOnHandAmount = 1.0
        )
        catalogDao.insertOrUpdate(fallback)
        fallback
    }

    private suspend fun checkAndReturn(item: CatalogItem): CatalogItem {
        if (item.lastOnHandAmount <= 0.0) {
            val updated = item.copy(lastOnHandAmount = 1.0)
            catalogDao.insertOrUpdate(updated)
            return updated
        }
        return item
    }

    suspend fun getCatalogItemByName(name: String): CatalogItem? = withContext(Dispatchers.IO) {
        catalogDao.getByName(name)
    }

    suspend fun getAllCatalogItemsList(): List<CatalogItem> = withContext(Dispatchers.IO) {
        catalogDao.getAllCatalogItemsList()
    }

    suspend fun saveCatalogItem(item: CatalogItem) = withContext(Dispatchers.IO) {
        val cleanName = item.name.removePrefix("Sysco ").removePrefix("SYSCO ").trim().toTitleCase()
        val normCategory = normalizeCategory(item.category).toTitleCase()
        val effectiveOnHand = if (item.lastOnHandAmount > 0.0) item.lastOnHandAmount else 1.0
        val normalizedItem = item.copy(
            syscoUpc = SyscoUpcNormalizer.normalize(item.syscoUpc),
            name = cleanName,
            category = normCategory,
            defaultShelfLifeDays = DateCalculator.clampShelfLifeDays(item.defaultShelfLifeDays, normCategory),
            lastOnHandAmount = effectiveOnHand
        )
        catalogDao.insertOrUpdate(normalizedItem)
        try { firestoreRepository.saveCatalogItem(normalizedItem) } catch (_: Exception) {}
    }

    fun getAllCatalogItems(): Flow<List<CatalogItem>> = catalogDao.getAllCatalogItems()

    suspend fun deleteCatalogItem(upc: String) = withContext(Dispatchers.IO) {
        val normUpc = SyscoUpcNormalizer.normalize(upc)
        catalogDao.deleteByUpc(normUpc)
        try { firestoreRepository.deleteCatalogItem(normUpc) } catch (_: Exception) {}
    }

    fun getAllScanRecords(): Flow<List<ScanRecord>> = scanRecordDao.getAllScanRecords()

    suspend fun addScanRecord(record: ScanRecord) = withContext(Dispatchers.IO) {
        val cleanName = record.itemName.removePrefix("Sysco ").removePrefix("SYSCO ").trim().toTitleCase()
        val effectiveOnHand = if (record.onHandAmount > 0.0) record.onHandAmount else 1.0
        val effectiveReceivedBy = if (record.receivedBy.isNotBlank()) record.receivedBy else settingsManager.staffName
        val normalizedRecord = record.copy(
            syscoUpc = SyscoUpcNormalizer.normalize(record.syscoUpc),
            itemName = cleanName,
            shelfLifeDays = DateCalculator.clampShelfLifeDays(record.shelfLifeDays, record.category),
            onHandAmount = effectiveOnHand,
            receivedBy = effectiveReceivedBy
        )
        scanRecordDao.insertScanRecord(normalizedRecord)
        try { firestoreRepository.addScanRecord(normalizedRecord) } catch (_: Exception) {}

        // Update catalog item's lastOnHandAmount only if not an audit record
        if (!normalizedRecord.isAudit) {
            val cleanUpc = normalizedRecord.syscoUpc
            val existingCatalog = catalogDao.getByUpc(cleanUpc)
            if (existingCatalog != null) {
                val updatedCatalog = existingCatalog.copy(lastOnHandAmount = effectiveOnHand)
                catalogDao.insertOrUpdate(updatedCatalog)
                try { firestoreRepository.saveCatalogItem(updatedCatalog) } catch (_: Exception) {}
            } else {
                val newCatalog = CatalogItem(
                    syscoUpc = cleanUpc,
                    name = normalizedRecord.itemName,
                    category = normalizedRecord.category,
                    defaultShelfLifeDays = normalizedRecord.shelfLifeDays,
                    unit = normalizedRecord.unit,
                    lastOnHandAmount = effectiveOnHand
                )
                catalogDao.insertOrUpdate(newCatalog)
                try { firestoreRepository.saveCatalogItem(newCatalog) } catch (_: Exception) {}
            }
        }
    }

    suspend fun updateScanRecordOnHandAmount(id: String, syscoUpc: String, onHandAmount: Double) = withContext(Dispatchers.IO) {
        val effectiveOnHand = if (onHandAmount > 0.0) onHandAmount else 1.0
        scanRecordDao.updateOnHandAmount(id, effectiveOnHand)
        try { firestoreRepository.updateScanRecordOnHandAmount(id, effectiveOnHand) } catch (_: Exception) {}
        val cleanUpc = SyscoUpcNormalizer.normalize(syscoUpc)
        val existingCatalog = catalogDao.getByUpc(cleanUpc)
        if (existingCatalog != null) {
            val updatedCatalog = existingCatalog.copy(lastOnHandAmount = effectiveOnHand)
            catalogDao.insertOrUpdate(updatedCatalog)
            try { firestoreRepository.saveCatalogItem(updatedCatalog) } catch (_: Exception) {}
        }
    }

    suspend fun markRecordPrinted(recordId: String) = withContext(Dispatchers.IO) {
        scanRecordDao.markPrinted(recordId, true)
        try { firestoreRepository.markRecordPrinted(recordId) } catch (_: Exception) {}
    }

    suspend fun deleteScanRecord(recordId: String) = withContext(Dispatchers.IO) {
        scanRecordDao.deleteRecord(recordId)
        try { firestoreRepository.deleteScanRecord(recordId) } catch (_: Exception) {}
    }

    suspend fun clearAllScanRecords() = withContext(Dispatchers.IO) {
        scanRecordDao.clearAll()
    }

    fun getAllExpirationRules(): Flow<List<ExpirationRule>> = expirationRuleDao.getAllRules()

    suspend fun getExpirationRule(category: String): ExpirationRule = withContext(Dispatchers.IO) {
        val normCategory = normalizeCategory(category)
        expirationRuleDao.getRuleByCategory(normCategory)
            ?: ExpirationRule(normCategory, DateCalculator.getShelfLifeDaysForCategory(normCategory), "Category default rule")
    }

    suspend fun saveExpirationRule(rule: ExpirationRule) = withContext(Dispatchers.IO) {
        val normCategory = normalizeCategory(rule.category)
        val clampedRule = rule.copy(
            category = normCategory,
            daysOffset = DateCalculator.clampShelfLifeDays(rule.daysOffset)
        )
        expirationRuleDao.insertRule(clampedRule)
    }

    suspend fun syncWithGoogleSheets(webAppUrl: String = settingsManager.webAppUrl): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = webAppUrl.ifBlank { SettingsManager.DEFAULT_WEB_APP_URL }

            // Pull latest catalog updates from Google Sheets
            refreshCatalogFromSheets(targetUrl)

            // Push unsynced scan records to Google Sheets
            val unsynced = scanRecordDao.getUnsyncedScanRecords()
            if (unsynced.isEmpty()) {
                return@withContext Result.success(0)
            }

            val dtos = unsynced.map {
                SheetScanRecordDto(
                    id = it.id,
                    upc = it.syscoUpc,
                    itemNumber = it.itemNumber,
                    name = it.itemName,
                    deliveryDate = it.deliveryDate,
                    useByDate = it.useByDate,
                    storageArea = ZplGenerator.getStorageLocationForCategory(it.category),
                    receivedBy = it.receivedBy.ifBlank { settingsManager.staffName },
                    onHandQty = it.onHandAmount,
                    category = it.category,
                    shelfLifeDays = it.shelfLifeDays,
                    unit = it.unit,
                    scanTimestamp = it.scanTimestamp
                )
            }

            val payload = SheetSyncPayload(
                spreadsheetId = settingsManager.sheetId.ifBlank { SettingsManager.DEFAULT_SHEET_ID },
                sheetTab = settingsManager.departmentSheetTab.ifBlank { "Delivery Scan Log" },
                companyCode = settingsManager.companyCode.ifBlank { "CVILLA" },
                records = dtos
            )

            val response = sheetsApiService.syncWithPayload(targetUrl, payload)
            if (response.isSuccessful && response.body()?.success == true) {
                unsynced.forEach { scanRecordDao.markSynced(it.id) }
                Result.success(unsynced.size)
            } else if (response.isSuccessful) {
                unsynced.forEach { scanRecordDao.markSynced(it.id) }
                Result.success(unsynced.size)
            } else {
                val err = Exception(response.body()?.message ?: "HTTP ${response.code()} sync error")
                ErrorLogger.logError(appContext, settingsManager, "SHEETS_SYNC", err)
                Result.failure(err)
            }
        } catch (e: Exception) {
            ErrorLogger.logError(appContext, settingsManager, "SHEETS_SYNC", e)
            Result.failure(e)
        }
    }

    suspend fun syncFromFirestore(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val catRes = firestoreRepository.fetchCatalog()
            if (catRes.isSuccess) {
                val items = catRes.getOrNull() ?: emptyList()
                if (items.isNotEmpty()) {
                    catalogDao.insertAll(items)
                }
            }
            val scanRes = firestoreRepository.fetchScanRecords()
            if (scanRes.isSuccess) {
                val records = scanRes.getOrNull() ?: emptyList()
                if (records.isNotEmpty()) {
                    records.forEach { scanRecordDao.insertScanRecord(it) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── WorkManager Integration ───────────────────────────────────────────────

    /**
     * Push a single scan record to Firestore directly.
     * Called by [SyncWorker] — throws on failure so the worker can retry.
     */
    suspend fun pushScanRecordToFirestore(record: ScanRecord) {
        val result = firestoreRepository.addScanRecord(record)
        if (result.isFailure) throw result.exceptionOrNull() ?: Exception("Firestore push failed")
    }

    /**
     * Enqueue a background [SyncWorker] job.
     * Safe to call from the main thread — WorkManager schedules automatically.
     */
    fun enqueueSyncWork(context: Context) {
        com.dietaryops.manager.work.SyncWorker.enqueue(context)
    }
}
