# Antigravity Operational Rules: DietaryOps Manager (Century Villa)

## User Persona & Communication Style

- **User**: Terry Little Jr. (Dietary Receiver & Systems Lead) / Andy Tygart (Dietary Director) at
  Century Villa Healthcare.
- **Communication Style**: Direct, concise, action-oriented, zero fluff.
- **Shorthand & Domain Vocabulary**:
    - `SUPC` / `Item #`: 6 or 7-digit Sysco item number (e.g. 4455648).
    - `TCS`: Time/Temperature Control for Safety food items.
    - `KDS`: Kitchen Display System (Floor Cook Line Board).
    - `FIFO`: First-In, First-Out ServSafe rotation rule.
    - `ZPL`: Zebra Programming Language for QLn420 203 dpi thermal printers.
    - `CVILLA`: Century Villa Healthcare facility profile code.
    - `DOPS`: DietaryOps Manager app ecosystem.

## App Vision & Core Architecture

- **Primary Goal**: All-in-one dietary command center for Century Villa Healthcare connecting
  Android devices to Google Sheet `CV_Dietary_Inventory_2026`
  (`16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY`).
- **3 Core Canvases**:
    1. **Dock Intake Scanner**: CameraX scanner + 7-digit keypad, 1-to-many barcode aliasing (Col C
       UPC / Col B Item# -> Col K Linked Inventory Item -> Col B On Hand in Inventory Raw), "Learn
       on Scan" bottom sheet.
    2. **Kitchen Display (Floor KDS)**: Dynamic time-based meal service (Breakfast 07:00, Lunch 11:
       15, Dinner 16:45), portion scoop indicators (#8 disher, 4 oz), texture mods (Mechanical
       Soft/Pureed slurry), 1-tap HACCP temp logger (>=140°F hot, <=41°F cold), interactive thaw
       pull checklist with auto-Zebra thaw label printing.
    3. **Live Management Dashboard**: KPI status badges (🔴 Zero Stock, 🟠 Below Par, 🟢 In Stock), Top
       50 Vendor Reorder Builder (*Sysco*, *Piazza Produce*, *Direct Supply*).

## Strict Technical Rules

1. **Zero-Latency Scanning**: Local Room DB queries FIRST (<10ms), fire-and-forget background
   network sync SECOND. Never block camera viewfinder on network lookups.
2. **Static Count Walk Ordering**: Always query catalog items ordered by
   `shelfOrderIndex ASC, name ASC, syscoUpc ASC` so changing counts never rearranges list order.
3. **Zebra Hardware Formatting**: Always include `^MNY` (Gap/Notch Media Tracking) and `^MTD`
   (Direct Thermal) in all ZPL label outputs.
4. **Deduplication**: Enforce `Mutex` lock in repository and `CacheService` scan ID deduplication in
   Google Apps Script.
5. **No Unrecognized Item Pollution**: Never save unrecognized items, badge payloads (`CV-`,
   `DOPS-AUTH`), or PIN numbers to the master catalog.
