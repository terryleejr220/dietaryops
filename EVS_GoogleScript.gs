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

function getSpreadsheet(targetId) {
  try {
    if (targetId && String(targetId).trim().length > 10) {
      return SpreadsheetApp.openById(String(targetId).trim());
    }
  } catch (e) {}
  try {
    const active = SpreadsheetApp.getActiveSpreadsheet();
    if (active) return active;
  } catch (e) {}
  return SpreadsheetApp.openById(FALLBACK_SPREADSHEET_ID);
}

function doGet(e) {
  try {
    const action = e && e.parameter ? (e.parameter.action || "").toLowerCase() : "";
    if (action === "fetch_catalog" || action === "get_catalog") {
      const targetId = e.parameter.spreadsheetId || FALLBACK_SPREADSHEET_ID;
      const ss = getSpreadsheet(targetId);
      const targetDept = (e.parameter.department || "").toLowerCase();
      const isEvsCatalog = targetDept.includes("evs") || (e.parameter.sheetTab && e.parameter.sheetTab.toLowerCase().includes("evs"));
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
    const action = (rawData.action || "").toLowerCase();

    // Action A: Fetch catalog as JSON array
    if (action === "fetch_catalog" || action === "get_catalog") {
      const ss = getSpreadsheet(rawData.spreadsheetId);
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
      const ss = getSpreadsheet(rawData.spreadsheetId);
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

    // Default Action: Scan Records / Count Sheet Sync
    let targetSheetId = FALLBACK_SPREADSHEET_ID;
    let deptName = "Dietary";
    let records = [];

    if (Array.isArray(rawData)) {
      records = rawData;
    } else if (typeof rawData === "object") {
      targetSheetId = rawData.spreadsheetId || FALLBACK_SPREADSHEET_ID;
      deptName = rawData.department || "Dietary";
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
      return responseJson({ status: "success", success: true, message: "No scan records to process", syncedCount: 0 });
    }

    const ss = getSpreadsheet(targetSheetId);
    const isEvs = (deptName || "").toLowerCase().includes("evs") || (rawData.sheetTab || "").toLowerCase().includes("evs");

    // Department Tab Routing
    const defaultLogTab = isEvs ? "EVS Scan Log" : "Delivery Scan Log";
    const defaultInvTab = isEvs ? "EVS Inventory" : "Inventory Raw";
    const tabName = rawData.sheetTab || defaultLogTab;

    // 1. Log Sheet Setup
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

    // 2. Count Sheet / Master Inventory Sheet Setup
    let rawInventorySheet = ss.getSheetByName(defaultInvTab) || ss.getSheetByName("Inventory Raw") || ss.getSheetByName("CV inventory 226") || ss.getSheetByName("Master Catalog");
    if (!rawInventorySheet) {
      rawInventorySheet = ss.insertSheet(defaultInvTab);
      rawInventorySheet.appendRow(["UPC", "Item Name", "Category", "On Hand Quantity", "Unit", "Last Count Date", "Counted By"]);
      rawInventorySheet.getRange("A1:G1").setFontWeight("bold").setBackground("#c9daf8");
      rawInventorySheet.setFrozenRows(1);
    }

    // Map existing UPCs in Inventory sheet for live row updates
    const invData = rawInventorySheet.getDataRange().getValues();
    const upcToRowMap = {};
    for (let i = 1; i < invData.length; i++) {
      const upcKey = String(invData[i][0]).trim();
      if (upcKey) upcToRowMap[upcKey] = i + 1;
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

      // Append to Log Sheet
      logSheet.appendRow([
        ts, receivedBy, itemName, upc, category, qty, unit, useByDate, storageArea
      ]);

      // Populate / Update Inventory Count Sheet
      if (upc && upcToRowMap[upc]) {
        const rowIdx = upcToRowMap[upc];
        rawInventorySheet.getRange(rowIdx, 4).setValue(qty);
        rawInventorySheet.getRange(rowIdx, 6).setValue(dateStr);
        rawInventorySheet.getRange(rowIdx, 7).setValue(receivedBy);
      } else if (upc) {
        rawInventorySheet.appendRow([upc, itemName, category, qty, unit, dateStr, receivedBy]);
        upcToRowMap[upc] = rawInventorySheet.getLastRow();
      }

      syncedCount++;
    });

    return responseJson({
      status: "success",
      success: true,
      message: `Successfully updated ${defaultInvTab} & ${tabName} (${syncedCount} items)`,
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
