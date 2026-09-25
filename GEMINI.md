# Project & System Operational Rules

## Diagnosing & Disabling Broken Plugin Hooks / Telemetry Plugins
- **Symptom**: Agent actions, Gradle runs, or CLI tool executions crash, hang, or emit recurring telemetry hook errors (such as `googlecloudtools.datacloud_telemetry`).
- **Remediation**:
  1. Inspect the installed plugins directory at `C:\Users\terry\.gemini\config\plugins\` or `~/.gemini/config/plugins/`.
  2. Locate the offending plugin folder (e.g. `googlecloudtools.datacloud_telemetry`).
  3. Safely disable the broken hook by renaming the directory (e.g., append `.disabled` or `.bak`) or removing it from `plugins.json` / `config.json`.
  4. Ensure `hooks.json` in global or workspace configs does not point to dangling hooks or missing executables.
  5. Restart the agent session or rebuild to confirm smooth operation.

## Multi-Tenant Cloud & Department Isolation Architecture
- **Base Branding & Engine**: DietaryOps Enterprise (`DOPS`).
- **Default Work Profile**: Century Villa Healthcare (`CVILLA`) uses dedicated Google Sheet ID (`16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY`).
- **Department Routing**:
  - Dietary: Logs to `"Delivery Scan Log"`, updates count sheet in `"Inventory Raw"`.
  - Environmental Services (EVS): Logs to `"EVS Scan Log"`, updates count sheet in `"EVS Inventory"`.
- **Identity Distinction**:
  - `AD99` ("System Administrator"): Admin login reserved for backend settings, cloud integrations, and tenant deployment.
  - `TL01` ("Terry Little Jr."): Operational floor supervisor for day-to-day scanning.
