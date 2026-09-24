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

    // 2. A. Populates / Updates "Inventory Raw" Sheet directly for Count Sheet Sync
    let rawInventorySheet = ss.getSheetByName("Inventory Raw") || ss.getSheetByName("CV inventory 226") || ss.getSheetByName("Master Catalog");
    if (!rawInventorySheet) {
      rawInventorySheet = ss.insertSheet("Inventory Raw");
      rawInventorySheet.appendRow(["UPC", "Item Name", "Category", "On Hand Quantity", "Unit", "Last Count Date", "Counted By"]);
      rawInventorySheet.getRange("A1:G1").setFontWeight("bold").setBackground("#c9daf8");
      rawInventorySheet.setFrozenRows(1);
    }

    // Map existing UPCs in Inventory Raw for live row updates
    const invData = rawInventorySheet.getDataRange().getValues();
    const upcToRowMap = {};
    for (let i = 1; i < invData.length; i++) {
      const upcKey = String(invData[i][0]).trim();
      if (upcKey) upcToRowMap[upcKey] = i + 1;
    }

    // Get or Create Audit Log Sheet (Delivery Scan Log)
    let logSheet = ss.getSheetByName(tabName);
    if (!logSheet) {
      logSheet = ss.insertSheet(tabName);
      logSheet.appendRow([
        "Timestamp", "Received By", "Item Name", "UPC",
        "Category", "Quantity", "Unit", "Use By Date", "Storage Location"
      ]);
      logSheet.getRange("A1:I1").setFontWeight("bold").setBackground("#d9ead3");
      logSheet.setFrozenRows(1);
    }

    let syncedCount = 0;

    // Process each record
    records.forEach(function(rec) {
      const upc = String(rec.upc || rec.syscoUpc || rec.barcode || "").trim();
      const itemName = String(rec.name || rec.itemName || rec.title || "Scanned Item").trim();
      const category = String(rec.category || rec.storageArea || "General").trim();
      const qty = rec.onHandQty !== undefined ? rec.onHandQty : (rec.onHandAmount !== undefined ? rec.onHandAmount : (rec.lastOnHandAmount !== undefined ? rec.lastOnHandAmount : 1.0));
      const unit = String(rec.unit || "EA").trim();
      const receivedBy = String(rec.receivedBy || rec.staff || "Staff").trim();
      const useByDate = String(rec.useByDate || rec.expirationDate || "").trim();
      const storageArea = String(rec.storageArea || rec.storageLocation || "").trim();
      const ts = rec.scanTimestamp ? new Date(rec.scanTimestamp) : new Date();
      const dateStr = ts.toLocaleDateString();

      // 1. Append to Delivery Scan Log
      logSheet.appendRow([
        ts, receivedBy, itemName, upc, category, qty, unit, useByDate, storageArea
      ]);

      // 2. Populate / Update "Inventory Raw" Count Sheet (Zero Handwriting Needed)
      if (upc && upcToRowMap[upc]) {
        // Update existing item row
        const rowIdx = upcToRowMap[upc];
        rawInventorySheet.getRange(rowIdx, 4).setValue(qty); // On Hand Quantity
        rawInventorySheet.getRange(rowIdx, 6).setValue(dateStr); // Last Count Date
        rawInventorySheet.getRange(rowIdx, 7).setValue(receivedBy); // Counted By
      } else if (upc) {
        // Append new item to Inventory Raw
        rawInventorySheet.appendRow([upc, itemName, category, qty, unit, dateStr, receivedBy]);
        upcToRowMap[upc] = rawInventorySheet.getLastRow();
      }

      syncedCount++;
    });

    return responseJson({
      status: "success",
      success: true,
      message: `Successfully updated Inventory Raw count sheet & Delivery Scan Log (${syncedCount} items)`,
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
