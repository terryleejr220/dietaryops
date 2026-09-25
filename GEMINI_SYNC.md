# DietaryOps Manager — Architecture, Integration & Sync Blueprint

**Current Version:** v24.0 (Build 24) | **Target SDK:** 36 (Android 15+ / API 36 Google Play Compliant)  
**Package Namespace:** `com.dietaryops.manager`

This document defines the production requirements, data contracts, and refactoring guidelines for DietaryOps Enterprise. 
Any AI assistant (Gemini / Claude / Cursor) working on this repository must strictly adhere to these rules.

---

## 1. Project Identity & Operational Environment
* **Base Engine & Branding:** DietaryOps Enterprise (`DOPS`)
* **Default Tenant Profile:** Century Villa Healthcare (Tipton, IN) (`CVILLA`)
* **Primary Lead:** Terry Little Jr. (Dietary Operations / Certified ServSafe Proctor / `TL01`)
* **Director of Foodservice:** Andy Tygart (`AT01`)
* **Housekeeping / EVS Lead:** Lorraine S. (`LS01`)
* **System Administrator:** `AD99` ("System Administrator")
* **Hardware Environment:** 
  * Mobile Device / Tablet running Android (Min SDK 26, Target SDK 36)
  * Mobile Zebra Printer: Zebra QLn420 (Bluetooth SPP, 203 DPI)
  * Label Dimensions: 2.25 in x 1.25 in (456 dots wide x 254 dots tall)

---

## 2. Target Architecture (Modular MVVM)

```
com.dietaryops.manager/
├── data/
│   ├── model/
│   │   ├── CatalogItem.kt           // Product metadata, shelf life days, pack size
│   │   ├── ScanRecord.kt            // Delivery scan records & staff initials
│   │   ├── StaffUser.kt             // Multi-facility staff profile & badge tokens
│   │   └── CompanyProfile.kt        // Facility routing & sheet tab maps
│   ├── local/
│   │   ├── AppDatabase.kt           // Room local offline database
│   │   └── ScanRecordDao.kt         // Offline scan records DAO
│   └── remote/
│       ├── GoogleSheetsApiService.kt// Retrofit Google Apps Script Webhook API
│       ├── CloudRepository.kt       // Unified Firestore & Sheets Cloud Repository
│       └── FirestoreRepository.kt   // Firebase Firestore cloud persistence
├── util/
│   ├── DateCalculator.kt            // ServSafe retention calculator
│   ├── SyscoUpcNormalizer.kt        // GTIN-14 / ITF-14 barcode normalizer
│   └── ErrorLogger.kt               // Automatic error logging pipeline
├── ui/
│   ├── AdaptiveMainLayout.kt        // Responsive multi-pane & phone layout with SystemBarStyle
│   ├── screens/
│   │   ├── ReceivingScreen.kt       // Live camera scanner, manual search, Zebra printing
│   │   ├── InventoryLogsScreen.kt   // Live logs, Mass Shelf Print, & Count Sheet Walk
│   │   ├── SettingsScreen.kt        // Role-aware settings, printer manager & error log viewer
│   │   └── AdminDrawerContent.kt    // Facility switcher & staff roster drawer
│   ├── components/
│   │   ├── BadgeLoginDialog.kt      // Lanyard badge scanner & 1-tap operator switcher
│   │   └── ScannerOverlay.kt        // High-tech reticle & scanline HUD
│   └── theme/                       // Material 3 Color, Type, and Theme
└── MainActivity.kt                  // Lightweight host activity with SystemBarStyle.auto
```

---

## 3. Core Business Logic & Non-Negotiable Rules

### A. Zebra ZPL Label Specifications (2.25" x 1.25" @ 203 DPI)
* **Label Canvas:** `^XA^PW456^LL254^MD15^LH0,0`
* **Receiving / Delivery Label:**
  * Displays Product Name, Delivery Date, Calculated Use-By Date (`deliveryDate + shelfLifeDays`), Unit Count, and personalized staff signature (`BY: TL01`).
* **Shelf Label (Top-Barcode Layout):**
  * Barcode centered at **TOP** (`^FO50,14^BY2,2.5,65^BCN,65,Y,N,N^FD...^FS`) for fast aisle scanning.
  * Product Name in middle (bold 30pt font, auto-wrapped).
  * Par Level & Storage Section side-by-side on bottom line (`Par: X cs • Sec: DRY STORAGE`).
* **Zebra Access Badge Sticker:**
  * Prints 2.25" x 1.25" thermal badge sticker with Code 128 barcode (`CV-TL01-ADMIN`, `CV-AT01-ADMIN`, `CV-LS01-EVS_MANAGER`) for 1-tap lanyard/clipboard scanning.
* **Threading Rule:** Bluetooth socket writes (`createRfcommSocketToServiceRecord`) MUST run on `Dispatchers.IO` with UI callbacks routed back to `Dispatchers.Main`.

### B. Google Sheets Webhook Integration & Cloud Isolation
* **Production Spreadsheet:** `CV inventory 226` (`16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY`)
* **Department Sheet Tab Isolation:**
  * **Dietary**: Logs to `"Delivery Scan Log"`, updates count sheet in `"Inventory Raw"`.
  * **Environmental Services (EVS)**: Logs to `"EVS Scan Log"`, updates count sheet in `"EVS Inventory"`.
* **Payload Structure (`SheetSyncPayload`)**:
  ```json
  {
    "spreadsheetId": "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY",
    "sheetTab": "Delivery Scan Log",
    "companyCode": "CVILLA",
    "department": "Dietary",
    "records": [
      {
        "id": "SCAN-101",
        "upc": "074861000001",
        "name": "Brown Sugar",
        "deliveryDate": "2026-09-25",
        "useByDate": "2027-09-25",
        "category": "Dry Storage",
        "receivedBy": "Terry Little Jr. (TL01)",
        "onHandQty": 2.0,
        "unit": "CS"
      }
    ]
  }
  ```

---

## 4. Current Refactoring Phase Roadmap

* [x] **Phase 1: Manifest Cleanup** — Remove unused `BILLING` and `AD_ID` permissions.
* [x] **Phase 2: Concurrency Upgrade** — Convert `ZplPrinter.print` to Kotlin Coroutines (`Dispatchers.IO`).
* [x] **Phase 3: Data Model Extraction** — Move `CatalogItem`, `ScanRecord`, and `StaffUser` into `data/model/`.
* [x] **Phase 4: UI Separation & Adaptive Layout** — Material 3 adaptive layout for phones and tablets.
* [x] **Phase 5: State Management** — StateFlow holders for scanner, inventory, and printer status.
* [x] **Phase 6: Count Sheet Walk & Mass Shelf Printing** — Zero handwriting floor count walk with live Google Sheets `Inventory Raw` sync & bulk Zebra label streaming.
* [x] **Phase 7: Android 15 & API 36 Compliance** — Target SDK 36, `SystemBarStyle.auto` edge-to-edge insets, and ProGuard R8 keep rules.
