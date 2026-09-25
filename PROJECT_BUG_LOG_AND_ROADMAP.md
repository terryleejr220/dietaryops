# DietaryOps Manager & EVS Enterprise — Bug Log, Feature Status & Error Reporting Reference

**Project Name:** DietaryOps Manager (`com.dietaryops.manager`)  
**Current Version:** v24.0 (Build 24)  
**Lead:** Terry Little Jr. (Dietary Operations / Certified ServSafe Proctor)  
**Facilities:** Century Villa Healthcare (`CVILLA`), Environmental Services (`EVS`)

---

## 1. Automated Error Reporting System (How Error Reports Work)

The app now features an automated multi-tier **Error Logging & Diagnostics Pipeline** (`ErrorLogger.kt`):

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
1. **Local Log Cache**: Errors are saved locally with timestamps, device model (e.g. `Samsung Galaxy Tab A8`), error tags, and staff signatures (`Terry Little Jr. (TL01)`).
2. **In-App Error Viewer**: Located under **Settings > App Error & Diagnostic Logs**. Tap **"View Logs"** to inspect or **"Copy Logs"** to share.
3. **Cloud Firestore Sync**: Pushes diagnostic reports asynchronously to `/companies/{companyCode}/error_logs` in Firebase Firestore.
4. **Google Sheets Webhook Sync**: Posts JSON error payloads (`"action": "log_error"`) directly to your active Google Apps Script WebApp endpoint.

---

## 2. Comprehensive Bug Log & Technical Fixes (BUG-01 to BUG-11)

| Bug ID | Component | Priority | Status | Description & Root Cause | Exact Technical Fix Applied |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **BUG-01** | Sync | Critical | **Resolved** | `Unable to create @Body converter for java.util.List<i54>` | Annotated all DTOs (`SheetScanRecordDto`, `SheetSyncPayload`, `CatalogSyncDto`) with `@Keep` and updated ProGuard rules (`-keepclassmembers class * { @retrofit2.http.* <methods>; }`) to prevent R8 interface obfuscation. |
| **BUG-02** | Security | High | **Resolved** | Staff ID lookup error `LS01 not found for facility EV` | Bypassed strict badge lock on floor receiving tools; floor tools open directly to staff; admin/webhook URLs remain locked behind Admin PIN/Badge. |
| **BUG-03** | Scanner | Medium | **Resolved** | Camera scanner sluggishness and freezing | Removed the 20s/8s camera battery-saver auto-pause timer in `ReceivingScreen.kt`. Camera scanner is now 100% live and instant. |
| **BUG-04** | UI | Medium | **Resolved** | Samsung One UI displaying solid black square app icon | Added adaptive icon assets (`mipmap-anydpi-v26/ic_launcher.xml`) with `ic_launcher_background` vector fill. |
| **BUG-05** | Scanner | Medium | **Resolved** | 14-digit outer case barcodes unrecognized | Implemented GTIN-14 / ITF-14 barcode normalization in `ProductManager.normalizeBarcode()` to strip packaging indicators. |
| **BUG-06** | UI | Low | **Resolved** | Quick-Add Dialog not closing after save | Updated `QuickAddDialog` in `LabelScannerScreen.kt` to trigger `showQuickAddDialog = false` upon saving. |
| **BUG-07** | Sync | Low | **Resolved** | Missing Item # column in Sheets sync | Mapped `syscoItemNumber` across master catalog DTOs and `SheetScanRecordDto`. |
| **BUG-08** | Logic | Low | **Resolved** | `ZPLDAILY` Apps Script failure email alerts | Deleted orphaned clock triggers in Google Apps Script `EVS_GoogleScript.gs`. |
| **BUG-09** | Logic | Low | **Resolved** | Privacy policy package ID mismatch | Updated documentation and privacy disclosures to `com.dietaryops.manager`. |
| **BUG-10** | UI | Low | **Resolved** | Congested menu rotation tags | Batch-standardized menu cycle tags to `'Every Week'` / `'5-Week Cycle'`. |
| **BUG-11** | Auth / UI | Medium | **Resolved** | Login screen asked to 'Scan Badge' but badge printing was missing | Built `ZplGenerator.generateStaffBadgeZpl()` to print 2.25" x 1.25" Zebra thermal badge stickers (`TL01`, `AT01`, `LS01`). |

---

## 3. Feature Audit & Status Matrix (F-01 to F-17)

| Feature ID | Category | Feature Name | Status | Usability Verdict & Action Plan |
| :--- | :--- | :--- | :--- | :--- |
| **F-01** | Scanner | CameraX ML Kit Barcode Capture | **Active** | **Keep & Simplify**: Restored raw speed and eliminated camera auto-pause freeze. |
| **F-02** | Printer | Zebra QLn420 Bluetooth SPP Printing | **Active** | **Keep**: Reliable Bluetooth SPP thermal printing on `Dispatchers.IO` coroutines. |
| **F-03** | Dietary | ServSafe / IDOH TCS Auto-Dating | **Active** | **Keep**: Automated `Delivered` + shelf life, and `Opened` TCS limit (+7d max). |
| **F-04** | Sync | Google Sheets Cloud Sync | **Active** | **Keep**: Fixed Retrofit Gson converter & ProGuard R8 obfuscation for live webhooks. |
| **F-05** | Printer | Flipped Shelf Label ZPL Layout | **Active** | **Keep**: Large 32pt bold Product Name, storage location, USE BY date, 1D barcode. |
| **F-06** | Dietary | Pack Size Breakdown (`EA = Case`) | **Active** | **Keep**: Displays case breakdown (e.g. `6 / #10 CS (6 Cans/Case) • EA = Case`). |
| **F-07** | Scanner | Manual UPC & Item # Search Bar | **Active** | **Keep**: Manual search field above camera with live autocomplete for smudged codes. |
| **F-08** | UI | Inline Quick-Add Product Dialog | **Active** | **Keep**: One-tap item creation with auto-dismiss on save. |
| **F-09** | UI | Role-Aware Settings Menu Layout | **Active** | **Keep**: Floor tools open to staff; Google Sheets URLs and system settings locked behind Admin PIN. |
| **F-10** | Security | Enterprise Staff Badge / PIN Pad | **Active** | **Simplified**: Bypassed strict badge lock on floor tools; use staff profile avatar pill. |
| **F-11** | Logic | Multi-Facility Routing (`CVILLA` vs `EVS`)| **Active** | **Keep**: Seamless routing between Dietary (`CVILLA`) and Housekeeping (`EVS`). |
| **F-12** | Scanner | Camera Battery Saver Auto-Pause | **Pruned** | **Removed**: Removed 20s/8s pause screen to keep camera live and fast. |
| **F-13** | EVS | Housekeeping Portal (`CVES36`) | **Active** | **Keep**: Added dedicated `EVS_GoogleScript.gs` Webhook and EVS shelf label templates. |
| **F-14** | Dietary | Sysco Master Catalog Consolidation | **Active** | **Keep**: Unified 150+ verified Sysco catalog items with UPC & item numbers. |
| **F-15** | Dietary | 5-Week Cycle Menu Tag Standardization| **Active** | **Keep**: Standardized menu rotation tags for kitchen workflow. |
| **F-16** | Dietary | Week 3 Menu Cycle Advance | **Active** | **Keep**: Automated cycle progression for menu items. |
| **F-17** | Auth | Staff Badge Generation & Zebra Printing | **Active** | **Implemented**: 1-tap Zebra thermal access badge sticker printing for `TL01`, `AT01`, `LS01`. |

---

## 4. Horizon Projections & Strategic Roadmap

```
┌─────────────────────────────────────────────────────────────────────────┐
│ HORIZON 1: Immediate Stabilization & De-Bloating (COMPLETE)            │
│ • Fix 74 scans sync (Retrofit Gson fix)                                │
│ • Remove camera auto-pause freeze                                       │
│ • Streamline Settings screen for staff                                 │
│ • Implement 1-Tap Zebra Thermal Badge Sticker Printing                 │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ HORIZON 2: Production Floor Hardening (Weeks 1-2)                       │
│ • Monday inventory walks optimization                                  │
│ • Dock receiving scans for Sysco / Piazza                              │
│ • Real-time offline database sync with WorkManager                     │
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
