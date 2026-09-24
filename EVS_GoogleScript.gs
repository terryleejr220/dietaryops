// ------------------------------------------
// DIETARY OPS & EVS ENTERPRISE GOOGLE SCRIPT
// ------------------------------------------
// Instructions:
// 1. In Google Sheets, go to Extensions -> Apps Script
// 2. Paste this code, overwriting everything
// 3. Click Deploy -> New Deployment
// 4. Select "Web app", execute as "Me", who has access "Anyone"
// 5. Copy the Web App URL into your DietaryOps Manager Dashboard

const FALLBACK_SPREADSHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY";

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return responseJson({ status: "error", message: "No post data received" });
    }

    const rawData = JSON.parse(e.postData.contents);
    let targetSheetId = FALLBACK_SPREADSHEET_ID;
    let tabName = "Delivery Scan Log";
    let records = [];

    // 1. Extract Spreadsheet ID, Tab Name, and Records list
    if (Array.isArray(rawData)) {
      records = rawData;
    } else if (typeof rawData === "object") {
      targetSheetId = rawData.spreadsheetId || FALLBACK_SPREADSHEET_ID;
      tabName = rawData.sheetTab || "Delivery Scan Log";
      if (Array.isArray(rawData.records)) {
        records = rawData.records;
      } else if (Array.isArray(rawData.items)) {
        records = rawData.items;
      } else if (rawData.scan) {
        records = [rawData.scan];
      } else {
        records = [rawData];
      }
    }

    if (!records || records.length === 0) {
      return responseJson({ status: "success", message: "No scan records to process", syncedCount: 0 });
    }

    const ss = SpreadsheetApp.openById(targetSheetId);
    let logSheet = ss.getSheetByName(tabName);
    
    // Create Log tab if missing
    if (!logSheet) {
      logSheet = ss.insertSheet(tabName);
      logSheet.appendRow([
        "Timestamp", "Received By", "Item Name", "UPC",
        "Category", "Quantity", "Unit", "Use By Date", "Storage Location"
      ]);
      logSheet.getRange("A1:I1").setFontWeight("bold").setBackground("#d9ead3");
      logSheet.setFrozenRows(1);
    }

    // Get or Create Master Catalog Sheet
    let catalogSheet = ss.getSheetByName("Master Catalog") || ss.getSheetByName("Inventory Raw") || ss.getSheetByName("CV inventory 226");
    if (!catalogSheet) {
      catalogSheet = ss.insertSheet("Master Catalog");
      catalogSheet.appendRow(["UPC", "Item Name", "Category", "Shelf Life Days", "Unit"]);
      catalogSheet.getRange("A1:E1").setFontWeight("bold").setBackground("#c9daf8");
      catalogSheet.setFrozenRows(1);
    }

    // Load existing catalog UPCs to avoid duplicate master entries
    const catalogData = catalogSheet.getDataRange().getValues();
    const existingUpcs = new Set();
    for (let i = 1; i < catalogData.length; i++) {
      const upcVal = String(catalogData[i][0]).trim();
      if (upcVal) existingUpcs.add(upcVal);
    }

    let syncedCount = 0;

    // Process each record
    records.forEach(function(rec) {
      const upc = String(rec.upc || rec.syscoUpc || rec.barcode || "").trim();
      const itemName = String(rec.name || rec.itemName || rec.title || "Scanned Item").trim();
      const category = String(rec.category || rec.storageArea || "General").trim();
      const qty = rec.onHandQty !== undefined ? rec.onHandQty : (rec.onHandAmount !== undefined ? rec.onHandAmount : 1.0);
      const unit = String(rec.unit || "EA").trim();
      const receivedBy = String(rec.receivedBy || rec.staff || "Staff").trim();
      const useByDate = String(rec.useByDate || rec.expirationDate || "").trim();
      const storageArea = String(rec.storageArea || rec.storageLocation || "").trim();
      const ts = rec.scanTimestamp ? new Date(rec.scanTimestamp) : new Date();

      // A. Append to Log Tab
      logSheet.appendRow([
        ts, receivedBy, itemName, upc, category, qty, unit, useByDate, storageArea
      ]);

      // B. If UPC is not in Master Catalog, auto-add to Master Catalog tab!
      if (upc && !existingUpcs.has(upc)) {
        const shelfLife = rec.shelfLifeDays || 365;
        catalogSheet.appendRow([upc, itemName, category, shelfLife, unit]);
        existingUpcs.add(upc);
      }

      syncedCount++;
    });

    return responseJson({
      status: "success",
      success: true,
      message: `Successfully synced ${syncedCount} scan record(s) to ${tabName} & Master Catalog`,
      syncedCount: syncedCount
    });

  } catch (error) {
    return responseJson({
      status: "error",
      success: false,
      message: error.toString()
    });
  }
}

function responseJson(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
