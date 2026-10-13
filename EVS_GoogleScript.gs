/**
 * Century Villa Health Care — Dietary Operations Backend
 * Google Apps Script Web App for Barcode Receiving Scanner & Live Inventory Sync
 * Spreadsheet: CV_Dietary_Inventory_2026
 * Repository: terryleejr220/dietaryops
 *
 * High-Speed CacheService Barcode Lookup (~5ms) + Live Delivery & Inventory Sync
 */

const SPREADSHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY";

// ─── Spreadsheet Access ────────────────────────────────────────────────────────

function getSpreadsheet() {
  return SpreadsheetApp.openById(SPREADSHEET_ID);
}

// ─── Shared Helper ─────────────────────────────────────────────────────────────

function jsonResponse(status, message, extraData) {
  const payload = Object.assign({ status: status, message: message }, extraData || {});
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

// ─── Sheets UI Menu ───────────────────────────────────────────────────────────

function onOpen() {
  try {
    const ui = SpreadsheetApp.getUi();
    ui.createMenu("Dietary Ops")
      .addItem("📋 Run Monday Inventory Rollover", "menuRunMondayRollover")
      .addItem("🧹 Clear Barcode Cache", "clearBarcodeCache")
      .addToUi();
  } catch (e) {
    Logger.log("onOpen UI skipped: " + e.toString());
  }
}

// ─── Barcode CacheService Lookup (~5ms Instant) ─────────────────────────────────

/**
 * Clears the script cache when UPCs in Column E are updated in the sheet.
 * Can be run from the Apps Script editor or the Dietary Ops menu.
 */
function clearBarcodeCache() {
  const cache = CacheService.getScriptCache();
  try {
    cache.removeAll(["barcode_catalog_loaded"]);
    Logger.log("Barcode Cache cleared successfully.");
  } catch (e) {
    Logger.log("Barcode Cache clear skipped: " + e.toString());
  }
}

/**
 * Resolves a UPC to its Product Name & Storage Area using CacheService (~5ms).
 * Reads Column E (UPC), Column A (Name), and Column D (Storage Area) from Sheet1 / Inventory Raw.
 */
function resolveBarcodeFromSheet(upcInput) {
  if (!upcInput) return { resolvedFromCache: false, name: "", storageArea: "" };

  const cleanUpc = String(upcInput).trim().replace(new RegExp("[^0-9]", "g"), "");
  if (!cleanUpc) return { resolvedFromCache: false, name: "", storageArea: "" };

  const cache = CacheService.getScriptCache();
  const cacheKey = "upc_" + cleanUpc;

  // 1. Check CacheService (Instant ~5ms hit)
  const cachedJson = cache.get(cacheKey);
  if (cachedJson) {
    try {
      const parsed = JSON.parse(cachedJson);
      return {
        resolvedFromCache: true,
        name: parsed.name || "",
        storageArea: parsed.storageArea || "Dry Storage"
      };
    } catch (e) {
      // Fallthrough to live lookup on parse failure
    }
  }

  // 2. Cache Miss: Read sheet once and build UPC lookup map
  const ss = getSpreadsheet();
  const sheet = ss.getSheetByName("Sheet1") || ss.getSheetByName("Inventory Raw") || ss.getSheets()[0];
  const data = sheet.getDataRange().getValues();

  let resolvedName = "";
  let resolvedStorage = "Dry Storage";
  let currentCategory = "General";

  const cacheBatch = {};

  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    const colA = String(row[0] || "").trim(); // Item Name / Category
    const colD = String(row[3] || "").trim(); // Storage Area / Category Marker "Y"
    const colE = String(row[4] || "").trim().replace(new RegExp("[^0-9]", "g"), ""); // Col E: UPC Barcode

    // Ignore empty rows, APK download links, and non-inventory rows
    if (!colA || colA.indexOf("📲") === 0 || colA.indexOf("Download") === 0 || colA.indexOf("http") === 0 || colA.toUpperCase().indexOf("APK") !== -1) {
      continue;
    }

    if (colD === "Y" || colD === "y" || colD === "1") {
      currentCategory = colA;
      continue;
    }

    const itemStorage = colD || currentCategory;

    if (colE) {
      const itemData = JSON.stringify({ name: colA, storageArea: itemStorage });
      cacheBatch["upc_" + colE] = itemData;

      if (colE === cleanUpc) {
        resolvedName = colA;
        resolvedStorage = itemStorage;
      }
    }
  }

  // Cache batch mappings for 1 hour (3600 seconds)
  try {
    cache.putAll(cacheBatch, 3600);
  } catch (e) {
    Logger.log("Cache putAll error: " + e.toString());
  }

  return {
    resolvedFromCache: false,
    name: resolvedName,
    storageArea: resolvedStorage
  };
}

// ─── Weekly Rollover ──────────────────────────────────────────────────────────

function executeRollover(isManual) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) {
    Logger.log("Rollover already running — skipping duplicate execution.");
    return;
  }
  try {
    const ss = getSpreadsheet();
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
    "Are you sure you want to run the Monday Inventory Rollover?\n\n" +
    "• Current counts will shift to Prior On-Hand.\n" +
    "• Current counts will be cleared for the new count sheet.\n\n" +
    "Do you want to continue?",
    ui.ButtonSet.YES_NO
  );
  if (response !== ui.Button.YES) {
    ui.alert("Rollover canceled. No changes were made.");
    return;
  }
  executeRollover(true);
  ui.alert("Success!",
    "Monday Rollover Complete.\n\nPrior counts updated, and Current On-Hand has been reset for this week's count.",
    ui.ButtonSet.OK);
}

function scheduledMondayRollover() {
  executeRollover(false);
}

// ─── Web App: GET ─────────────────────────────────────────────────────────────
//
// Sheet column layout:
//   Col A (index 0) — Item Name  OR  Category Label (if Col D = "Y")
//   Col B (index 1) — Current On-Hand count
//   Col C (index 2) — Prior On-Hand count
//   Col D (index 3) — Location / Storage Area  OR  "Y" to mark a category header row
//   Col E (index 4) — Barcode / UPC (Index 4)

function doGet(e) {
  const ss    = getSpreadsheet();
  const sheet = ss.getSheetByName("Sheet1") || ss.getSheetByName("Inventory Raw") || ss.getSheets()[0];
  const data  = sheet.getDataRange().getValues();

  let currentCategory = "General";
  const inventoryItems = [];

  for (let i = 1; i < data.length; i++) {
    const row  = data[i];
    const colA = String(row[0] || "").trim();
    const colB = String(row[1] || "").trim();
    const colC = String(row[2] || "").trim();
    const colD = String(row[3] || "").trim();
    const colE = String(row[4] || "").trim(); // Col E: Barcode / UPC

    // Ignore empty rows, APK download links, and non-inventory rows
    if (!colA || colA.indexOf("📲") === 0 || colA.indexOf("Download") === 0 || colA.indexOf("http") === 0 || colA.toUpperCase().indexOf("APK") !== -1) {
      continue;
    }

    if (colD === "Y" || colD === "y" || colD === "1") {
      currentCategory = colA;
      continue;
    }

    inventoryItems.push({
      category:      currentCategory,
      name:          colA,
      onHand:        colB,
      previousCount: colC,
      location:      colD || currentCategory,
      upc:           colE
    });
  }

  return ContentService
    .createTextOutput(JSON.stringify(inventoryItems))
    .setMimeType(ContentService.MimeType.JSON);
}

// ─── Web App: POST ────────────────────────────────────────────────────────────

function doPost(e) {
  if (!e || !e.postData || !e.postData.contents)
    return jsonResponse("error", "No postData received. doPost must be called via HTTP POST.");

  const ss = getSpreadsheet();
  let params = {};
  try {
    params = JSON.parse(e.postData.contents);
  } catch (err) {
    return jsonResponse("error", "Invalid JSON: " + err.toString());
  }

  const cap = function(val, len) { return String(val || "").substring(0, len); };

  // ── update_inventory ──────────────────────────────────────────────────────
  if (params.action === "update_inventory") {
    if (!Array.isArray(params.items))
      return jsonResponse("error", "update_inventory requires an 'items' array.");

    const sheet = ss.getSheetByName("Sheet1") || ss.getSheetByName("Inventory Raw") || ss.getSheets()[0];
    const data  = sheet.getDataRange().getValues();
    const rowMap = {};
    for (let r = 1; r < data.length; r++) {
      const name = String(data[r][0] || "").trim().toLowerCase();
      if (name) rowMap[name] = r + 1;
    }
    let updatedCount = 0;
    params.items.forEach(function(item) {
      const targetRow = rowMap[String(item.name || "").trim().toLowerCase()];
      if (targetRow) { sheet.getRange(targetRow, 2).setValue(item.onHand); updatedCount++; }
    });
    return jsonResponse("success", "Updated " + updatedCount + " items in Google Sheets");
  }

  // ── log_floor_note ────────────────────────────────────────────────────────
  if (params.action === "log_floor_note") {
    try {
      let notesSheet = ss.getSheetByName("Floor Notes");
      if (!notesSheet) {
        notesSheet = ss.insertSheet("Floor Notes");
        notesSheet.appendRow(["Timestamp", "Note Type", "Staff", "Device Model", "Note Content"]);
      }
      notesSheet.appendRow([
        new Date(),
        cap(params.noteType || "💡 Floor Note", 50),
        cap(params.staff    || "Floor Staff",   50),
        cap(params.device   || "Android Device", 50),
        cap(params.content  || params.message || "", 1000)
      ]);
      return ContentService.createTextOutput(JSON.stringify({
        status: "success", message: "Floor note logged to Google Sheets",
        loggedContent: cap(params.content || params.message || "", 100)
      })).setMimeType(ContentService.MimeType.JSON);
    } catch (err) { return jsonResponse("error", err.toString()); }
  }

  // ── log_error ─────────────────────────────────────────────────────────────
  if (params.action === "log_error") {
    try {
      let errorSheet = ss.getSheetByName("Error Logs");
      if (!errorSheet) {
        errorSheet = ss.insertSheet("Error Logs");
        errorSheet.appendRow(["Timestamp", "Tag", "Staff", "Device Model", "Error Message"]);
      }
      errorSheet.appendRow([
        new Date(),
        cap(params.tag     || "APP_ERROR",      30),
        cap(params.staff   || "System",          50),
        cap(params.device  || "Android Device",  50),
        cap(params.message || "Unknown error",  500)
      ]);
      return jsonResponse("success", "Error logged to Google Sheets");
    } catch (err) { return jsonResponse("error", err.toString()); }
  }

  // ── log_delivery (default — accepts barcode scan and resolves via CacheService) ─
  if (params.action && params.action !== "log_delivery") {
    return jsonResponse("error",
      "Unknown action: \"" + params.action + "\". " +
      "Valid: update_inventory, log_floor_note, log_error, log_delivery.");
  }

  try {
    // Deduplicate scan ID to prevent duplicate log rows
    const scanId = params.id || (params.records && params.records.length > 0 ? params.records[0].id : null);
    if (scanId) {
      const cache = CacheService.getScriptCache();
      if (cache.get("scanned_" + scanId)) {
        return ContentService.createTextOutput(JSON.stringify({
          status: "success",
          message: "Duplicate scan " + scanId + " safely skipped"
        })).setMimeType(ContentService.MimeType.JSON);
      }
      cache.put("scanned_" + scanId, "1", 21600); // 6-hour deduplication
    }

    let logSheet = ss.getSheetByName("Delivery Scan Log");
    if (!logSheet) {
      logSheet = ss.insertSheet("Delivery Scan Log");
      logSheet.appendRow(["Timestamp", "UPC", "Product Name", "Delivery Date",
        "Use By Date", "Storage Area", "Received By", "Status"]);
    }

    const rawUpc = cap(params.upc || params.barcode || params.UPC || params.itemNumber || params.item_number, 50);

    // High-speed CacheService barcode lookup (~5ms hit)
    const resolvedLookup = resolveBarcodeFromSheet(rawUpc);

    const name = cap(params.name || params.productName || params["Product Name"] || params.product || resolvedLookup.name, 100);
    const delivDate = cap(params.deliveryDate || params.deliveredDate || params["Delivered Date"], 30);
    const useBy = cap(params.useByDate || params.expirationDate || params.useBy || params["Use-By Date"], 30);
    const storage = cap(params.storageArea || params["Storage Area"] || resolvedLookup.storageArea || "Dry Storage", 50);
    const staff = cap(params.receivedBy || params["Received By"] || "Terry", 50);
    const status = cap(params.status || params.Status || "Received", 30);

    logSheet.appendRow([new Date(), rawUpc, name, delivDate, useBy, storage, staff, status]);

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      message: "Delivery scan logged successfully",
      logged: name || rawUpc,
      resolvedFromCache: resolvedLookup.resolvedFromCache,
      name: name,
      storageArea: storage
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) { return jsonResponse("error", err.toString()); }
}

// ─── Gmail Auto-Sync ──────────────────────────────────────────────────────────

function syncGmailFloorNotesAndErrors() {
  const ss = getSpreadsheet();
  let notesSheet = ss.getSheetByName("Floor Notes");
  if (!notesSheet) {
    notesSheet = ss.insertSheet("Floor Notes");
    notesSheet.appendRow(["Timestamp", "Note Type", "Staff / Sender", "Device / Source", "Note Content"]);
  }

  const query = 'is:unread (subject:"[DietaryOps]" OR subject:"[Floor Note]" OR subject:"[Error]" OR subject:"[DOPS]")';
  const threads = GmailApp.search(query, 0, 10);
  let syncedCount = 0;

  threads.forEach(function(thread) {
    thread.getMessages().forEach(function(msg) {
      if (!msg.isUnread()) return;
      const subject = msg.getSubject();
      const noteType = subject.toLowerCase().indexOf("error") !== -1 ? "⚠️ Email Error" : "📧 Email Note";
      notesSheet.appendRow([
        msg.getDate(), noteType, msg.getFrom(),
        "Gmail Auto-Sync (" + subject + ")",
        msg.getPlainBody().substring(0, 1000)
      ]);
      msg.markRead();
      syncedCount++;
    });
  });

  Logger.log("Synced " + syncedCount + " email note(s)/error(s) to Floor Notes sheet.");
}

// ─── Test Functions ───────────────────────────────────────────────────────────

function testDoPost() {
  const mockEvent = { postData: { contents: JSON.stringify({
    upc: "074865027137", name: "Green Beans",
    deliveryDate: "2026-10-25", useByDate: "2029-09-25",
    storageArea: "Dry Storage", receivedBy: "Terry", status: "Received"
  })}};
  Logger.log(doPost(mockEvent).getContent());
}

function testBarcodeLookup() {
  const result = resolveBarcodeFromSheet("074865027137");
  Logger.log("Barcode Lookup Result: " + JSON.stringify(result));
}

function testBarcodeOnlyScan() {
  const result = resolveBarcodeFromSheet("074865027137");
  Logger.log("Test Barcode Only Scan: " + JSON.stringify(result));
}
