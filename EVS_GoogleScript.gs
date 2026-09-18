// ==========================================
// ENVIRONMENTAL SERVICES INVENTORY TRACKER
// ==========================================
// Instructions:
// 1. In Google Sheets, go to Extensions -> Apps Script
// 2. Paste this code, overwriting everything
// 3. Click Deploy -> New Deployment
// 4. Select "Web app", execute as "Me", who has access "Anyone"
// 5. Copy the Web App URL into your DietaryOps Manager Dashboard

const SPREADSHEET_ID = "1zGT0W6QP1acqCe5Jt-8l7spXtZWxt99LI2X3ekj2kxw"; // Fallback ID for CVES

function doPost(e) {
  try {
    const payload = JSON.parse(e.postData.contents);
    const targetSheetId = payload.spreadsheetId || SPREADSHEET_ID;
    const tabName = payload.sheetTab || "EVS Log";
    const scanData = payload.scan;

    const ss = SpreadsheetApp.openById(targetSheetId);
    let sheet = ss.getSheetByName(tabName);
    
    // Create tab if it doesn't exist
    if (!sheet) {
      sheet = ss.insertSheet(tabName);
      sheet.appendRow([
        "Timestamp", "Employee ID", "Item Name", "UPC", 
        "Category", "Quantity Received", "Unit", "Notes"
      ]);
      sheet.getRange("A1:H1").setFontWeight("bold").setBackground("#d9ead3");
      sheet.setFrozenRows(1);
    }

    // Format Data
    const rowData = [
      new Date(scanData.scanTimestamp),
      scanData.receivedBy || "Unknown",
      scanData.itemName,
      scanData.syscoUpc,
      scanData.category,
      scanData.onHandAmount,
      scanData.unit,
      "" // Notes column
    ];

    // Append to sheet
    sheet.appendRow(rowData);

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      message: `Appended ${scanData.itemName} to ${tabName}`
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: error.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}
