// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct WelcomeView: View {
    @Binding var showPair: Bool
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                Spacer(minLength: 40)
                ZStack {
                    RoundedRectangle(cornerRadius: 30, style: .continuous).fill(Theme.brandGradient)
                        .frame(width: 112, height: 112).shadow(color: .purple.opacity(0.35), radius: 24, y: 12)
                    Image(systemName: "terminal.fill").font(.system(size: 50, weight: .semibold)).foregroundStyle(.white)
                }
                VStack(spacing: 10) {
                    Text("Pocket Agent").font(.largeTitle.bold())
                    Text("Sunucundaki terminal oturumları, tmux ve kodlama ajanların cebinde.")
                        .font(.title3).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                VStack(alignment: .leading, spacing: 18) {
                    feature("qrcode.viewfinder", "Kodla eşle", "Host'ta pocket-agent pair; QR'ı tara ya da kodu yaz.")
                    feature("key.horizontal.fill", "Anahtar cihazda kalır", "SSH anahtarın telefonda üretilir; yalnız public key gider.")
                    feature("rectangle.stack.fill", "Kalıcı tmux oturumları", "Bağlantı kopsa da iş sunucuda devam eder.")
                    feature("bell.badge.fill", "Ajan bildirimleri", "Claude/Codex onay isteklerini gör, onayla.")
                }
                .padding(20)
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                Button {
                    showPair = true
                } label: {
                    Label("Host eşle", systemImage: "qrcode.viewfinder").font(.headline)
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
                .accessibilityIdentifier("pairButton")
                Text("Host'ta: npm i -g pocket-agent-cli && pocket-agent onboard")
                    .font(.footnote.monospaced()).foregroundStyle(.secondary).textSelection(.enabled)
            }
            .padding(24)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
    }

    private func feature(_ icon: String, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon).font(.title3).foregroundStyle(Theme.brandGradient).frame(width: 30)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline)
                Text(text).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}
