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

function doGet(e) {
  try {
    const action = e && e.parameter && e.parameter.action ? e.parameter.action.toLowerCase() : "status";
    if (action === "ping" || action === "status") {
      return responseJson({
        status: "online",
        service: "DietaryOps & EVS Cloud Integration API",
        version: "2.0.0",
        timestamp: new Date().toISOString()
      });
    }
    if (action === "fetch_catalog" || action === "get_catalog") {
      const ssId = (e && e.parameter && e.parameter.spreadsheetId) ? e.parameter.spreadsheetId : FALLBACK_SPREADSHEET_ID;
      const dept = (e && e.parameter && e.parameter.department) ? e.parameter.department.toLowerCase() : "";
      const isEvs = dept.includes("evs");
      const ss = SpreadsheetApp.openById(ssId);
      const invSheet = isEvs 
        ? (ss.getSheetByName("EVS Inventory") || ss.getSheetByName("EVS Raw"))
        : (ss.getSheetByName("Inventory Raw") || ss.getSheetByName("CV inventory 226") || ss.getSheetByName("Master Catalog"));
      if (!invSheet) return responseJson([]);
      const values = invSheet.getDataRange().getValues();
      if (values.length <= 1) return responseJson([]);
      const headers = values[0].map(h => String(h).trim().toLowerCase());
      const upcCol = headers.findIndex(h => h.includes("upc") || h.includes("barcode") || h.includes("code"));
      const nameCol = headers.findIndex(h => h.includes("name") || h.includes("item") || h.includes("product") || h.includes("description"));
      const catCol = headers.findIndex(h => h.includes("category") || h.includes("location") || h.includes("storage"));
      const qtyCol = headers.findIndex(h => h.includes("quantity") || h.includes("on hand") || h.includes("qty") || h.includes("count"));
      const unitCol = headers.findIndex(h => h.includes("unit") || h.includes("uom"));
      const catalogList = [];
      for (let r = 1; r < values.length; r++) {
        const row = values[r];
        const itemUpc = upcCol >= 0 ? String(row[upcCol]).trim() : "";
        const itemName = nameCol >= 0 ? String(row[nameCol]).trim() : "";
        if (!itemUpc && !itemName) continue;
        catalogList.push({
          syscoUpc: itemUpc,
          name: itemName,
          category: catCol >= 0 ? String(row[catCol]).trim() : "General",
          lastOnHandAmount: qtyCol >= 0 && !isNaN(parseFloat(row[qtyCol])) ? parseFloat(row[qtyCol]) : 1.0,
          unit: unitCol >= 0 && String(row[unitCol]).trim() ? String(row[unitCol]).trim() : "EA",
          defaultShelfLifeDays: 365
        });
      }
      return responseJson(catalogList);
    }
    return responseJson({
      status: "online",
      service: "DietaryOps & EVS Cloud Integration API",
      hint: "Use POST for syncing scans and fetching catalogs, or GET ?action=ping"
    });
  } catch (err) {
    return responseJson({ status: "error", message: err.toString() });
  }
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return responseJson({ status: "error", message: "No post data received" });
    }

    const rawData = JSON.parse(e.postData.contents);
    // 1. Action Routing (fetch_catalog, log_error, or default scan records)
    const action = (rawData.action || "").toLowerCase();

    // Action A: Fetch catalog as JSON array
    if (action === "fetch_catalog" || action === "get_catalog") {
      const ss = SpreadsheetApp.openById(rawData.spreadsheetId || FALLBACK_SPREADSHEET_ID);
      const targetDept = (rawData.department || "").toLowerCase();
      const isEvsCatalog = targetDept.includes("evs") || (rawData.sheetTab && rawData.sheetTab.toLowerCase().includes("evs"));
      const invSheet = isEvsCatalog
        ? (ss.getSheetByName("EVS Inventory") || ss.getSheetByName("EVS Raw"))
        : (ss.getSheetByName("Inventory Raw") || ss.getSheetByName("CV inventory 226") || ss.getSheetByName("Master Catalog"));
      if (!invSheet) {
        return responseJson([]);
      }
      const values = invSheet.getDataRange().getValues();
      if (values.length <= 1) {
        return responseJson([]);
      }
      const headers = values[0].map(h => String(h).trim().toLowerCase());
      const upcCol = headers.findIndex(h => h.includes("upc") || h.includes("barcode") || h.includes("code"));
      const nameCol = headers.findIndex(h => h.includes("name") || h.includes("item") || h.includes("product") || h.includes("description"));
      const catCol = headers.findIndex(h => h.includes("category") || h.includes("location") || h.includes("storage"));
      const qtyCol = headers.findIndex(h => h.includes("quantity") || h.includes("on hand") || h.includes("qty") || h.includes("count"));
      const unitCol = headers.findIndex(h => h.includes("unit") || h.includes("uom"));

      const catalogList = [];
      for (let r = 1; r < values.length; r++) {
        const row = values[r];
        const itemUpc = upcCol >= 0 ? String(row[upcCol]).trim() : "";
        const itemName = nameCol >= 0 ? String(row[nameCol]).trim() : "";
        if (!itemUpc && !itemName) continue;
        catalogList.push({
          syscoUpc: itemUpc,
          name: itemName,
          category: catCol >= 0 ? String(row[catCol]).trim() : "General",
          lastOnHandAmount: qtyCol >= 0 && !isNaN(parseFloat(row[qtyCol])) ? parseFloat(row[qtyCol]) : 1.0,
          unit: unitCol >= 0 && String(row[unitCol]).trim() ? String(row[unitCol]).trim() : "EA",
          defaultShelfLifeDays: 365
        });
      }
      return responseJson(catalogList);
    }

    // Action B: Log client errors to dedicated Error Log tab
    if (action === "log_error") {
      const ss = SpreadsheetApp.openById(rawData.spreadsheetId || FALLBACK_SPREADSHEET_ID);
      let errSheet = ss.getSheetByName("Error Log");
      if (!errSheet) {
        errSheet = ss.insertSheet("Error Log");
        errSheet.appendRow(["Timestamp", "Device", "Tag", "Message", "Details"]);
        errSheet.getRange("A1:E1").setFontWeight("bold").setBackground("#f4cccc");
        errSheet.setFrozenRows(1);
      }
      errSheet.appendRow([
        new Date(),
        rawData.device || "Android",
        rawData.tag || "ERROR",
        rawData.message || "Unknown error",
        rawData.details || ""
      ]);
      return responseJson({ status: "success", message: "Error logged" });
    }

    // Default Action: Scan Records Sync
    let targetSheetId = FALLBACK_SPREADSHEET_ID;
    let tabName = "Delivery Scan Log";
    let records = [];

    // Extract Spreadsheet ID, Tab Name, and Records list
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
    const targetDept = (rawData.department || "").toLowerCase();
    const isEvs = tabName.toLowerCase().includes("evs") || targetDept.includes("evs");
    const invSheetName = isEvs ? "EVS Inventory" : "Inventory Raw";

    // 2. A. Populates / Updates Count Sheet directly (Total isolation: Dietary vs EVS)
    let rawInventorySheet = isEvs
      ? (ss.getSheetByName("EVS Inventory") || ss.getSheetByName("EVS Raw"))
      : (ss.getSheetByName("Inventory Raw") || ss.getSheetByName("CV inventory 226") || ss.getSheetByName("Master Catalog"));

    if (!rawInventorySheet) {
      rawInventorySheet = ss.insertSheet(invSheetName);
      rawInventorySheet.appendRow(["UPC", "Item Name", "Category", "On Hand Quantity", "Unit", "Last Count Date", "Counted By"]);
      rawInventorySheet.getRange("A1:G1").setFontWeight("bold").setBackground(isEvs ? "#d0e0e3" : "#c9daf8");
      rawInventorySheet.setFrozenRows(1);
    }

    // Map existing UPCs in Count Sheet for live row updates
    const invData = rawInventorySheet.getDataRange().getValues();
    const upcToRowMap = {};
    for (let i = 1; i < invData.length; i++) {
      const upcKey = String(invData[i][0]).trim();
      if (upcKey) upcToRowMap[upcKey] = i + 1;
    }

    // Get or Create Department Audit Log Sheet
    let logSheet = ss.getSheetByName(tabName);
    if (!logSheet) {
      logSheet = ss.insertSheet(tabName);
      logSheet.appendRow([
        "Timestamp", "Received By", "Item Name", "UPC",
        "Category", "Quantity", "Unit", "Use By Date", "Storage Location"
      ]);
      logSheet.getRange("A1:I1").setFontWeight("bold").setBackground(isEvs ? "#cfe2f3" : "#d9ead3");
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

      // 1. Append to Department Scan Log
      logSheet.appendRow([
        ts, receivedBy, itemName, upc, category, qty, unit, useByDate, storageArea
      ]);

      // 2. Populate / Update Count Sheet (Zero Handwriting Needed)
      if (upc && upcToRowMap[upc]) {
        // Update existing item row
        const rowIdx = upcToRowMap[upc];
        rawInventorySheet.getRange(rowIdx, 4).setValue(qty); // On Hand Quantity
        rawInventorySheet.getRange(rowIdx, 6).setValue(dateStr); // Last Count Date
        rawInventorySheet.getRange(rowIdx, 7).setValue(receivedBy); // Counted By
      } else if (upc) {
        // Append new item to Count Sheet
        rawInventorySheet.appendRow([upc, itemName, category, qty, unit, dateStr, receivedBy]);
        upcToRowMap[upc] = rawInventorySheet.getLastRow();
      }

      syncedCount++;
    });

    return responseJson({
      status: "success",
      success: true,
      message: `Successfully updated ${invSheetName} count sheet & ${tabName} (${syncedCount} items)`,
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
