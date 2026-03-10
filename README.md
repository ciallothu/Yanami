English | [简体中文](README_zh.md)

# Yanami

**Yanami** is an Android client for the [Komari](https://github.com/komari-monitor/komari) server monitoring tool, built with the Material Design 3 design language.

> A Material Design 3 Android client for the Komari server monitoring tool.

---

## Features

- **Multi-Instance Management** — Add, edit, and switch between multiple Komari server instances.
- **Password or API-KEY Authentication** — Support password or API-KEY authentication.
- **Real-Time Node List** — WebSocket real-time push for node status (CPU / RAM / Disk / Network IO).
- **Node Detail Dashboard** — Load history line charts, Ping latency trends, basic server information.
- **SSH Terminal** — Full-featured ANSI/VT100 terminal based on termux terminal-view + WebSocket, supporting special key toolbars and font size adjustment.
- **Multi-Language Support** — Chinese (Default), English, Japanese.
- **Theme System** — Material You dynamic colors (Android 12+) + 6 preset color palettes, supporting dark/light mode and system-following mode.

## Screenshots

### Instance Management

<p style="text-align: center;">
    <img alt="desktop" src="assets/addserver.png" width="360"> <img alt="desktop" src="assets/serverlist.png" width="360">
</p>

### Day/Light Mode

<p style="text-align: center;">
    <img alt="desktop" src="assets/nodelist.png" width="360"> <img alt="desktop" src="assets/nodedetail1.png" width="360">
</p>

### Night/Dark Mode

<p style="text-align: center;">
    <img alt="desktop" src="assets/nodelistdark.png" width="360"> <img alt="desktop" src="assets/nodedetaildark.png" width="360">
</p>

### Latency Monitoring/SSH Terminal

<p style="text-align: center;">
    <img alt="desktop" src="assets/nodedetail2.png" width="360"> <img alt="desktop" src="assets/nodeterminal.png" width="360">
</p>

## System Requirements

| Item | Requirement |
|---|---|
| Android | 9.0 (API 28) and above |
| Server | Komari 1.1.7 or above |

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Clean and Build
./gradlew clean assembleDebug
```

Build outputs are located at `app/build/outputs/apk/`.

## Tech Stack

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.3.10 | Main language |
| Jetpack Compose BOM | 2026.02.01 | UI Framework |
| MD3 | — | Design System |
| Voyager | 1.1.0-beta03 | Navigation + ScreenModel |
| Koin | 4.1.1 | Dependency Injection |
| Ktor | 3.4.1 | HTTP Client + WebSocket |
| Room | 2.8.4 | Local Database (Encrypted credential storage) |
| Vico | 3.0.4 | Charts (Compose M3) |
| termux terminal-view | 0.119.0-beta.3 | Terminal ANSI/VT100 Rendering |
| DataStore Preferences | 1.2.0 | User Preferences Persistence |

## Architecture

Adopts the **MVI (Model-View-Intent)** pattern, separated into three layers:

```
UI Layer      Voyager Screen + Compose UI + MviViewModel<State, Event, Effect>
Domain Layer  Repository Interface + Domain Model (Node, ServerInstance …)
Data Layer    Repository Implementation, Ktor, Room, DataStore
```

Each page follows the **Contract Pattern**, describing the complete MVI contract of the page with nested `State` / `Event` / `Effect`.

### Navigation Flow

```
ServerListScreen → AddServerScreen
                 → NodeListScreen → NodeDetailScreen → SshTerminalScreen
                 → SettingsScreen
```

### Authentication & Network

- Obtain `session_token` via `POST /api/login` (supports 2FA).
- Token is encrypted with AES/GCM and stored in Room, automatically restored on startup.
- WebSocket (`wss://host/api/rpc2`) requires `Cookie: session_token` and `Origin` headers.
- `SessionCookieInterceptor` (OkHttp) automatically injects the Cookie.

## License

This project is licensed under the [MIT License](LICENSE).
