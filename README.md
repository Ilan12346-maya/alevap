# Termux X11 Standalone Android App Framework

![Screenshot](https://i.ibb.co/qFXJsjg6/Screenshot-20260204-220716.jpg)
![Screenshot](https://i.ibb.co/jv5NS6MM/Screenshot-20260204-220645.jpg)

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

To build this project entirely within Termux, you need the following environment:

### 1. Required Termux Packages
Install these via `pkg install`:
- **General Build Tools:** `pkg install python cmake ninja git findutils sed grep binutils`
- **Java Development:** `pkg install openjdk-17` (or openjdk-21)
- **Android NDK:** This project is built using the optimized [termux-ndk](https://github.com/lzhiyong/termux-ndk). Follow the instructions there to install it.
- **Optional:** `pkg install adb` (for deployment)

### 2. External Dependencies
- **Android SDK:** Command-line tools must be installed (usually in `~/androidsdk`).
- **AAPT2:** The build uses the system-native aapt2 (included in most Termux environments or available via `pkg install aapt2`).

While Android Studio can be used for general development, the **`bundlelibs.py`** script (typically invoked via `build.sh`) **must** be executed inside a Termux environment. This script pulls, patches, and prepares the Linux userland libraries directly from your Termux installation.

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
Use the `renamepackage.py` script to change the application ID. 

**CRITICAL LIMITATION:** Your new package name **MUST be exactly 10 characters long** (just like `com.alevap`). 
*   *Why?* The binary patching engine performs direct string replacement in pre-compiled binaries. To preserve binary offsets and avoid crashing the app, the character count must remain identical. (e.g., `com.mytool`). This is a technical shortcut that might be improved later, but for now, it's a hard requirement.

#### 2. Import Binaries and Libraries
Place your Linux binaries and their `.so` dependencies into the project structure.
- **Binaries:** Should be placed in the bundling directory. The system will rename them to `lib[name].so` during the build process for proper execution permissions.
- **Assets:** Non-executable data should go into `app/src/main/assets/bootstrap`.

#### 3. Configure the Startup Sequence
Open `MainActivity.java` and locate the `launchJWM()` method. Update the `ProcessBuilder` commands to point to your main binary and ensure environment variables are set correctly.

#### 4. Adjust local.properties
Ensure `local.properties` correctly points to your local Android SDK and AAPT2 path within Termux.

### Build and Deployment
Run `./build.sh` to clean, bundle, patch, and compile. If ADB is active, it will auto-deploy.

---

## Developer's Mission & Community

While I take issues and feedback seriously, please understand that this is a personal passion project. My primary technical goal is to have **Need for Speed Underground (NFSU)** running as a native-feeling standalone Android app.

Feel free to fork this, tear it apart, and build your own things. If you have a great idea but aren't sure how to implement the code, I highly recommend asking an AI like **Gemini** or **Claude** to help you navigate the logic—that's how a lot of this framework was optimized!

### Limitations
- **Experimental:** This is not a production-ready container solution. It is a hackable framework for enthusiasts.
- **Hardware Acceleration:** Full GPU support is a work in progress.
- **SDK Version:** While we target SDK 28 for execution support, the app can still be installed on newer Android versions.


---

## Credits

- Based on and inspired by the [Termux-X11](https://github.com/termux/termux-x11) project.
- Documentation and project structure optimization assisted by Gemini CLI.
