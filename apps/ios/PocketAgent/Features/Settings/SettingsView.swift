// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @AppStorage("appearance") private var appearance = 0
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
            Form {
                Section {
                    if let id = model.identity {
                        LabeledContent("Tür", value: "Ed25519 · Keychain")
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Parmak izi").font(.caption).foregroundStyle(.secondary)
                            Text(id.fingerprint).font(.caption.monospaced()).textSelection(.enabled)
                                .accessibilityIdentifier("deviceFingerprint")
                        }
                        Button(copied ? "Kopyalandı" : "Public key'i kopyala") {
                            UIPasteboard.general.string = id.publicKeyOpenSSH
                            copied = true
                        }
                        Button("Yeni anahtar üret", role: .destructive) { confirmRegenerate = true }
                    } else {
                        ErrorBanner(message: model.identityError ?? "Anahtar yüklenemedi")
                    }
                } header: { Text("Cihaz anahtarı") } footer: {
                    Text("Özel anahtar yalnız bu cihazın Keychain'inde; yedeklere ve iCloud'a girmez.")
                }

                Section("Backend'ler") {
                    if model.accounts.isEmpty {
                        Text("Kodla eşleme backend'i otomatik ekler.").foregroundStyle(.secondary)
                    }
                    ForEach(model.accounts) { a in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(a.displayHost).font(.headline)
                                Spacer()
                                if let c = checks[a.url] {
                                    Text(c).font(.caption.bold()).foregroundStyle(c == "Geçerli" ? .green : .orange)
                                }
                            }
                            Text(a.url).font(.caption.monospaced()).foregroundStyle(.secondary)
                        }
                        .swipeActions { Button("Kaldır", role: .destructive) { model.removeAccount(a) } }
                        .task { await check(a) }
                    }
                }

                Section("Görünüm") {
                    Picker("Tema", selection: $appearance) {
                        Text("Sistem").tag(0)
                        Text("Açık").tag(1)
                        Text("Koyu").tag(2)
                    }
                    Stepper(value: $fontSize, in: 8...24, step: 1) {
                        LabeledContent("Terminal yazı boyutu", value: "\(Int(fontSize)) pt")
                    }
                }

                Section("Gizlilik") {
                    Label("Terminal içeriği backend'e hiç gitmez; yalnız ajan olay özetleri (24 saat).", systemImage: "lock.shield")
                    Label("Analitik veya izleme yok.", systemImage: "eye.slash")
                }
                .font(.footnote)

                Section("Hakkında") {
                    LabeledContent("Sürüm", value: version)
                    Link("Kaynak kod (GPL-3.0)", destination: URL(string: "https://github.com/azygoss/pocket-agent")!)
                    NavigationLink("Açık kaynak lisansları") { LicensesView() }
                }
            }
            .navigationTitle("Ayarlar")
            .confirmationDialog("Yeni anahtar üretilsin mi?", isPresented: $confirmRegenerate, titleVisibility: .visible) {
                Button("Üret", role: .destructive) { try? model.regenerateKey() }
            } message: {
                Text("Eski anahtar silinir; tüm host'ları yeniden eşlemen gerekir.")
            }
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

struct LicensesView: View {
    private let items: [(String, String)] = [
        ("Citadel", "MIT · SSH istemcisi, SFTP"),
        ("SwiftNIO / SwiftNIO SSH", "Apache-2.0"),
        ("swift-crypto", "Apache-2.0"),
        ("SwiftTerm", "MIT · terminal emülatörü"),
        ("BigInt", "MIT"),
        ("swift-log", "Apache-2.0"),
    ]
    var body: some View {
        List(items, id: \.0) { item in
            VStack(alignment: .leading) {
                Text(item.0).font(.headline)
                Text(item.1).font(.caption).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Lisanslar")
    }
}
