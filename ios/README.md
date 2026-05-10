# Yanami iPhone

Native SwiftUI iPhone app for Yanami.

## Scope

- Connects to Komari with password, API Key, or guest mode.
- Supports custom HTTP headers, including Cloudflare Access service token headers.
- Stores the server profile and credentials in Keychain.
- Tests the connection through `common:getVersion`.
- Loads node information and latest status through Komari RPC.

## Build

```bash
xcodebuild \
  -project Yanami.xcodeproj \
  -scheme Yanami \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath ../build/ios \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The debug simulator app is generated at `../build/ios/Build/Products/Debug-iphonesimulator/Yanami.app`.

Signed iPhone device builds require Apple signing credentials and a provisioning profile in CI.
