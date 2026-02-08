# Launch - Android Launcher App

This is a minimal Android launcher application built with Kotlin. The launcher provides a distraction-free home screen with gesture-based navigation.

## Project Overview

Launch is a simple Android launcher that displays a blank black home screen. Users can swipe up to reveal an alphabetically sorted list of all installed apps. The app list includes a search field that automatically opens the keyboard, allowing users to quickly filter and launch apps.

## Technology Stack

- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Key Libraries**:
  - AndroidX Core KTX
  - AndroidX AppCompat
  - Material Components
  - RecyclerView

## Project Structure

```
app/src/main/
├── java/dev/torola/launch/
│   ├── MainActivity.kt          # Main launcher activity with gesture handling and app loading
│   ├── AppInfo.kt               # Data class for app information
│   ├── AppsAdapter.kt           # RecyclerView adapter with filtering capability
│   ├── PinShortcutActivity.kt   # Handles pinned shortcut requests from external apps
│   └── ShortcutReceiver.kt      # BroadcastReceiver for legacy shortcut installation
├── res/
│   ├── layout/
│   │   ├── activity_main.xml    # Main layout with blank screen and app drawer
│   │   └── item_app.xml         # Individual app item layout (text only)
│   └── values/
│       └── styles.xml           # Dark theme styles
└── AndroidManifest.xml          # Launcher configuration with HOME category

Root configuration files:
├── app/build.gradle.kts         # App-level build configuration with signing setup
├── build.gradle.kts             # Project-level build configuration
├── settings.gradle.kts          # Gradle project settings
├── gradle.properties            # Gradle properties and optimization settings
├── flake.nix                    # NixOS development environment configuration
├── gradlew-fhs                  # FHS wrapper for Gradle (use this on NixOS!)
└── keystore.properties.example  # Example keystore configuration for releases
```

## Development Environment (NixOS)

This project is configured to build on NixOS using Nix flakes with an FHS (Filesystem Hierarchy Standard) environment.

### Setup

```bash
# Enter the development environment
nix develop

# The environment automatically:
# - Sets up Android SDK (API 34, build tools, platform tools)
# - Configures ANDROID_HOME
# - Creates local.properties
# - Creates gradlew-fhs wrapper script
```

### Building

**IMPORTANT**: On NixOS, always use `./gradlew-fhs` instead of `./gradlew`. The FHS wrapper ensures Android build tools (like AAPT2) can run properly.

```bash
# Debug build (for development)
./gradlew-fhs assembleDebug

# Release build (requires keystore setup)
./gradlew-fhs assembleRelease

# Install to connected device
./gradlew-fhs installDebug
```

### Why FHS Wrapper?

Android build tools downloaded by Gradle are dynamically linked and cannot run directly on NixOS. The `gradlew-fhs` wrapper runs the entire Gradle build process inside an FHS environment with necessary libraries (glibc, zlib, etc.).

## Key Features

### 1. Gesture-Based Navigation
- **Swipe up**: Opens app drawer with keyboard
- **Swipe down**: Closes app drawer
- **Back button**: Closes app drawer (doesn't exit launcher)

### 2. App Search
- Keyboard automatically opens when app drawer is shown
- Real-time filtering as you type (case-insensitive)
- Press Enter/Go to launch first filtered app
- Apps are always sorted alphabetically

### 3. External Shortcut Support
The launcher supports adding shortcuts from external apps (like Hermit for PWAs):

- **Modern shortcuts (Android 8.0+)**: Uses `PinShortcutActivity` with LauncherApps API
- **Legacy shortcuts**: Handled via `ShortcutReceiver` broadcast receiver
- Shortcuts are stored in SharedPreferences
- Shortcuts appear alphabetically sorted with regular apps

### How Shortcuts Work
1. External app requests to pin a shortcut
2. `PinShortcutActivity` launches with confirmation dialog
3. If confirmed, shortcut ID and package are saved
4. Shortcuts are launched via `LauncherApps.startShortcut()`

## Code Standards

### Kotlin Conventions
- Use standard Kotlin patterns and idioms
- Keep code simple and readable
- Use data classes for models (e.g., `AppInfo`)
- Prefer `lateinit` for Android components initialized in `onCreate`

### Android Best Practices
- Activities should be simple and delegate complex logic to helper methods
- Use ViewBinding is not required - direct findViewById is fine for this simple app
- Permissions are declared in AndroidManifest.xml:
  - `QUERY_ALL_PACKAGES`: Required to list all apps on Android 11+
  - `INSTALL_SHORTCUT`: For legacy shortcut support

### File Organization
- Keep related functionality together
- One primary class per file
- Use meaningful names that describe purpose
- Add comments for non-obvious logic (e.g., shortcut handling)

## Automatic Installation

After completing any code change task, automatically install the debug build to a connected device:

1. Check for connected devices using `adb devices`
2. If at least one device is listed (with a device serial and "device" status), run `./gradlew-fhs installDebug`
3. If no devices are detected, skip the installation step

## Important Implementation Details

### App Loading (MainActivity.kt:133-181)
- Uses `PackageManager.queryIntentActivities()` with proper API level handling
- On Android 13+ (API 33), uses `ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())`
- Combines regular apps with shortcuts from SharedPreferences
- All apps sorted alphabetically by label (case-insensitive)

### Shortcut Storage
Shortcuts are stored in SharedPreferences with this structure:
```
shortcuts (SharedPreferences file)
├── shortcut_list: Set<String>          # Set of shortcut IDs
└── For each shortcut ID:
    ├── {id}_name: String                # Display name
    ├── {id}_shortcut_id: String         # ShortcutInfo ID (for pinned shortcuts)
    ├── {id}_package: String             # Package name (for pinned shortcuts)
    └── {id}_intent: String              # Intent URI (for legacy shortcuts)
```

### Launching Apps vs Shortcuts (MainActivity.kt:208-234)
- Regular apps: Use `Intent(ACTION_MAIN)` with component name
- Pinned shortcuts: Use `LauncherApps.startShortcut()` with shortcut ID
- Legacy shortcuts: Parse intent URI and launch directly
- All launches hide the app drawer after starting

## Building for Production

### Option 1: Signed Release Build

1. Generate signing key (one-time):
```bash
keytool -genkey -v -keystore ~/launch-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias launch
```

2. Create `keystore.properties` in project root:
```properties
storePassword=your_keystore_password
keyPassword=your_key_password
keyAlias=launch
storeFile=/home/tjtorola/launch-keystore.jks
```

3. Build and install:
```bash
nix develop
./gradlew-fhs assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

### Option 2: Debug Build (Personal Use)
```bash
nix develop
./gradlew-fhs assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Security Notes

- **Never commit** `keystore.properties` or `.jks` files to git (already in .gitignore)
- Keep your keystore password safe - losing it means you can't update the app
- The debug build is fine for personal use; release builds are needed for distribution

## Testing on Device

The app requires being set as the default launcher.

**Important:** Launcher apps don't appear in your regular app drawer. They only appear when selecting a default launcher.

### Method 1: Using the Home Button
1. Press the **Home** button on your Android device
2. You'll be prompted to choose a launcher
3. Select "Launch" and tap "Always" or "Just once"

### Method 2: Via Settings
1. Open your device's **Settings**
2. Go to **Apps** or **Applications**
3. Tap the three dots menu or find **Default apps**
4. Select **Home app** or **Launcher**
5. Choose "Launch" from the list

### Method 3: Launch Manually (for testing)
To test the app without setting it as default:
```bash
adb shell am start -n dev.torola.launch/.MainActivity
```

### Reverting to Your Previous Launcher
If you want to go back to your original launcher:
1. Press the Home button
2. Select your previous launcher
3. Or go to Settings → Apps → Default apps → Home app

## Common Issues

### Build fails with AAPT2 errors
Make sure you're using `./gradlew-fhs` instead of `./gradlew` on NixOS.

### Gradle daemon issues
```bash
./gradlew --stop
./gradlew-fhs assembleDebug
```

### Not all apps showing
Ensure `QUERY_ALL_PACKAGES` permission is in the manifest (it is).

### Shortcut not appearing after adding
Check logs: `adb logcat -d | grep PinShortcutActivity`

## Future Development Ideas

- Add customizable gestures for launching specific apps
- Support for app widgets
- Dark/light theme toggle
- Custom app list sorting options
- App hiding/favorites functionality
- Custom icon support for shortcuts

## External Resources

For more information on Android launcher development:
- [Android Launcher Apps Documentation](https://developer.android.com/guide/components/intents-common#Home)
- [Pinned Shortcuts API](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts#pin-shortcut)
- [NixOS Android Development](https://nixos.wiki/wiki/Android)
