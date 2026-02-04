# Changelog

## [v0.2.0] - 2026-02-04
### Milestone: Full Standalone Capability
- **Standalone Victory:** The application is now 100% independent. All hardcoded paths to `/data/data/com.termux` have been eliminated.
- **Binary Patching Engine:** Added automatic binary-level string replacement (`com.termux` -> `com.alevap`) in `bundle_x11.sh` to fix hardcoded paths in pre-compiled binaries like `htop` and `bash`.
- **W^X Bypass (SDK 28):** Lowered `targetSdkVersion` to 28 to allow execution of binaries and scripts directly from the app's internal data directory, bypassing modern Android execution restrictions.
- **Floating Debug Tool:** Added a programmatically created "Copy Log" button at the top-right of the UI for instant clipboard access to debug logs.
- **Robust Wrapper System:** Re-engineered wrapper generation for `aterm`, `bash`, `jwm`, etc., with proper `DISPLAY=:1`, `LD_LIBRARY_PATH`, and `PATH` exports.
- **FS Sanitization:** Implemented aggressive `usr/bin` cleanup on startup to resolve "Permission Denied" errors caused by stale symlinks.
- **Self-Test 2.0:** Extended diagnostic tests to verify shell functionality and wrapper integrity.

## [v0.1.2] - 2026-02-03
### Added
- **JWM Integration:** Joe's Window Manager bundled with full configuration and dependencies.
- **XTerm (ATerm) Support:** Added `aterm` and `xterm` wrapper for terminal access within JWM.
- **Improved Internal Startup:** "Startup" button now launches the internal X server and enters the JWM environment directly.
- **Automated Diagnostic:** Self-test runs automatically at startup to verify all GUI components and libraries.

## [v0.1.1] - 2026-02-03
### Added
- **Internal X Server Launch:** Implemented self-contained X server startup using `app_process` and `CmdEntryPoint`.
- **Auto-Extraction:** Assets are automatically extracted to `files/usr` on first run.

## [v0.1.0] - 2026-01-31
### Initial Stable Release
- Core `liblorie.so` X11 server functionality.
- Basic build system and backup infrastructure.
- `com.alevap` package rename.