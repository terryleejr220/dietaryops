# DietaryOps Manager — Agent Rules

## Diagnosing Broken Antigravity Plugin Hooks

When every tool call fails with `MODULE_NOT_FOUND` or similar hook errors:

1. **Identify the culprit hook** — The error message names the hook (e.g., `jsonhook__<plugin_name>_PreToolUse_0_0`).
2. **Do NOT just rename the plugin folder** — Renaming (e.g., appending `.DISABLED`) breaks the module path but the hook stays registered. The system still fires the hook, it just crashes, blocking ALL tool calls.
3. **Proper fix** — Either:
   - Delete the plugin folder entirely (`Remove-Item -Recurse -Force ~/.gemini/config/plugins/<plugin_name>`), OR
   - Edit the plugin's `manifest.json` / `hooks.json` to remove or disable the broken hook entry (`"enabled": false`), OR
   - If the plugin has a `plugin.json`, check for hook declarations there.
4. **Restart Antigravity** — Hooks are loaded at startup. Changes to hook registrations require a session restart.
5. **Verify** — After restart, run a simple tool call (e.g., `echo test`) to confirm hooks no longer block execution.

Key insight: Plugin hooks fire on EVERY tool invocation. A broken hook is a total blocker, not a partial failure.
