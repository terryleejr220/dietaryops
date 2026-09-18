# DietaryOpsManager (Dietary Helper)

**DietaryOpsManager** is a production Android application built for dietary and foodservice operations at Century Villa Healthcare. It streamlines receiving inspections, automated food safety use-by date calculations, high-precision thermal label printing, and kitchen inventory management.

---

## Key Features

- **Receiving & Barcode Inspection**:
  - CameraX-powered barcode scanner using Google ML Kit Vision.
  - GS1 barcode parsing and Sysco UPC normalization.
  - Automatic shelf-life and use-by date calculation complying with food safety standards.
- **Zebra Mobile Printing (ZPL)**:
  - Direct Bluetooth SPP printing to Zebra mobile printers (such as Zebra QLn420 @ 203 DPI).
  - Pre-calibrated label dimensions: 2.25 in x 1.25 in (456 x 254 dots).
  - Code 128 barcodes formatted for receiving labels, opened item use-by labels, and manager authentication badges.
  - Asynchronous background I/O operations using Kotlin Coroutines.
- **Kitchen Inventory Management**:
  - Live on-hand counts organized by kitchen category tabs.
  - Decimal quantity entry (e.g., `0.5`, `0.25`, `0.75`) preventing spreadsheet date auto-formatting bugs.
  - Google Sheets webhook dispatcher for real-time inventory synchronization.
- **Admin & Safety Controls**:
  - PIN authentication and barcode badge scanning (`CV-ADMIN-AUTH`).
  - Remote configuration and Firestore audit logging.

---

## Tech Stack & Architecture

- **Platform**: Android (Min SDK 26, Target SDK 36)
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: Clean Architecture / MVVM with StateFlow
- **Local Persistence**: Room Database (`AppDatabase`, `ScanRecordDao`, `CatalogItemDao`)
- **Networking**: Retrofit 2, OkHttp 3, Google Sheets Webhooks
- **Cloud & Auth**: Firebase Auth, Firestore, Firebase Remote Config
- **Hardware Integration**: Bluetooth SPP (RFCOMM) & Zebra Programming Language (ZPL)

---

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer
- JDK 21 (Microsoft 21.0.12 or Temurin 21)
- Android SDK 36

### Setup & Build

1. Clone the repository:
   ```bash
   git clone https://github.com/terryleejr220/dietaryops.git
   ```
2. Open the project in Android Studio.
3. Configure `local.properties` with your Android SDK path and optional signing configuration:
   ```properties
   sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
   KEYSTORE_STORE_PASSWORD=<your_store_password>
   KEYSTORE_KEY_PASSWORD=<your_key_password>
   KEYSTORE_KEY_ALIAS=<your_key_alias>
   ```
4. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## License & Operational Guidelines

Proprietary — Developed for Century Villa Healthcare Dietary Operations.
Adheres to ServSafe food safety standards and guidelines.
