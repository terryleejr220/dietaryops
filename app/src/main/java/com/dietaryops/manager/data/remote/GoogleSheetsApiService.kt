package com.dietaryops.manager.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

import com.google.gson.annotations.SerializedName

data class SheetScanRecordDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("upc", alternate = ["syscoUpc", "syscoUPC", "UPC"])
    val upc: String,

    @SerializedName("itemNumber", alternate = ["syscoItemNumber", "item_number"])
    val itemNumber: String = "",

    @SerializedName("name", alternate = ["itemName", "item_name"])
    val name: String,

    @SerializedName("deliveryDate", alternate = ["delivery_date"])
    val deliveryDate: String,

    @SerializedName("useByDate", alternate = ["use_by_date", "expirationDate"])
    val useByDate: String,

    @SerializedName("storageArea", alternate = ["category", "storage_area", "storageLocation"])
    val storageArea: String,

    @SerializedName("receivedBy", alternate = ["staff", "staffInitials", "received_by", "by"])
    val receivedBy: String = "",

    @SerializedName("onHandQty", alternate = ["onHandAmount", "on_hand_qty", "qty", "quantity"])
    val onHandQty: Double = 1.0,

    @SerializedName("category")
    val category: String = storageArea,

    @SerializedName("shelfLifeDays")
    val shelfLifeDays: Int = 365,

    @SerializedName("unit")
    val unit: String = "EA",

    @SerializedName("scanTimestamp")
    val scanTimestamp: Long = System.currentTimeMillis()
) {
    val syscoUpc: String get() = upc
    val itemName: String get() = name
    val onHandAmount: Double get() = onHandQty

    constructor(
        id: String,
        syscoUpc: String,
        itemName: String,
        deliveryDate: String,
        useByDate: String,
        category: String,
        shelfLifeDays: Int,
        unit: String = "EA",
        onHandAmount: Double? = 1.0,
        scanTimestamp: Long = System.currentTimeMillis(),
        itemNumber: String = "",
        receivedBy: String = "",
        storageArea: String = category
    ) : this(
        id = id,
        upc = syscoUpc,
        itemNumber = itemNumber,
        name = itemName,
        deliveryDate = deliveryDate,
        useByDate = useByDate,
        storageArea = storageArea,
        receivedBy = receivedBy,
        onHandQty = onHandAmount ?: 1.0,
        category = category,
        shelfLifeDays = shelfLifeDays,
        unit = unit,
        scanTimestamp = scanTimestamp
    )
}

data class SheetSyncResponse(
    val success: Boolean,
    val message: String? = null,
    val syncedCount: Int = 0
)

data class CatalogSyncDto(
    @SerializedName("syscoUpc", alternate = ["upc", "syscoUPC", "UPC", "barcode", "Barcode", "itemUpc", "sysco_upc", "code", "Code", "Sysco UPC"])
    val syscoUpc: String? = null,

    @SerializedName("name", alternate = ["itemName", "item_name", "productName", "product_name", "title", "product", "Product", "Item", "Product / Item", "Product Name", "Description"])
    val name: String? = null,

    @SerializedName("category", alternate = ["Category", "item_category", "itemCategory", "cat", "CATEGORY", "Location"])
    val category: String? = null,

    @SerializedName("defaultShelfLifeDays", alternate = ["shelfLifeDays", "shelfLife", "shelf_life_days", "default_shelf_life_days", "daysOffset", "days", "Shelf Life", "shelf_life"])
    val defaultShelfLifeDays: Int? = null,

    @SerializedName("unit", alternate = ["Unit", "unitOfMeasure", "uom", "UNIT"])
    val unit: String? = null,

    @SerializedName("lastOnHandAmount", alternate = ["onHandAmount", "quantity", "onHand", "quantityOnHand", "on_hand", "lastOnHand", "last_on_hand", "qty", "Amount", "OnHand", "QTY", "Quantity", "previousCount", "previous_count", "prevCount", "prev_count", "lastCount", "last_count", "count", "printable", "printableCount", "previous", "On Hand", "Previous Count", "Prev Count", "Last Count", "Printable", "Printable Count", "Previous", "Count"])
    val lastOnHandAmount: Double? = null,

    @SerializedName("syscoItemNumber", alternate = ["syscoItemNumber", "Sysco Item #", "Sysco Item #:", "SUPC", "sysco_item_number", "itemNumber", "item_number", "item#"])
    val syscoItemNumber: String? = null,

    @SerializedName("piazzaItemNumber", alternate = ["piazzaItemNumber", "Piazza Item #", "Piazza #", "piazza_item_number", "piazza#"])
    val piazzaItemNumber: String? = null,

    @SerializedName("vendorItemNumber", alternate = ["vendorItemNumber", "Vendor Item #", "Vendor Item", "vendor_item_number", "vendorItem", "Vendor #", "Vendor Number"])
    val vendorItemNumber: String? = null,

    @SerializedName("vendorSku", alternate = ["vendorSku", "Vendor SKU", "Vendor Sku", "vendor_sku", "SKU", "sku"])
    val vendorSku: String? = null,

    @SerializedName("alternateBarcodes", alternate = ["alternateBarcodes", "Alternate Barcodes", "Alternate UPC", "Alternate Barcode", "Alt UPC", "Barcodes", "Bar Code", "Alternate Bar Codes", "alternate_barcodes"])
    val alternateBarcodes: String? = null
)

interface GoogleSheetsApiService {
    companion object {
        const val DEFAULT_WEB_APP_URL = "https://script.google.com/macros/s/AKfycbx0heDYU0f1XyDELM_DFuKdlKmFW_ZJD6cEGegpLHva19PLv-_2CBE_U2EmAuJt1_FxDg/exec"
    }

    @POST
    suspend fun syncScanRecords(
        @Url endpointUrl: String = DEFAULT_WEB_APP_URL,
        @Body records: List<SheetScanRecordDto>
    ): Response<SheetSyncResponse>

    @GET
    suspend fun fetchCatalog(
        @Url endpointUrl: String = DEFAULT_WEB_APP_URL
    ): Response<List<CatalogSyncDto>>
}
