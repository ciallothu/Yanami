import LocalAuthentication
import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedTab = 0
    @State private var unlocked = false

    var body: some View {
        Group {
            if store.settings.biometricEnabled && !unlocked {
                BiometricLockView {
                    authenticate()
                }
            } else {
                tabs
            }
        }
    }

    private var tabs: some View {
        TabView(selection: $selectedTab) {
            ServerListView()
                .tabItem {
                    Label("Servers", systemImage: "server.rack")
                }
                .tag(0)

            NodeListView()
                .tabItem {
                    Label("Nodes", systemImage: "list.bullet.rectangle")
                }
                .tag(1)

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
                .tag(2)
        }
        .environment(\.sizeCategory, store.settings.fontScale.contentSizeCategory)
        .preferredColorScheme(store.settings.darkMode.colorScheme)
        .task {
            if store.settings.autoEnterNodeList, store.activeServer != nil {
                selectedTab = 1
            }
            if store.activeServer != nil && store.nodes.isEmpty {
                await store.loadNodes(mode: .initial)
            }
        }
        .onChange(of: store.settings.autoEnterNodeList) { enabled in
            if enabled, store.activeServer != nil {
                selectedTab = 1
            }
        }
        .onChange(of: store.settings.biometricEnabled) { enabled in
            unlocked = !enabled
        }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?
        let policy = LAPolicy.deviceOwnerAuthentication
        guard context.canEvaluatePolicy(policy, error: &error) else {
            unlocked = true
            return
        }
        context.evaluatePolicy(policy, localizedReason: "Unlock YanamiNext") { success, _ in
            if success {
                DispatchQueue.main.async {
                    unlocked = true
                }
            }
        }
    }
}

private struct BiometricLockView: View {
    let onUnlock: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.shield")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
            Text("YanamiNext Locked")
                .font(.title3.bold())
            Button("Unlock", action: onUnlock)
                .buttonStyle(.borderedProminent)
        }
        .padding()
        .onAppear(perform: onUnlock)
    }
}

private extension String {
    var colorScheme: ColorScheme? {
        switch self {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}

private extension Double {
    var contentSizeCategory: ContentSizeCategory {
        switch self {
        case ..<0.9: return .small
        case 0.9..<1.1: return .medium
        case 1.1..<1.25: return .large
        default: return .extraLarge
        }
    }
}
