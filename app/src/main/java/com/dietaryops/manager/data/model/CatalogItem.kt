package com.dietaryops.manager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.dietaryops.manager.SyscoProduct

@Entity(tableName = "catalog_items")
data class CatalogItem(
    @PrimaryKey
    @SerializedName("syscoUpc", alternate = ["upc", "syscoUPC", "UPC", "barcode", "Barcode", "itemUpc", "sysco_upc", "code", "Code", "Sysco UPC"])
    val syscoUpc: String,

    @SerializedName("name", alternate = ["itemName", "item_name", "productName", "product_name", "title", "product", "Product", "Item", "Product / Item", "Product Name", "Description"])
    val name: String,

    @SerializedName("category", alternate = ["Category", "item_category", "itemCategory", "cat", "CATEGORY", "Location"])
    val category: String = "General",

    @SerializedName("defaultShelfLifeDays", alternate = ["shelfLifeDays", "shelfLife", "shelf_life_days", "default_shelf_life_days", "daysOffset", "days", "Shelf Life", "shelf_life"])
    val defaultShelfLifeDays: Int = 365,

    @SerializedName("unit", alternate = ["Unit", "unitOfMeasure", "uom", "UNIT"])
    val unit: String = "EA",

    @SerializedName("lastOnHandAmount", alternate = ["onHandAmount", "quantity", "onHand", "quantityOnHand", "on_hand", "lastOnHand", "last_on_hand", "qty", "Amount", "OnHand", "QTY", "Quantity", "previousCount", "previous_count", "prevCount", "prev_count", "lastCount", "last_count", "count", "printable", "printableCount", "previous", "On Hand", "Previous Count", "Prev Count", "Last Count", "Printable", "Printable Count", "Previous", "Count"])
    val lastOnHandAmount: Double = 1.0,

    @SerializedName("syscoItemNumber", alternate = ["syscoItemNumber", "Sysco Item #", "Sysco Item #:", "SUPC", "sysco_item_number", "itemNumber", "item_number", "item#"])
    val syscoItemNumber: String = "",

    @SerializedName("piazzaItemNumber", alternate = ["piazzaItemNumber", "Piazza Item #", "Piazza #", "piazza_item_number", "piazza#"])
    val piazzaItemNumber: String = "",

    @SerializedName("vendorItemNumber", alternate = ["vendorItemNumber", "Vendor Item #", "Vendor Item", "vendor_item_number", "vendorItem", "Vendor #", "Vendor Number"])
    val vendorItemNumber: String = "",

    @SerializedName("vendorSku", alternate = ["vendorSku", "Vendor SKU", "Vendor Sku", "vendor_sku", "SKU", "sku"])
    val vendorSku: String = "",

    @SerializedName("alternateBarcodes", alternate = ["alternateBarcodes", "Alternate Barcodes", "Alternate UPC", "Alternate Barcode", "Alt UPC", "Barcodes", "Bar Code", "Alternate Bar Codes", "alternate_barcodes"])
    val alternateBarcodes: String = "",

    @SerializedName("parLevel", alternate = ["par", "Par", "par_level", "Par Level", "PAR"])
    val parLevel: Double = 0.0
) {
    fun toSyscoProduct(): SyscoProduct {
        return SyscoProduct(
            upc = syscoUpc,
            syscoItemNumber = syscoItemNumber,
            piazzaItemNumber = piazzaItemNumber,
            vendorItemNumber = vendorItemNumber,
            vendorSku = vendorSku,
            alternateBarcodes = alternateBarcodes,
            name = name,
            shelfLifeDays = defaultShelfLifeDays,
            category = category,
            unit = unit,
            lastOnHandAmount = lastOnHandAmount,
            parLevel = parLevel
        )
    }

    companion object {
        fun fromSyscoProduct(product: SyscoProduct, category: String = "General", unit: String = "EA", lastOnHandAmount: Double? = null): CatalogItem {
            val effectiveOnHand = lastOnHandAmount ?: product.lastOnHandAmount
            return CatalogItem(
                syscoUpc = product.upc,
                name = product.name,
                category = category,
                defaultShelfLifeDays = product.shelfLifeDays,
                unit = unit,
                lastOnHandAmount = effectiveOnHand,
                syscoItemNumber = product.syscoItemNumber,
                piazzaItemNumber = product.piazzaItemNumber,
                vendorItemNumber = product.vendorItemNumber,
                vendorSku = product.vendorSku,
                alternateBarcodes = product.alternateBarcodes,
                parLevel = product.parLevel
            )
        }
    }
}
