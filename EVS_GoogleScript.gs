/**
 * Century Villa Health Care — Dietary Operations Backend
 * Google Apps Script Web App for Barcode Receiving Scanner & Live Inventory Sync
 * Repository: terryleejr220/dietaryops
 */

const SPREADSHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY";

function getSpreadsheet() {
  try {
    return SpreadsheetApp.getActiveSpreadsheet() || SpreadsheetApp.openById(SPREADSHEET_ID);
  } catch (e) {
    return SpreadsheetApp.openById(SPREADSHEET_ID);
  }
}

/**
 * GET Request: Returns all inventory items with their current On-Hand counts.
 * Endpoint: Web App URL (used by the scanner app to populate inventory catalog)
 */
function doGet(e) {
  const ss = getSpreadsheet();
  const sheet = ss.getSheetByName("Sheet1") || ss.getSheets()[0];
  const data = sheet.getDataRange().getValues();

  let currentCategory = "General";
  const inventoryItems = [];

  // Skip header row (row 0)
  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    const colA = String(row[0] || "").trim();
    const colB = String(row || "").trim();
    const colC = String(row || "").trim();

    if (!colA) continue;

    // Detect Category header rows (where Column A has text but Column B & C are empty)
    if (colB === "" && colC === "") {
      currentCategory = colA;
      continue;
    }

    // Standard inventory item
    inventoryItems.push({
      category: currentCategory,
      name: colA,
      onHand: colB,             // Column B is the latest On-Hand count
      previousCount: colC,      // Column C is the previous count
      location: String(row || currentCategory)
    });
  }

  return ContentService.createTextOutput(JSON.stringify(inventoryItems))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * POST Request: Handles both barcode delivery logs AND real-time inventory updates.
 */
function doPost(e) {
  // Safety check for empty requests or manual editor execution
  if (!e || !e.postData || !e.postData.contents) {
    return ContentService.createTextOutput(
      JSON.stringify({ 
        status: "error", 
        message: "No postData received. doPost must be called via HTTP POST." 
      })
    ).setMimeType(ContentService.MimeType.JSON);
  }

  const ss = getSpreadsheet();
  let params = {};

  try {
    params = JSON.parse(e.postData.contents);
  } catch (err) {
    return ContentService.createTextOutput(
      JSON.stringify({ 
        status: "error", 
        message: "Invalid JSON: " + err.toString() 
      })
    ).setMimeType(ContentService.MimeType.JSON);
  }

  // --- ACTION 1: Update Inventory Counts from App ---
  if (params.action === "update_inventory" && Array.isArray(params.items)) {
    const sheet = ss.getSheetByName("Sheet1") || ss.getSheets()[0];
    const data = sheet.getDataRange().getValues();

    // Map item names to their row index in the sheet
    const rowMap = {};
    for (let r = 1; r < data.length; r++) {
      const name = String(data[r][0] || "").trim().toLowerCase();
      if (name) rowMap[name] = r + 1; // 1-based row index for Google Sheets
    }

    let updatedCount = 0;
    params.items.forEach(function(item) {
      const lookupName = String(item.name || "").trim().toLowerCase();
      const targetRow = rowMap[lookupName];
      if (targetRow) {
        sheet.getRange(targetRow, 2).setValue(item.onHand);
        updatedCount++;
      }
    });

    return ContentService.createTextOutput(JSON.stringify({ 
      status: "success", 
      message: `Updated ${updatedCount} items in Google Sheets` 
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // --- ACTION 2: Log Delivery Scan / Label Print ---
  try {
    let logSheet = ss.getSheetByName("Delivery Scan Log");
    if (!logSheet) {
      logSheet = ss.insertSheet("Delivery Scan Log");
      logSheet.appendRow(["Timestamp", "UPC", "Product Name", "Delivery Date", "Use By Date", "Storage Area", "Received By", "Status"]);
    }

    // Flexible key mapping: handles all naming variations from different app builds
    const upc = params.upc || params.barcode || params.UPC || params.itemNumber || params.item_number || "";
    const name = params.name || params.productName || params["Product Name"] || params.product || "";
    const delivDate = params.deliveryDate || params.deliveredDate || params["Delivered Date"] || "";
    const useBy = params.useByDate || params.expirationDate || params.useBy || params["Use-By Date"] || "";
    const storage = params.storageArea || params["Storage Area"] || "Dry Storage";
    const staff = params.receivedBy || params["Received By"] || "Terry";
    const status = params.status || params.Status || "Received";

    logSheet.appendRow([
      new Date(),
      upc,
      name,
      delivDate,
      useBy,
      storage,
      staff,
      status
    ]);

    return ContentService.createTextOutput(JSON.stringify({ 
      status: "success", 
      message: "Delivery scan logged successfully",
      logged: name || upc
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ 
      status: "error", 
      message: err.toString() 
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * Safe Test Function: Use this in Apps Script Editor to test without a mobile device
 */
function testDoPost() {
  const mockEvent = {
    postData: {
      contents: JSON.stringify({
        upc: "074865027137",
        name: "Green Beans",
        deliveryDate: "09/25/26",
        useByDate: "09/25/27",
        storageArea: "Dry Storage",
        receivedBy: "Terry",
        status: "Received"
      })
    }
  };

  const response = doPost(mockEvent);
  Logger.log(response.getContent());
}
