# DietaryOps Manager & EVS Enterprise — Bug Log, Feature Status & Error Reporting Reference

**Project Name:** DietaryOps Manager (`com.dietaryops.manager`)  
**Current Version:** v35.0 (Build 35)  
**Lead:** Terry Little Jr. (Dietary Operations / Certified ServSafe Proctor) & Andy Tygart (Dietary Director)  
**Facilities:** Century Villa Healthcare (`CVILLA`), Environmental Services (`EVS`)  
**Spreadsheet Target:** `CV_Dietary_Inventory_2026` (`16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY`)

---

## 1. Automated Error Reporting System (How Error Reports Work)

The app features an automated multi-tier **Error Logging & Diagnostics Pipeline** (`ErrorLogger.kt`):

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           APP RUNTIME ERROR                             │
│       (Sync Failure / Camera Exception / Bluetooth Print Error)         │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┐
           ▼                         ▼                         ▼
 ┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
 │ 1. LOCAL LOG CACHE│     │2. CLOUD FIRESTORE │     │3. GOOGLE SHEETS   │
 │   SharedPreferences│     │   /error_logs     │     │   Webhook Payload │
 │ (In-app Log Viewer│     │(Remote Real-time) │     │ (action: log_error│
 └───────────────────┘     └───────────────────┘     └───────────────────┘
```

### Key Components:

1. **Local Log Cache**: Errors are saved locally with timestamps, device model (e.g. `Samsung Galaxy S23+`), error tags, and staff signatures (`Terry Little Jr. (TL01)` / `Andy Tygart (AT01)`).
2. **In-App Error Viewer**: Located under **Settings > App Error & Diagnostic Logs**. Tap **"View Logs"** to inspect or **"Copy Logs"** to share.
3. **Cloud Firestore Sync**: Pushes diagnostic reports asynchronously to `/companies/{companyCode}/error_logs` in Firebase Firestore.
4. **Google Sheets Webhook Sync**: Posts JSON error payloads (`"action": "log_error"`) directly to your active Google Apps Script WebApp endpoint.

---

## 2. Comprehensive Bug Log & Technical Fixes (BUG-01 to BUG-18)

| Bug ID     | Component | Priority | Status       | Description & Root Cause                                          | Exact Technical Fix Applied                                                                                                                                                                                                 |
|:-----------|:----------|:---------|:-------------|:------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **BUG-01** | Sync      | Critical | **Resolved** | `Unable to create @Body converter for java.util.List<i54>`        | Annotated all DTOs (`SheetScanRecordDto`, `SheetSyncPayload`, `CatalogSyncDto`) with `@Keep` and updated ProGuard rules (`-keepclassmembers class * { @retrofit2.http.* <methods>; }`) to prevent R8 interface obfuscation. |
| **BUG-02** | Security  | High     | **Resolved** | Staff ID lookup error `LS01 not found for facility EV`            | Bypassed strict badge lock on floor receiving tools; floor tools open directly to staff; admin/webhook URLs remain locked behind Admin PIN/Badge.                                                                           |
| **BUG-03** | Scanner   | Medium   | **Resolved** | Camera scanner sluggishness and freezing                          | Removed the 20s/8s camera battery-saver auto-pause timer in `ReceivingScreen.kt`. Camera scanner is now 100% live and instant.                                                                                              |
| **BUG-04** | UI        | Medium   | **Resolved** | Samsung One UI displaying solid black square app icon             | Added adaptive icon assets (`mipmap-anydpi-v26/ic_launcher.xml`) with `ic_launcher_background` vector fill.                                                                                                                 |
| **BUG-05** | Scanner   | Medium   | **Resolved** | 14-digit outer case barcodes unrecognized                         | Implemented GTIN-14 / ITF-14 barcode normalization in `ProductManager.normalizeBarcode()` to strip packaging indicators.                                                                                                    |
| **BUG-06** | UI        | Low      | **Resolved** | Quick-Add Dialog not closing after save                           | Updated `QuickAddDialog` in `LabelScannerScreen.kt` to trigger `showQuickAddDialog = false` upon saving.                                                                                                                    |
| **BUG-07** | Sync      | Low      | **Resolved** | Missing Item # column in Sheets sync                              | Mapped `syscoItemNumber` across master catalog DTOs and `SheetScanRecordDto`.                                                                                                                                               |
| **BUG-08** | Logic     | Low      | **Resolved** | `ZPLDAILY` Apps Script failure email alerts                       | Deleted orphaned clock triggers in Google Apps Script `EVS_GoogleScript.gs`.                                                                                                                                                |
| **BUG-09** | Logic     | Low      | **Resolved** | Privacy policy package ID mismatch                                | Updated documentation and privacy disclosures to `com.dietaryops.manager`.                                                                                                                                                  |
| **BUG-10** | UI        | Low      | **Resolved** | Congested menu rotation tags                                      | Batch-standardized menu cycle tags to `'Every Week'` / `'5-Week Cycle'`.                                                                                                                                                    |
| **BUG-11** | Auth / UI | Medium   | **Resolved** | Login screen asked to 'Scan Badge' but badge printing was missing | Built `ZplGenerator.generateStaffBadgeZpl()` to print 2.25" x 1.25" Zebra thermal badge stickers (`TL01`, `AT01`, `LS01`).                                                                                                  |
| **BUG-12** | Database  | Critical | **Resolved** | Count sheet jumping / re-sorting list on On-Hand edits            | Locked Room DB query ordering to `@Query("SELECT * FROM catalog_items ORDER BY shelfOrderIndex ASC, name ASC, syscoUpc ASC")`.                                                                                              |
| **BUG-13** | Scanner   | High     | **Resolved** | 15-20 second dock freeze on "Processing..."                       | Removed blocking HTTP network calls from `processScan()`. Barcode & SUPC lookups query local Room DB in sub-10ms.                                                                                                          |
| **BUG-14** | Sync      | High     | **Resolved** | Duplicate entries in `Delivery Scan Log`                          | Added `Mutex` lock (`sheetsSyncMutex.tryLock()`) in `DeliveryRepository.kt` and 6-hour `CacheService` scan ID deduplication in Google Apps Script.                                                                         |
| **BUG-15** | Printer   | Medium   | **Resolved** | Label drifting and printing across perforations                   | Added `^MNY` (Gap/Notch Tracking) and `^MTD` (Direct Thermal) to all ZPL label headers in `ZplGenerator.kt`.                                                                                                                |
| **BUG-16** | Printer   | Medium   | **Resolved** | Barcode edge cutoff on 14-digit GTINs                             | Shifted Code 128 barcode placement to `^FO30,185` with module width `^BY1,2,30` in `ZplGenerator.kt`.                                                                                                                     |
| **BUG-17** | Data      | Low      | **Resolved** | Category header strings saved as catalog items                    | Added `deleteCategoryHeaderItems()` query to purge category header strings (`"Canned Goods"`, `"Dry Storage"`) from database.                                                                                              |
| **BUG-18** | Sync      | High     | **Resolved** | On-Hand quantity overwriting instead of incrementing              | Updated `addScanRecord()` in `DeliveryRepository.kt` and `EVS_GoogleScript.gs` to **increment** stock (`currentStock + addedQty`).                                                                                          |

---

## 3. Feature Audit & Status Matrix (F-01 to F-23)

| Feature ID | Category | Feature Name                               | Status     | Usability Verdict & Action Plan                                                                      |
|:-----------|:---------|:-------------------------------------------|:-----------|:-----------------------------------------------------------------------------------------------------|
| **F-01**   | Scanner  | CameraX ML Kit Barcode Capture             | **Active** | **Keep & Simplify**: Restored raw speed and eliminated camera auto-pause freeze.                     |
| **F-02**   | Printer  | Zebra QLn420 Bluetooth SPP Printing        | **Active** | **Keep**: Reliable Bluetooth SPP thermal printing on `Dispatchers.IO` coroutines.                    |
| **F-03**   | Dietary  | ServSafe / IDOH TCS Auto-Dating            | **Active** | **Keep**: Automated `Delivered` + shelf life, and `Opened` TCS limit (+7d max).                      |
| **F-04**   | Sync     | Google Sheets Cloud Sync                   | **Active** | **Keep**: Fixed Retrofit Gson converter & ProGuard R8 obfuscation for live webhooks.                 |
| **F-05**   | Printer  | Flipped Shelf Label ZPL Layout             | **Active** | **Keep**: Large 32pt bold Product Name, storage location, USE BY date, 1D barcode.                   |
| **F-06**   | Dietary  | Pack Size Breakdown (`EA = Case`)          | **Active** | **Keep**: Displays case breakdown (e.g. `6 / #10 CS (6 Cans/Case) • EA = Case`).                     |
| **F-07**   | Scanner  | Manual UPC & Item # Search Bar             | **Active** | **Keep**: Manual search field above camera with live autocomplete for smudged codes.                 |
| **F-08**   | UI       | Inline Quick-Add Product Dialog            | **Active** | **Keep**: One-tap item creation with auto-dismiss on save.                                           |
| **F-09**   | UI       | Role-Aware Settings Menu Layout            | **Active** | **Keep**: Floor tools open to staff; Google Sheets URLs and system settings locked behind Admin PIN. |
| **F-10**   | Security | Enterprise Staff Badge / PIN Pad           | **Active** | **Simplified**: Bypassed strict badge lock on floor tools; use staff profile avatar pill.            |
| **F-11**   | Logic    | Multi-Facility Routing (`CVILLA` vs `EVS`) | **Active** | **Keep**: Seamless routing between Dietary (`CVILLA`) and Housekeeping (`EVS`).                      |
| **F-12**   | Scanner  | Camera Battery Saver Auto-Pause            | **Pruned** | **Removed**: Removed 20s/8s pause screen to keep camera live and fast.                               |
| **F-13**   | EVS      | Housekeeping Portal (`CVES36`)             | **Active** | **Keep**: Added dedicated `EVS_GoogleScript.gs` Webhook and EVS shelf label templates.               |
| **F-14**   | Dietary  | Sysco Master Catalog Consolidation         | **Active** | **Keep**: Unified 150+ verified Sysco catalog items with UPC & item numbers.                         |
| **F-15**   | Dietary  | 5-Week Cycle Menu Tag Standardization      | **Active** | **Keep**: Standardized menu rotation tags for kitchen workflow.                                      |
| **F-16**   | Dietary  | Week 3 Menu Cycle Advance                  | **Active** | **Keep**: Automated cycle progression for menu items.                                                |
| **F-17**   | Auth     | Staff Badge Generation & Zebra Printing    | **Active** | **Implemented**: 1-tap Zebra thermal access badge sticker printing for `TL01`, `AT01`, `LS01`.       |
| **F-18**   | Kitchen  | Canvas 1: Floor Cook KDS Line Board        | **Active** | **Implemented**: Active Cycle Menu (`Week 5`), dynamic meal service (Breakfast, Lunch, Dinner), portion scoop guides (`#8 disher`, `4 oz`), texture mods (Mechanical Soft/Pureed slurry), 1-tap HACCP temp logger, and thaw pull checklist. |
| **F-19**   | Manager  | Canvas 2: Live Dashboard & Reorder Hub     | **Active** | **Implemented**: KPI status badges (🔴 Zero Stock, 🟠 Below Par, 🟢 In Stock), Top 50 Vendor Reorder Builder (*Sysco*, *Piazza Produce*, *Direct Supply*). |
| **F-20**   | Scanner  | 1-to-Many Barcode Aliasing & "Learn on Scan" | **Active** | **Implemented**: Resolves brand scans to generic master kitchen items (`linkedInventoryItem`), and opens "Learn on Scan" bottom sheet for unmapped boxes. |
| **F-21**   | UI / Web | 📊 Embedded In-App "Live Canvas"           | **Active** | **Implemented**: Renders live Google Sheets dashboards directly inside the app with 5-minute auto-refresh for wall-mounted kitchen displays. |
| **F-22**   | Printer  | 🥗 Top-Level "Prep Stickers" Studio        | **Active** | **Implemented**: Dedicated top-level navigation destination for printing Cooked Food Prep, Thaw Pull, Opened TCS, and "USE FIRST" rotation stickers. |
| **F-23**   | Dock     | 🚛 Truck OTW Guided Manifest Checklist    | **Active** | **Implemented**: Filters incoming truck shipments tagged Column AF (`OTW`) with 1-tap "Receive 1 Case & Print" dock execution. |

---

## 4. Horizon Projections & Strategic Roadmap

```
┌─────────────────────────────────────────────────────────────────────────┐
│ HORIZON 1: Immediate Stabilization & De-Bloating (COMPLETE)            │
│ • Fix 74 scans sync (Retrofit Gson fix)                                │
│ • Remove camera auto-pause freeze & sub-15ms ML Kit scanner             │
│ • Implement 1-Tap Zebra Thermal Badge Sticker Printing                 │
│ • Lock static Count Walk sheet list ordering                           │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ HORIZON 2: Canvas 1 (KDS) & Canvas 2 (Dashboard) Production (COMPLETE) │
│ • Floor Cook KDS Line Board & 1-tap HACCP Temp Logger                   │
│ • Live Management Dashboard & Top 50 Vendor Reorder Sheet               │
│ • 1-to-Many Barcode Aliasing & "Learn on Scan" Bottom Sheet             │
│ • Embedded In-App Live Canvas with 5-minute auto-refresh               │
│ • 🚛 Truck OTW Guided Manifest with 1-tap Receive & Print               │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ HORIZON 3: Housekeeping (CVES36) Rollout (Month 1)                       │
│ • Import EVS vendor order history CSV                                  │
│ • Test EVS storeroom scans & custom EVS shelf labels                   │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ HORIZON 4: Intelligent Growth & Future Vision (Quarter 1)               │
│ • Automated par replenishment draft orders                             │
│ • Bin shelf barcodes & automated zero-bloat inventory tracking         │
└─────────────────────────────────────────────────────────────────────────┘
```
