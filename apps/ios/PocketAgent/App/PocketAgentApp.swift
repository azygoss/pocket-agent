// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

@main
struct PocketAgentApp: App {
    @StateObject private var model = AppModel()
    @AppStorage("appearance") private var appearance = 2

    init() {
        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = UIColor(PA.card)
        tab.shadowColor = UIColor(PA.line)
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(appearance == 1 ? .light : appearance == 2 ? .dark : nil)
                .onOpenURL { url in
                    if let t = PairingLink.parse(url.absoluteString) { model.pendingPairLink = t }
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab = 0
    @State private var showPair = false
    @Environment(\.scenePhase) private var phase

    var body: some View {
        Group {
            if model.hosts.isEmpty && model.accounts.isEmpty {
                WelcomeView(showPair: $showPair)
            } else {
                TabView(selection: $tab) {
                    HostsView(showPair: $showPair)
                        .tabItem { Label("Oturumlar", systemImage: "terminal") }.tag(0)
                    InboxView()
                        .tabItem { Label("Gelen", systemImage: "tray.full") }.tag(1)
                        .badge(model.pendingApprovals)
                    SettingsView()
                        .tabItem { Label("Ayarlar", systemImage: "gearshape") }.tag(2)
                }
                .tint(PA.accent)
            }
        }
        .sheet(isPresented: $showPair) { PairingView() }
        .hostKeyPrompt(active: !model.pairingSheetVisible)
        .onChange(of: model.pendingPairLink) { _, link in if link != nil { showPair = true } }
        .task {
            while !Task.isCancelled {
                if phase == .active { await model.refreshInbox() }
                try? await Task.sleep(nanoseconds: 15_000_000_000)
            }
        }
    }
}
