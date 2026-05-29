# Development Setup Guide

Looking to contribute? Great! Here's everything you need to get started with NyanTV development.

## Tech Stack

NyanTV is built with modern Android development tools:
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose for Android TV
- **Architecture:** MVVM with Clean Architecture
- **Build System:** Gradle with Kotlin DSL
- **Minimum SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 15 (API 35)

## Prerequisites

### Android Studio
Download and install the latest stable version of [Android Studio](https://developer.android.com/studio). We recommend using at least **Android Studio Hedgehog (2023.1.1)** or newer.

During installation, ensure these components are selected:
- Android SDK
- Android SDK Platform
- Android TV System Image (for emulator testing)

### JDK 17
NyanTV uses Java 17. Android Studio bundles a compatible JDK, but if you need to manage multiple versions:
- **Windows/Linux:** [SDKMAN!](https://sdkman.io/)
- **macOS:** [jEnv](https://github.com/jenv/jenv) or `brew install openjdk@17`

### Android TV Emulator (Optional)
For testing on a virtual device:
1. Open Android Studio
2. Go to **Device Manager** → **Create Device**
3. Select **TV** category (e.g., "Android TV 1080p")
4. Choose a system image (API 30+ recommended)
5. Finish the setup

> [!TIP]
> If you have a physical Android TV device, you can connect it via ADB over network for testing.

## Project Setup

### 1. Clone the Repository
```bash
git clone https://github.com/NyanTV/NyanTV.git
cd NyanTV
```

### 2. Open in Android Studio
- Select **File → Open**
- Navigate to the cloned repository folder
- Wait for Gradle sync to complete

### 3. Configure API Keys

**Important:** Never commit real API keys! The `gradle.properties` file is public and must only contain build settings.

Create a `local.properties` file in the project root. This file is already in `.gitignore` and will never be committed:

```properties
ANILIST_CLIENT_ID=your_anilist_id
ANILIST_CLIENT_SECRET=your_anilist_secret
MAL_CLIENT_ID=your_mal_id
MAL_CLIENT_SECRET=your_mal_secret
SIMKL_CLIENT_ID=your_simkl_id
SIMKL_CLIENT_SECRET=your_simkl_secret
CALLBACK_SCHEME=nyantv://callback
```

For UI development without API access, you can use `0` as placeholder values. The app will show connection errors but the UI will be fully testable.

#### Getting real API Keys
- **AniList:** [Developer Portal](https://anilist.co/settings/developer) → Create Client → Redirect URL: `nyantv://callback`
- **MyAnimeList:** [MAL API Registration](https://myanimelist.net/apiconfig)
- **Simkl:** [Simkl Developer](https://simkl.com/settings/developer/)

### 4. Build and Run

**Local development** (uses `local.properties`):
```bash
./gradlew assembleDebug
./gradlew installDebug
```

**With command-line flags** (overrides `local.properties`):
```bash
./gradlew assembleDebug \
  -PANILIST_CLIENT_ID=12345 \
  -PANILIST_CLIENT_SECRET=abc123 \
  -PMAL_CLIENT_ID=67890 \
  -PMAL_CLIENT_SECRET=def456 \
  -PSIMKL_CLIENT_ID=11111 \
  -PSIMKL_CLIENT_SECRET=ghi789
```

Or just use Android Studio's Run button (green triangle).

### How Secrets Work

The `app/build.gradle.kts` reads secrets in this priority:
1. **Command line `-P` flags** (highest priority)
2. **`local.properties` file** (local development)
3. **Fallback defaults** like `"0"` (allows compilation without keys)

This means:
- Local devs put keys in `local.properties`
- CI/CD injects keys via `-P` flags from GitHub Secrets
- Nobody ever commits real keys to the repository

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/nyantv/
│   │   │   ├── data/           # Data layer (repositories, APIs)
│   │   │   ├── extensions/     # Aniyomi extension support
│   │   │   ├── player/         # Video player
│   │   │   ├── ui/             # Compose UI components
│   │   │   │   ├── anime/      # Anime detail screen
│   │   │   │   ├── home/       # Home screen
│   │   │   │   ├── library/    # Library screen
│   │   │   │   ├── player/     # Player screen
│   │   │   │   ├── screens/    # Detail, Search, PlayerTab
│   │   │   │   ├── settings/   # Settings screens
│   │   │   │   ├── theme/      # App theming
│   │   │   │   ├── utils/      # Helper functions
│   │   │   │   └── widgets/    # Reusable components
│   │   │   └── viewmodel/      # AppViewModel
│   │   └── res/                # Resources (drawables, fonts)
└── build.gradle.kts            # App-level build config
```

## Development Workflow

### Code Style
- We follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- D-pad friendly navigation patterns for TV
- Compose components should follow [TV Compose Guidelines](https://developer.android.com/develop/ui/compose/tv)

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Troubleshooting

### Gradle Sync Issues
- **File → Invalidate Caches and Restart** in Android Studio
- Delete `.gradle/` folder and sync again
- Check that you're using JDK 17 in **File → Project Structure → SDK Location**

### Build Errors
```bash
# Clean build cache
./gradlew clean

# Refresh dependencies
./gradlew build --refresh-dependencies
```

### API Keys Not Found
- Verify your `local.properties` exists in the project root
- Check that property names match exactly (case-sensitive): `ANILIST_CLIENT_ID`, not `anilist_client_id`
- For CI builds, check that `-P` flags are correctly passed

### Emulator Not Showing in TV Mode
- Ensure you selected a **TV** device profile, not a phone/tablet
- The emulator should show a TV interface with D-pad navigation
- Enable **Host GPU** in emulator settings for better performance

### API Connection Issues
- Verify your API keys in `local.properties`
- Check network permissions in `AndroidManifest.xml`
- Ensure the redirect URL matches exactly: `nyantv://callback`

## Community

Feel free to reach out for help or discussion:
- [Telegram](https://t.me/NyanSupport)
- [Discord](https://discord.gg/y2vaFPXs4F)
- [Stoat](https://stoat.chat/invite/fKzse8yy)

We're always happy to assist contributors! 🎉