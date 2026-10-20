// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("appearance") private var appearance = 2
    @AppStorage("terminalFontSize") private var fontSize = 13.0
    @State private var copied = false
    @State private var confirmRegenerate = false
    @State private var checks: [String: String] = [:]

    private var version: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }

    var body: some View {
        NavigationStack {
            PocketScreen {
                VStack(spacing: 0) {
                    PocketTopBar("Ayarlar")
                    ScrollView {
                        VStack(alignment: .leading, spacing: PA.Space.xl) {
                            group("Kişiselleştirme") {
                                SettingsRow(icon: "paintpalette", tone: .violet, title: "Görünüm",
                                            subtitle: ["Sistem", "Açık", "Koyu"][appearance]) {
                                    Picker("", selection: $appearance) {
                                        Text("Sistem").tag(0); Text("Açık").tag(1); Text("Koyu").tag(2)
                                    }.pickerStyle(.menu).tint(PA.text2)
                                }
                                InsetDivider()
                                SettingsRow(icon: "textformat.size", tone: .busy, title: "Terminal yazı boyutu",
                                            subtitle: "\(Int(fontSize)) pt · JetBrains Mono") {
                                    Stepper("", value: $fontSize, in: 8...24, step: 1).labelsHidden()
                                }
                            }
                            group("Bağlantı") {
                                deviceKey
                                InsetDivider()
                                if model.accounts.isEmpty {
                                    SettingsRow(icon: "antenna.radiowaves.left.and.right", tone: .neutral, title: "Backend",
                                                subtitle: "Kodla eşleme backend'i otomatik ekler") { EmptyView() }
                                }
                                ForEach(model.accounts) { a in
                                    SettingsRow(icon: "antenna.radiowaves.left.and.right", tone: .accent, title: a.displayHost,
                                                subtitle: a.url, subtitleMono: true) {
                                        if let c = checks[a.url] {
                                            StatusChip(text: c, tone: c == "Geçerli" ? .live : .warn)
                                        } else {
                                            ProgressView().tint(PA.accent)
                                        }
                                    }
                                    .contextMenu { Button("Backend'i kaldır", role: .destructive) { model.removeAccount(a) } }
                                    .task { await check(a) }
                                }
                                InsetDivider()
                                SettingsRow(icon: "key.horizontal", tone: .warn, title: "Bilinen host anahtarları",
                                            subtitle: "\(model.hosts.filter { !$0.pinnedHostKeys.isEmpty }.count) anahtar pinli") { EmptyView() }
                            }
                            group("Uygulama") {
                                SettingsRow(icon: "lock.shield", tone: .live, title: "Gizlilik",
                                            subtitle: "Terminal içeriği backend'e gitmez; yalnız 24 saatlik ajan olay özetleri. Analitik yok.") { EmptyView() }
                                InsetDivider()
                                Link(destination: URL(string: "https://github.com/azygoss/pocket-agent")!) {
                                    SettingsRow(icon: "chevron.left.forwardslash.chevron.right", tone: .neutral, title: "Kaynak kod",
                                                subtitle: "GPL-3.0 · github.com/azygoss/pocket-agent") { Image(systemName: "arrow.up.right").foregroundStyle(PA.text2) }
                                }
                                InsetDivider()
                                NavigationLink { LicensesView() } label: {
                                    SettingsRow(icon: "info.circle", tone: .neutral, title: "Hakkında",
                                                subtitle: "Sürüm \(version) · açık kaynak lisansları") { Chevron() }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .screenPadding().padding(.bottom, PA.Space.x3)
                    }
                }
            }
            .confirmationDialog("Yeni anahtar üretilsin mi?", isPresented: $confirmRegenerate, titleVisibility: .visible) {
                Button("Üret", role: .destructive) { try? model.regenerateKey() }
            } message: {
                Text("Eski anahtar silinir; tüm host'ları yeniden eşlemen gerekir.")
            }
        }
        .tint(PA.accent)
    }

    @ViewBuilder private var deviceKey: some View {
        if let id = model.identity {
            VStack(alignment: .leading, spacing: PA.Space.md) {
                SettingsRow(icon: "key.fill", tone: .accent, title: "Cihaz anahtarı", subtitle: "Ed25519 · Keychain · yedeklere girmez") { EmptyView() }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Parmak izi").font(PAFont.labelSmall).foregroundStyle(PA.text2)
                    Text(id.fingerprint).font(PAFont.mono(12.5)).foregroundStyle(PA.text).textSelection(.enabled)
                        .accessibilityIdentifier("deviceFingerprint")
                }
                .padding(PA.Space.md).frame(maxWidth: .infinity, alignment: .leading)
                .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
                .padding(.horizontal, PA.Space.lg)
                HStack(spacing: PA.Space.sm) {
                    Button(copied ? "Kopyalandı" : "Public key'i kopyala") {
                        UIPasteboard.general.string = id.publicKeyOpenSSH
                        copied = true
                    }.buttonStyle(.tonal)
                    Button("Yeni anahtar üret") { confirmRegenerate = true }.buttonStyle(TonalButtonStyle(tone: .danger))
                }
                .padding(.horizontal, PA.Space.lg).padding(.bottom, PA.Space.md)
            }
        } else {
            ErrorBanner(message: model.identityError ?? "Anahtar yüklenemedi").padding(PA.Space.sm)
        }
    }

    private func group<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: PA.Space.xs) {
            SectionHeader(title)
            VStack(spacing: 0) { content() }.padding(.vertical, PA.Space.xs).pocketCard()
        }
    }

    private func check(_ a: BackendAccount) async {
        do {
            _ = try await BackendClient(baseURL: a.url, token: model.token(for: a)).whoami()
            checks[a.url] = "Geçerli"
        } catch BackendError.unauthorized {
            checks[a.url] = "Token geçersiz"
        } catch {
            checks[a.url] = "Erişilemedi"
        }
    }
}

struct SettingsRow<Trailing: View>: View {
    var icon: String
    var tone: Tone
    var title: String
    var subtitle: String?
    var subtitleMono = false
    @ViewBuilder var trailing: Trailing
    var body: some View {
        HStack(spacing: PA.Space.md) {
            IconTile(icon: icon, tone: tone, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(PAFont.titleMedium).foregroundStyle(PA.text)
                if let subtitle {
                    Text(subtitle).font(subtitleMono ? PAFont.mono(12) : PAFont.bodySmall).foregroundStyle(PA.text2).lineLimit(3)
                }
            }
            Spacer(minLength: PA.Space.sm)
            trailing
        }
        .padding(.horizontal, PA.Space.lg).padding(.vertical, PA.Space.md)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }
}

struct LicensesView: View {
    @Environment(\.dismiss) private var dismiss
    private let items: [(String, String, String)] = [
        ("Citadel", "MIT · SSH istemcisi, SFTP", "lock"),
        ("SwiftNIO / SwiftNIO SSH", "Apache-2.0", "network"),
        ("swift-crypto", "Apache-2.0", "key"),
        ("SwiftTerm", "MIT · terminal emülatörü", "terminal"),
        ("BigInt", "MIT", "number"),
        ("swift-log", "Apache-2.0", "doc.text"),
        ("IBM Plex, JetBrains Mono", "OFL-1.1 · yazı tipleri", "textformat"),
    ]
    var body: some View {
        PocketScreen {
            VStack(spacing: 0) {
                PocketTopBar("Lisanslar", subtitle: "Açık kaynak bileşenler", back: { dismiss() })
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                            SettingsRow(icon: item.2, tone: .neutral, title: item.0, subtitle: item.1) { EmptyView() }
                            if i < items.count - 1 { InsetDivider() }
                        }
                    }
                    .padding(.vertical, PA.Space.xs).pocketCard()
                    .screenPadding().padding(.bottom, PA.Space.x3)
                }
            }
        }
    }
}
