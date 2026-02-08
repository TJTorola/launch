# Simple Android Launcher

A minimalist Android launcher app with a blank home screen and swipe-up app drawer.

## Features

- Blank black home screen
- Swipe up to reveal all installed apps
- Apps sorted alphabetically
- Tap to launch apps
- Swipe down to hide app drawer
- Back button hides app drawer

## NixOS Development Setup

This project is configured to build on NixOS using Nix flakes.

### Prerequisites

- NixOS with flakes enabled
- For building on NixOS, the project uses an FHS environment to run Android build tools

### Getting Started

1. **Enter the development environment:**
   ```bash
   nix develop
   ```

   This will:
   - Download and set up the Android SDK (API 34)
   - Configure all environment variables
   - Create `local.properties` automatically
   - Create `gradlew-fhs` wrapper script

2. **Build the app:**
   ```bash
   ./gradlew-fhs assembleDebug
   ```

   **Important:** Use `./gradlew-fhs` instead of `./gradlew` on NixOS. The FHS wrapper ensures Android build tools (like AAPT2) can run properly.

3. **Install to a connected Android device:**
   ```bash
   ./gradlew-fhs installDebug
   ```

4. **Check connected devices:**
   ```bash
   adb devices
   ```

### Build Output

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/
├── java/dev/torola/launch/
│   ├── MainActivity.kt      # Main activity with gesture handling
│   ├── AppInfo.kt           # Data class for app information
│   └── AppsAdapter.kt       # RecyclerView adapter for app list
├── res/
│   ├── layout/
│   │   ├── activity_main.xml   # Main layout (blank + RecyclerView)
│   │   └── item_app.xml        # App list item layout
│   └── values/
│       └── styles.xml          # Dark theme styles
└── AndroidManifest.xml      # Launcher configuration
```

## How It Works

1. The `AndroidManifest.xml` declares the app as a launcher with `CATEGORY_HOME` intent filter
2. `MainActivity` uses `GestureDetector` to detect swipe gestures
3. Apps are loaded using `PackageManager` and sorted alphabetically
4. `RecyclerView` is hidden by default (blank screen) and shown when swiping up
5. Tapping an app launches it via an `Intent`

## Setting as Default Launcher

**Important:** Launcher apps don't appear in your regular app drawer. They only appear when selecting a default launcher.

### Method 1: Using the Home Button
1. Press the **Home** button on your Android device
2. You'll be prompted to choose a launcher
3. Select "Simple Launcher" and tap "Always" or "Just once"

### Method 2: Via Settings
1. Open your device's **Settings**
2. Go to **Apps** or **Applications**
3. Tap the three dots menu or find **Default apps**
4. Select **Home app** or **Launcher**
5. Choose "Simple Launcher" from the list

### Method 3: Launch Manually (for testing)
To test the app without setting it as default:
```bash
adb shell am start -n dev.torola.launch/.MainActivity
```

### Reverting to Your Previous Launcher
If you want to go back to your original launcher:
1. Press the Home button
2. Select your previous launcher
3. Or go to Settings > Apps > Default apps > Home app

## Development Notes

- Minimum SDK: API 24 (Android 7.0)
- Target SDK: API 34 (Android 14)
- Language: Kotlin
- Build system: Gradle with Kotlin DSL

## Troubleshooting

### Build fails with AAPT2 errors on NixOS

Make sure you're using `./gradlew-fhs` instead of `./gradlew`. The FHS wrapper is required for Android build tools to run on NixOS.

### Gradle daemon issues

If you encounter daemon issues, stop all daemons:
```bash
./gradlew --stop
```

Then rebuild with `./gradlew-fhs assembleDebug`.
