# DietaryHelper (Century Villa Delivery Scanner) — Architecture & Sync Blueprint

**Current Version:** v20.0 (Build 20)

This document defines the production requirements, data contracts, and refactoring guidelines for Century Villa Dietary Operations. 
Any AI assistant (Gemini / Claude / Cursor) working on this repository must strictly adhere to these rules.

---

## 1. Project Identity & Operational Environment
* **Facility:** Century Villa Healthcare (Tipton, IN)
* **Lead:** Terry Little Jr. (Dietary Operations / Certified ServSafe Proctor)
* **Director of Foodservice:** Andy Tygart
* **Hardware Environment:** 
  * Mobile Device / Tablet running Android (Min SDK 26, Target SDK 34)
  * Mobile Zebra Printer: Zebra QLn420 (Bluetooth SPP, 203 DPI)
  * Label Dimensions: 2.25 in x 1.25 in (456 dots wide x 254 dots tall)

---

## 2. Target Architecture (Modular MVVM)
The current monolithic `MainActivity.kt` (~1,000 lines) and `ProductManager.kt` are being refactored into the following clean package structure:

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
│   ├── AdaptiveMainLayout.kt        // Responsive multi-pane & phone layout
│   ├── screens/
│   │   ├── ReceivingScreen.kt       // Live camera scanner, manual search, Zebra printing
│   │   ├── InventoryLogsScreen.kt   // Live inventory logs & Google Sheets sync
│   │   ├── SettingsScreen.kt        // Role-aware settings & printer manager
│   │   └── AdminDrawerContent.kt    // Facility switcher & staff roster drawer
│   ├── components/
│   │   ├── BadgeLoginDialog.kt      // Lanyard badge scanner & 1-tap operator switcher
│   │   └── ScannerOverlay.kt        // High-tech reticle & scanline HUD
│   └── theme/                       // Material 3 Color, Type, and Theme
└── MainActivity.kt                  // Lightweight host activity
```

---

## 3. Core Business Logic & Non-Negotiable Rules

### A. Zebra ZPL Label Specifications (2.25" x 1.25" @ 203 DPI)
* **Label Header:** `^XA^PW456^LL0254^PON^LH0,0`
* **Receiving/Delivery Label:**
  * Displays Product Name, Delivery Date, Calculated Use-By Date (`deliveryDate + shelfLifeDays`), Unit Count (e.g. `1 cs`), and Code 128 barcode (`UPC|MM/dd/yy`).
* **Opened Product Label:**
  * Must display `[OPENED]` tag, Open Date, and Calculated Opened Use-By Date:
    `min(deliveryDate + shelfLifeDays, openDate + openedShelfLifeDays)`.
* **Manager Badge:** Code 128 payload `CV-ADMIN-AUTH` toggles admin privileges when scanned by camera.
* **Threading Rule:** Bluetooth socket writes (`createRfcommSocketToServiceRecord`) MUST run on `Dispatchers.IO` with UI callbacks routed back to `Dispatchers.Main`. Never use raw `Thread { ... }.start()`.

### B. Google Sheets Webhook Integration
* **Production Spreadsheet:** `CV inventory 226` (`16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY`)
* **Webhook Action:** `POST` with `Content-Type: application/json`
* **Payload Structure:**
  ```json
  {
    "action": "update_inventory",
    "items": [
      {
        "name": "V8",
        "category": "Canned Goods",
        "onHand": "27"
      }
    ]
  }
  ```
* **Quantity Formatting Rule:** Decimals (`0.5`, `0.25`, `0.75`) are preferred over fractions to prevent Google Sheets from auto-parsing case quantities as calendar dates.

---

## 4. Current Refactoring Phase Roadmap

* [x] **Phase 1: Manifest Cleanup** — Remove unused `BILLING` and `AD_ID` permissions.
* [x] **Phase 2: Concurrency Upgrade** — Convert `ZplPrinter.print` to Kotlin Coroutines (`Dispatchers.IO`).
* [x] **Phase 3: Data Model Extraction** — Move `SyscoProduct` and `InventoryItem` into `data/model/`.
* [x] **Phase 4: UI Separation** — Move `ScannerScreen` and `InventoryCountScreen` out of `MainActivity.kt`.
* [x] **Phase 5: State Management** — Introduce `MainViewModel` to survive screen rotation.
