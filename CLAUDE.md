# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Yanami is a Material Design 3 Android client for the **Komari** server monitoring tool. It provides real-time WebSocket updates, multi-instance management, and server status visualization.

- **Package:** `com.sekusarisu.yanami`
- **Language:** Kotlin | **Min SDK:** 28 | **Target/Compile SDK:** 36
- **UI:** Jetpack Compose with Material 3

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "com.sekusarisu.yanami.ExampleUnitTest"

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean assembleDebug
```

## Architecture

**MVI (Model-View-Intent)** pattern with three layers:

- **UI Layer:** Voyager `Screen` + Compose UI + `MviViewModel<State, Event, Effect>`
- **Domain Layer:** Repository interfaces + domain models (`Node`, `ServerInstance`, etc.)
- **Data Layer:** Repository implementations, Ktor (HTTP + WebSocket), Room DB, DataStore

Each screen follows a **Contract pattern** — a `Contract` object containing nested `State`, `Event`, and `Effect` types (e.g., `NodeListContract`).

### Key Libraries

| Library | Purpose |
|---|---|
| Voyager 1.1.0-beta03 | Navigation + ScreenModel lifecycle |
| Koin 4.1.1 | Dependency injection |
| Ktor 3.4.0 | HTTP client + WebSocket (RPC2 protocol) |
| Room 2.8.4 + KSP | Local database (encrypted credentials) |
| Vico 2.2.0 | Charts (Compose M3) |
| DataStore | User preferences (theme, language, dark mode) |

### Navigation Flow (Voyager)

```
ServerListScreen → AddServerScreen → NodeListScreen → NodeDetailScreen
                                   ↘ SettingsScreen
```

### Data & Networking

- **Komari API** uses JSON-RPC 2.0 over WebSocket (`wss://domain/api/rpc2`) as primary transport, with HTTP POST fallback.
- WebSocket requires `Origin` header — without it, server returns 403.
- Authentication via `session_token` cookie, injected by `SessionCookieInterceptor` (OkHttp).
- Credentials encrypted with AES/GCM via Android KeyStore (`CryptoManager`).
- API details documented in `docs/API_STRUCTURES.md`.

### DI Setup

All dependencies registered in `di/AppModule.kt` via Koin. App initialized in `YanamiApplication.kt`.

## Internationalization

Default language is **Chinese (zh)**. Also supports English (en) and Japanese (ja). String resources in `res/values/`, `res/values-en/`, `res/values-ja/`. Runtime switching uses `AppCompatDelegate.setApplicationLocales()` — requires `AppCompatActivity`.

## Documentation

- `docs/ARCHITECTURE.md` — Architecture diagrams and flows (Chinese)
- `docs/API_STRUCTURES.md` — Complete Komari API reference
- `docs/PROGRESS.md` — Development status and session notes
- `REQUEST.MD` — Original requirements specification
