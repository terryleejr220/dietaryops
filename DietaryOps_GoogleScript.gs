/**
 * Century Villa Health Care — Dietary Operations Backend
 * Google Apps Script Web App for Barcode Receiving Scanner & Live Inventory Sync
 * Spreadsheet: CV_Dietary_Inventory_2026
 * Repository: terryleejr220/dietaryops
 *
 * Updated: Oct 4, 2026
 * - [SOURCE-OF-TRUTH] Master Catalog is now the primary backend data holder
 * - [SAFE-MERGE] Safe, non-destructive merge (never deletes pre-seed data)
 * - [SCHEMA-ALIGNED] Perfectly aligned with Master Catalog layout (Vendor, Item#, UPC, Name, Brand, Unit)
 * - [LIVE-SYNC] doGet() reads Master Catalog + live counts from Inventory Raw
 * - [LOCK-SAFE] Rollover and Count Walk synchronization intact
 */

const SPREADSHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY";
const CACHE_TTL_SECONDS = 3600; // 1 hour

// ─── Spreadsheet Access ────────────────────────────────────────────────────────

function getSpreadsheet() {
  return SpreadsheetApp.openById(SPREADSHEET_ID);
}

// ─── Shared Helpers ────────────────────────────────────────────────────────────

function jsonResponse(status, message) {
  return ContentService
    .createTextOutput(JSON.stringify({ status: status, message: message }))
    .setMimeType(ContentService.MimeType.JSON);
}

function getOrCreateSheet(ss, name, headers) {
  let sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    if (headers && headers.length) sheet.appendRow(headers);
  }
  return sheet;
}

function cap(val, len) {
  return String(val || "").substring(0, len);
}

function getShelfLifeForCategory(category) {
  const cat = String(category || "").toLowerCase();
  if (cat.indexOf("dairy") !== -1 || cat.indexOf("fresh") !== -1 || cat.indexOf("cooler") !== -1) return 7;
  if (cat.indexOf("protein") !== -1 || cat.indexOf("frozen") !== -1 || cat.indexOf("freezer") !== -1) return 14;
  return 365;
}

// ─── Barcode / UPC Lookup Cache ────────────────────────────────────────────────

function getBarcodeMap() {
  const cache  = CacheService.getScriptCache();
  const cached = cache.get("barcode_map");
  if (cached) {
    try { return JSON.parse(cached); } catch (e) {}
  }

  const ss    = getSpreadsheet();
  const sheet = ss.getSheetByName("Master Catalog") || ss.getSheetByName("Inventory Raw") || ss.getSheets()[0];
  const data  = sheet.getDataRange().getValues();
  const map   = {};

  const isMasterCatalog = sheet.getName() === "Master Catalog";

  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    let upc = "";
    let itemName = "";
    let location = "Dry Storage";

    if (isMasterCatalog) {
      // Master Catalog: Col C (index 2) is UPC, Col D (index 3) is Product Name, Col E (index 4) is Location/Brand
      upc      = String(row[2] || "").trim();
      itemName = String(row[3] || "").trim();
      location = String(row[4] || "").trim();
    } else {
      itemName = String(row[0] || "").trim(); // Col A — Item Name
      location = String(row[3] || "").trim(); // Col D — Location
      upc      = String(row[8] || "").trim(); // Col I — UPC
    }

    if (upc && itemName) {
      map[upc] = { name: itemName, storageArea: location || "Dry Storage" };
    }
  }

  try { cache.put("barcode_map", JSON.stringify(map), CACHE_TTL_SECONDS); } catch (e) {}
  return map;
}

function clearBarcodeCache() {
  CacheService.getScriptCache().remove("barcode_map");
  Logger.log("Barcode cache cleared.");
}

// ─── Auto-Register Scanned Items into Master Catalog ──────────────────────────

function ensureInMasterCatalog(ss, upc, name, storageArea, unit, itemNum) {
  const mcSheet = ss.getSheetByName("Master Catalog");
  if (!mcSheet || mcSheet.getLastRow() < 1) return;

  const cleanUpc  = String(upc || "").trim();
  const cleanName = String(name || "").trim();
  if (!cleanUpc && !cleanName) return;

  const data = mcSheet.getDataRange().getValues();
  for (let r = 1; r < data.length; r++) {
    const existingUpc  = String(data[r][2] || "").trim(); // Col C - UPC
    const existingName = String(data[r][3] || "").trim().toLowerCase(); // Col D - Name
    if (cleanUpc && existingUpc === cleanUpc) return; // already in catalog
    if (cleanName && existingName === cleanName.toLowerCase()) return; // already in catalog
  }

  const category = storageArea || "General";
  const shelfLife = getShelfLifeForCategory(category);

  // Append new item to Master Catalog
  // Master Catalog Layout: Col A=Vendor, Col B=Item#, Col C=UPC, Col D=Name, Col E=Location, Col F=Unit, Col G=Par, Col H=ShelfLife
  mcSheet.appendRow([
    "Sysco",
    itemNum || "",
    cleanUpc,
    cleanName,
    storageArea || "Dry Storage",
    unit || "EA",
    "",
    shelfLife
  ]);

  clearBarcodeCache();
}

// ─── SAFE NON-DESTRUCTIVE: Merge Missing Items Only ───────────────────────────

function syncMasterCatalogFromInventoryRaw() {
  const ss       = getSpreadsheet();
  const rawSheet = ss.getSheetByName("Inventory Raw") || ss.getSheetByName("Sheet1");
  const mcSheet  = ss.getSheetByName("Master Catalog");

  if (!rawSheet) throw new Error("Inventory Raw sheet not found.");
  if (!mcSheet)  throw new Error("Master Catalog sheet not found. Please create a sheet tab named 'Master Catalog'.");

  // 1. Read existing Master Catalog to build a set of existing UPCs and Names
  const mcData = mcSheet.getDataRange().getValues();
  const existingKeys = new Set();

  for (let r = 1; r < mcData.length; r++) {
    // Master Catalog layout: Col C (index 2) is UPC, Col D (index 3) is Product Name
    const existingUpc  = String(mcData[r][2] || "").trim();
    const existingName = String(mcData[r][3] || "").trim().toLowerCase();
    if (existingUpc)  existingKeys.add("upc:" + existingUpc);
    if (existingName) existingKeys.add("name:" + existingName);
  }

  // 2. Scan Inventory Raw — ONLY collect items that do NOT already exist in Master Catalog
  const rawData = rawSheet.getDataRange().getValues();
  let currentCategory = "General";
  const newItemsToAppend = [];

  for (let i = 1; i < rawData.length; i++) {
    const row  = rawData[i];
    const colA = String(row[0] || "").trim(); // Item Name
    const colD = String(row[3] || "").trim(); // Marker / Unit
    const colE = String(row[4] || "").trim(); // On Menu
    const colF = String(row[5] || "").trim(); // Par
    const colG = String(row[6] || "").trim(); // Location
    const colH = String(row[7] || "").trim(); // Item #
    const colI = String(row[8] || "").trim(); // UPC
    const colJ = String(row[9] || "").trim(); // Pack Size

    if (!colA) continue;
    if (colD === "Y" || colD === "y" || colD === "1") {
      currentCategory = colA;
      continue;
    }
    if (colA.indexOf("Download Century Villa") !== -1 || colA.indexOf("APK") !== -1) continue;

    const hasUpc  = colI && existingKeys.has("upc:" + colI);
    const hasName = existingKeys.has("name:" + colA.toLowerCase());

    // SKIP if already in Master Catalog — NEVER overwrite pre-seed data!
    if (hasUpc || hasName) {
      continue;
    }

    // New missing item to append at the bottom:
    // Format matches Master Catalog: Col A=Vendor, Col B=Item#, Col C=UPC, Col D=Name, Col E=Location, Col F=Unit
    newItemsToAppend.push([
      "Sysco", colH, colI, colA, colG || currentCategory,
      colD || "EA", colF, getShelfLifeForCategory(currentCategory), colJ, colE
    ]);

    // Track it so duplicates within Inventory Raw don't get added twice
    if (colI) existingKeys.add("upc:" + colI);
    existingKeys.add("name:" + colA.toLowerCase());
  }

  // 3. Append ONLY new items to the bottom of Master Catalog
  if (newItemsToAppend.length > 0) {
    const startRow = mcSheet.getLastRow() + 1;
    mcSheet.getRange(startRow, 1, newItemsToAppend.length, newItemsToAppend[0].length).setValues(newItemsToAppend);
  }

  clearBarcodeCache();
  return newItemsToAppend.length;
}

function menuSyncMasterCatalog() {
  const ui = SpreadsheetApp.getUi();
  const resp = ui.alert(
    "Safe Merge into Master Catalog",
    "This will ONLY add missing items to the bottom of 'Master Catalog'.\n\nYour existing pre-seed items will NOT be changed or deleted.\n\nContinue?",
    ui.ButtonSet.YES_NO
  );
  if (resp !== ui.Button.YES) return;

  const count = syncMasterCatalogFromInventoryRaw();
  ui.alert("Success!", "Added " + count + " new missing items to Master Catalog. Pre-seed items were preserved.", ui.ButtonSet.OK);
}

// ─── Sheets UI Menu ───────────────────────────────────────────────────────────

function onOpen() {
  try {
    const ui = SpreadsheetApp.getUi();
    ui.createMenu("Dietary Ops")
      .addItem("📋 Run Monday Inventory Rollover", "menuRunMondayRollover")
      .addItem("🔄 Safe Merge Missing Items into Master Catalog", "menuSyncMasterCatalog")
      .addItem("🗑️  Clear Barcode Cache", "clearBarcodeCache")
      .addToUi();
  } catch (e) {
    Logger.log("onOpen UI skipped: " + e.toString());
  }
}

// ─── Weekly Rollover ──────────────────────────────────────────────────────────

function executeRollover(isManual) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) return;
  try {
    const ss       = getSpreadsheet();
    const rawSheet = ss.getSheetByName("Inventory Raw") || ss.getSheetByName("Sheet1");
    if (!rawSheet) return;

    const lastRow = rawSheet.getLastRow();
    if (lastRow < 2) return;

    const currentRange = rawSheet.getRange(2, 2, lastRow - 1, 1);
    const priorRange   = rawSheet.getRange(2, 3, lastRow - 1, 1);
    priorRange.setValues(currentRange.getValues());
    currentRange.clearContent();

    const logSheet = ss.getSheetByName("Audit & Changes Log");
    if (logSheet) {
      let user = "Automated Monday Trigger";
      try { if (isManual) user = Session.getActiveUser().getEmail() || "Terry / Staff"; } catch (e) {}
      logSheet.appendRow([new Date(), user, "Weekly Inventory Rollover",
        "Shifted Current to Prior; reset Current to blank for new count"]);
    }
  } finally {
    lock.releaseLock();
  }
}

function menuRunMondayRollover() {
  const ui = SpreadsheetApp.getUi();
  const response = ui.alert(
    "Confirm Weekly Inventory Rollover",
    "Shift current counts to Prior On-Hand and reset for new week?\n\nDo you want to continue?",
    ui.ButtonSet.YES_NO
  );
  if (response !== ui.Button.YES) return;
  executeRollover(true);
  ui.alert("Success!", "Monday Rollover Complete.", ui.ButtonSet.OK);
}

// ─── Web App: GET (Loads full catalog into Android App) ────────────────────────

function doGet(e) {
  const ss       = getSpreadsheet();
  const mcSheet  = ss.getSheetByName("Master Catalog");
  const rawSheet = ss.getSheetByName("Inventory Raw") || ss.getSheetByName("Sheet1");

  // Read live counts from Inventory Raw: Map(name -> { onHand, previousCount, shelfOrder, category, location })
  const liveCountMap = new Map();
  const liveUpcMap   = new Map();

  if (rawSheet) {
    const rawData = rawSheet.getDataRange().getValues();
    let currentCategory = "General";
    for (let r = 1; r < rawData.length; r++) {
      const colA = String(rawData[r][0] || "").trim();
      const colD = String(rawData[r][3] || "").trim();
      const colI = String(rawData[r][8] || "").trim();

      if (!colA) continue;
      if (colD === "Y" || colD === "y" || colD === "1") {
        currentCategory = colA;
        continue;
      }
      if (colA.indexOf("Download Century Villa") !== -1 || colA.indexOf("APK") !== -1) continue;

      const itemInfo = {
        onHand:        String(rawData[r][1] || "").trim(),
        previousCount: String(rawData[r][2] || "").trim(),
        shelfOrder:    r,
        category:      currentCategory,
        location:      String(rawData[r][6] || currentCategory).trim()
      };

      liveCountMap.set(colA.toLowerCase(), itemInfo);
      if (colI) liveUpcMap.set(colI, itemInfo);
    }
  }

  const inventoryItems = [];

  // IF Master Catalog exists and has items: use it as PRIMARY SOURCE OF TRUTH
  if (mcSheet && mcSheet.getLastRow() > 1) {
    const mcData = mcSheet.getDataRange().getValues();
    const headerRow = mcData[0].map(function(h) { return String(h || "").trim().toLowerCase(); });

    function findCol(candidates, fallbackIndex) {
      for (let c = 0; c < candidates.length; c++) {
        const idx = headerRow.findIndex(function(h) {
          return h === candidates[c] || h.indexOf(candidates[c]) !== -1;
        });
        if (idx !== -1) return idx;
      }
      return fallbackIndex;
    }

    const vendorCol  = findCol(["vendor", "distributor", "supplier"], 0);
    const itemNumCol = findCol(["item #", "item number", "item_number", "sku", "supc"], 1);
    const upcCol     = findCol(["upc", "barcode", "gtin", "code"], 2);
    const nameCol    = findCol(["product name", "item name", "description", "product", "name"], 3);
    const locCol     = findCol(["storage area", "storage", "location", "brand"], 4);
    const unitCol    = findCol(["unit", "pack", "pack size", "uom"], 5);
    const catCol     = findCol(["category", "dept", "department"], -1);
    const parCol     = findCol(["par", "par level"], -1);
    const shelfCol   = findCol(["shelf life", "shelf life days", "days"], -1);

    for (let i = 1; i < mcData.length; i++) {
      const row = mcData[i];
      const vendor   = String(row[vendorCol] || "").trim();
      const itemNum  = String(row[itemNumCol] || "").trim();
      const upc      = String(row[upcCol] || "").trim();
      const name     = String(row[nameCol] || "").trim();
      const brandLoc = String(row[locCol] || "").trim();
      const unit     = String(row[unitCol] || "EA").trim();

      if (!name && !upc) continue;

      // Match live counts from Inventory Raw by UPC first, then Name
      const live = (upc && liveUpcMap.get(upc)) || liveCountMap.get(name.toLowerCase()) || null;

      // Determine Category: prefer explicit category column, then Inventory Raw, then fallback
      let category = (catCol !== -1 && String(row[catCol] || "").trim()) || "";
      if (!category && live && live.category) {
        category = live.category;
      }
      if (!category) {
        category = brandLoc || vendor || "General";
      }

      // Determine Location
      const location = (live && live.location) || brandLoc || "Dry Storage";

      // Determine Shelf Life
      let shelfDays = (shelfCol !== -1 ? parseInt(row[shelfCol], 10) : 0);
      if (!shelfDays || isNaN(shelfDays)) {
        shelfDays = getShelfLifeForCategory(category);
      }

      // Determine Par
      const par = (parCol !== -1 ? String(row[parCol] || "") : "");

      inventoryItems.push({
        category:             category,
        name:                 name,
        onHand:               live ? live.onHand : "",
        previousCount:        live ? live.previousCount : "",
        location:             location,
        upc:                  upc,
        syscoItemNumber:      itemNum,
        unit:                 unit,
        defaultShelfLifeDays: shelfDays,
        parLevel:             parseFloat(par) || 0.0,
        shelfOrderIndex:      live ? live.shelfOrder : (1000 + i)
      });
    }

    return ContentService
      .createTextOutput(JSON.stringify(inventoryItems))
      .setMimeType(ContentService.MimeType.JSON);
  }

  // Fallback: Read directly from Inventory Raw if Master Catalog not yet populated
  if (rawSheet) {
    const data = rawSheet.getDataRange().getValues();
    let currentCategory = "General";

    for (let i = 1; i < data.length; i++) {
      const row  = data[i];
      const colA = String(row[0] || "").trim();
      const colB = String(row[1] || "").trim();
      const colC = String(row[2] || "").trim();
      const colD = String(row[3] || "").trim();
      const colI = String(row[8] || "").trim();

      if (!colA) continue;
      if (colD === "Y" || colD === "y" || colD === "1") {
        currentCategory = colA;
        continue;
      }
      if (colA.indexOf("Download Century Villa") !== -1 || colA.indexOf("APK") !== -1) continue;

      inventoryItems.push({
        category:        currentCategory,
        name:            colA,
        onHand:          colB,
        previousCount:   colC,
        location:        colD || currentCategory,
        upc:             colI,
        shelfOrderIndex: i
      });
    }

    return ContentService
      .createTextOutput(JSON.stringify(inventoryItems))
      .setMimeType(ContentService.MimeType.JSON);
  }

  return jsonResponse("error", "No inventory data found in spreadsheet.");
}

// ─── Web App: POST (Batch sync from Android App + Delivery Scans) ──────────────

function doPost(e) {
  let params = {};
  if (e && e.postData && e.postData.contents) {
    try {
      params = JSON.parse(e.postData.contents);
    } catch (err) {
      params = e.parameter || {};
    }
  } else if (e && e.parameter) {
    params = e.parameter;
  }

  const ss = getSpreadsheet();

  // Route 1: Android SheetSyncPayload (Count Walk OR Delivery Scans)
  if (params.records && Array.isArray(params.records) && params.records.length > 0) {
    const tabName = params.sheetTab || "";

    // Count Walk targeting Inventory Raw
    if (tabName.indexOf("Inventory") !== -1 || tabName.indexOf("Raw") !== -1 || tabName.indexOf("Sheet1") !== -1 || tabName === "") {
      const sheet = ss.getSheetByName(tabName) || ss.getSheetByName("Inventory Raw") || ss.getSheetByName("Sheet1");
      if (!sheet) {
        return jsonResponse("error", "Target sheet not found: " + tabName);
      }

      const data = sheet.getDataRange().getValues();
      const nameMap = new Map();
      const upcMap = new Map();

      for (let r = 1; r < data.length; r++) {
        const name = String(data[r][0] || "").trim().toLowerCase();
        const upc = String(data[r][8] || "").trim();
        if (name) nameMap.set(name, r + 1);
        if (upc) upcMap.set(upc, r + 1);
      }

      const itemsToUpdate = params.records || params.items || [];
      let updatedCount = 0;

      itemsToUpdate.forEach(function(item) {
        const cleanName = String(item.name || item.itemName || "").trim().toLowerCase();
        const cleanUpc = String(item.upc || item.syscoUpc || "").trim();
        const qty = item.onHandQty !== undefined ? item.onHandQty : item.onHand;

        let targetRow = null;
        if (cleanUpc && upcMap.has(cleanUpc)) {
          targetRow = upcMap.get(cleanUpc);
        } else if (cleanName && nameMap.has(cleanName)) {
          targetRow = nameMap.get(cleanName);
        }

        if (targetRow && qty !== undefined && qty !== null) {
          sheet.getRange(targetRow, 2).setValue(qty);
          updatedCount++;
        }
      });

      return ContentService.createTextOutput(JSON.stringify({
        success: true,
        status: "success",
        message: "Updated " + updatedCount + " items in " + tabName,
        syncedCount: updatedCount
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // Delivery Scans Batch
    const logSheet = getOrCreateSheet(ss, tabName || "Delivery Scan Log", [
      "Timestamp", "UPC", "Product Name", "Delivery Date", "Use By Date", "Storage Area", "Received By", "Status"
    ]);

    params.records.forEach(function(rec) {
      logSheet.appendRow([
        new Date(),
        cap(rec.upc || rec.syscoUpc, 50),
        cap(rec.name || rec.itemName, 100),
        cap(rec.deliveryDate, 30),
        cap(rec.useByDate, 30),
        cap(rec.storageArea, 50),
        cap(rec.receivedBy, 50),
        "Received"
      ]);

      // Auto-register new product into Master Catalog if missing
      ensureInMasterCatalog(ss, rec.upc || rec.syscoUpc, rec.name || rec.itemName, rec.storageArea, rec.unit, rec.itemNumber || rec.syscoItemNumber);
    });

    return ContentService.createTextOutput(JSON.stringify({
      success: true,
      status: "success",
      message: "Logged " + params.records.length + " scans to " + tabName,
      syncedCount: params.records.length
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // Single Delivery Scan Fallback
  try {
    const rawUpc = params.upc || params.barcode || params.UPC || params.itemNumber || params.item_number || "";
    let resolvedName    = params.name || params.productName || params["Product Name"] || params.product || "";
    let resolvedStorage = params.storageArea || params["Storage Area"] || "";

    if (rawUpc && !resolvedName) {
      const barcodeMap = getBarcodeMap();
      const match = barcodeMap[rawUpc.trim()];
      if (match) {
        resolvedName    = resolvedName    || match.name;
        resolvedStorage = resolvedStorage || match.storageArea;
      }
    }

    const logSheet = getOrCreateSheet(ss, "Delivery Scan Log", [
      "Timestamp", "UPC", "Product Name", "Delivery Date", "Use By Date", "Storage Area", "Received By", "Status"
    ]);

    logSheet.appendRow([
      new Date(),
      cap(rawUpc, 50),
      cap(resolvedName, 100),
      cap(params.deliveryDate, 30),
      cap(params.useByDate, 30),
      cap(resolvedStorage || "Dry Storage", 50),
      cap(params.receivedBy || "Terry", 50),
      cap(params.status || "Received", 30)
    ]);

    // Auto-register new product into Master Catalog if missing
    ensureInMasterCatalog(ss, rawUpc, resolvedName, resolvedStorage, params.unit, params.itemNumber);

    return ContentService.createTextOutput(JSON.stringify({
      success: true,
      status: "success",
      message: "Delivery scan logged successfully",
      logged: resolvedName || rawUpc
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return jsonResponse("error", err.toString());
  }
}
