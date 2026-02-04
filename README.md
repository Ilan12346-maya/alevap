# Termux X11 Standalone Android App Framework

This project is an experimental framework for turning a Termux X11 environment into a standalone Android app with a native app feeling.

The goal is to lower the barrier for running Linux/X11 software on Android. Instead of requiring users to install Termux, configure emulators, and deal with complex setups, the result should feel like a normal Android app: install it, use it, uninstall it.

## Motivation

Using tools like Winlator or similar solutions still requires a lot of technical knowledge. Because of that, this space remains very niche.

I grew up with desktop icons, not layers of launchers and accounts. In 2026 everything seems to require Steam, launchers, or wrappers. This project is an attempt to bring back the simplicity of “click the app and it runs”, even for complex Linux software.

## What This Is

- A minimal Android app (under ~30 MB) bundling:
  - Termux-based Linux userland
  - X11 server
  - User shell
- Runs standalone, without requiring Termux to be installed on the device
- Includes example programs as proof of concept:
  - xterm
  - htop
  - xeyes
  - JWM (lightweight window manager)

Think of it as a reusable base for Android “app ports” powered by Termux X11.

## What You Can Build With It

You can extend the environment and repackage it as a custom Android app. Examples:
- Add OpenJDK and run JDownloader2 as a native-feeling Android app
- Package Linux desktop applications
- Experiment with Wine, DXVK, or emulation setups
- Build a focused single-purpose app instead of a general emulator UI

My personal target is **Need for Speed Underground** running on Android with a fully optimized setup and native UX. This project is not about Play Store distribution. Users are expected to provide their own files.

## Current Status

- Version: 0.0.3
- Standalone APK works on devices without Termux
- X server is functional
- Basic apps and window manager included
- Still early and experimental

## Planned Next Steps

- GPU acceleration
- Efficient Wine environment
- Evaluate FEX vs Box86/Box64
- Performance tuning and UX improvements

There is still a lot of work ahead.

## Build Requirements

Recommended (Termux-based workflow):
- Termux (Essential for the bundling process)
- Android SDK
- Termux NDK
- ADB (optional but highly recommended)

While Android Studio can be used for general development, the **`bundlelibs.py`** script (typically invoked via `build.sh`) **must** be executed inside a Termux environment. This script is responsible for pulling, patching, and preparing the Linux userland libraries directly from your Termux installation.

## Included Scripts

- **`build.sh`**: The primary automation script for the Termux workflow. It orchestrates versioning, bundling, patching, Gradle compilation, and deployment. It is designed to be run in Termux but can be adapted for other environments if the bundling step is handled.
- **`bundlelibs.py`**: The core framework component. It **must be run inside Termux**. it identifies required libraries, copies them into the project, and applies binary-level patching to redirect hardcoded Termux paths.
- **`renamepackage.py`**: A utility to quickly clone the project and repurpose it. It performs a global search-and-replace of the package name (default `com.alevap`), application name, and directory structures.
- **`backup.py`**: A specialized utility for incremental backups of the project state, configuration, and bundled assets during development.

This is meant to be a foundation, not a finished product.

## License

GPLv3

This project is based on and inspired by Termux X11, shared openly for experimentation and learning.

## Final Note

I built most of this on my phone. I have a kid and limited time, but Termux lets me work in short sessions wherever I am.

Maybe this helps someone out there.

---

## Technical Documentation & Framework Guide

### Core Architecture: The Standalone Challenge
Modern Android versions enforce strict "W^X" (Write XOR Execute) policies, which normally prevent an app from executing code from its own data directory. To make this framework work as a standalone "one-click" app, we currently use two specific technical strategies:

1.  **Target SDK 28:** We intentionally target Android SDK 28. This allows the application to maintain legacy execution permissions, enabling binaries like `bash`, `xterm`, and `jwm` to run directly from the internal files directory without being blocked by the system.
2.  **Binary Path Patching:** Standard Termux binaries are hardcoded to look for libraries and configurations in `/data/data/com.termux`. Since this app runs as `com.alevap` (or your custom package name), the build script (`bundlelibs.py` / `build.sh`) performs binary-level string replacement. It swaps all instances of `com.termux` with the new package name to ensure that standard Linux software can find its dependencies within the app's own private storage.

### How to Bundle Your Own App
To turn a Linux tool into a standalone Android app using this framework, follow these steps:

#### 1. Customize the Package Identity
Use the `renamepackage.py` script to change the application ID from `com.alevap` to your desired name (e.g., `com.myuser.mygame`). This ensures your app is unique and doesn't conflict with other installations.

#### 2. Import Binaries and Libraries
Place your Linux binaries and their `.so` dependencies into the project structure.
- **Binaries:** Should be placed in the bundling directory. The system will rename them to `lib[name].so` during the build process. This is a requirement for Android's package manager to extract them with the necessary execution permissions.
- **Assets:** Non-executable data (configs, icons, game files) should go into `app/src/main/assets/bootstrap`.

#### 3. Configure the Startup Sequence
Open `MainActivity.java` and locate the `launchJWM()` method. This is where the internal X server is initialized. To launch a specific app instead of a general desktop environment:
- Update the `ProcessBuilder` commands to point to your main binary.
- Ensure necessary environment variables (like `DISPLAY=:1`, `HOME`, and `LD_LIBRARY_PATH`) are correctly exported in the wrapper scripts created by the app at runtime.

#### 4. Adjust local.properties
The build system is designed to work inside Termux. You must ensure `local.properties` correctly points to your local Android SDK installation:
```properties
sdk.dir=/data/data/com.termux/files/home/androidsdk
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

### Build and Deployment
Run `./build.sh` to trigger the automated pipeline.
- It will clean the previous environment.
- It will pull libraries from your Termux system, patch them, and bundle them into the APK.
- If you have `adb` installed and your phone is in developer mode, it will automatically install and launch the app.

### Limitations
- **Experimental:** This is not a production-ready container solution. It is a hackable framework for enthusiasts.
- **Hardware Acceleration:** Full GPU support is a work in progress.
- **SDK Version:** While we target SDK 28 for execution support, the app can still be installed on newer Android versions.

---

## Credits

- Based on and inspired by the [Termux-X11](https://github.com/termux/termux-x11) project.
- Documentation and project structure optimization assisted by Gemini CLI.