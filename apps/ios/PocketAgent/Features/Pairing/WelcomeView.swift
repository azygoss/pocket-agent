// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

/// First-launch setup guide; mirrors Android's "Cebindeki terminal" SetupGuide.
struct WelcomeView: View {
    @Binding var showPair: Bool
    @EnvironmentObject var model: AppModel
    @State private var addManual = false

    var body: some View {
        PocketScreen {
            ScrollView {
                VStack(alignment: .leading, spacing: PA.Space.lg) {
                    HStack(spacing: PA.Space.md) {
                        BrandMark(size: 30)
                        Text("Pocket Agent").font(PAFont.titleLarge).foregroundStyle(PA.text)
                        Spacer()
                    }
                    .frame(minHeight: 64)

                    VStack(alignment: .leading, spacing: PA.Space.sm) {
                        Text("Cebindeki terminal").font(PAFont.headline).foregroundStyle(PA.text)
                        Text("Sunucundaki tmux oturumları, dosyalar ve kodlama ajanların. SSH anahtarın telefonda üretilir, hiçbir yere gönderilmez.")
                            .font(PAFont.body).foregroundStyle(PA.text2)
                    }
                    .padding(.top, PA.Space.sm)

                    StepCard(step: "1", title: "Host'ta çalıştır",
                             text: "Sunucunda tek komutla kurulumu başlat. Komut sana bir QR ve kod verir.") {
                        CodeLine(code: "npm i -g pocket-agent-cli && pocket-agent onboard")
                    }
                    StepCard(step: "2", title: "Telefonu eşle",
                             text: "QR'ı tara ya da kodu yaz. Host, bu cihazın public key'ini onaylayıp yetkilendirir.") {
                        Button { showPair = true } label: {
                            Label("QR veya kodla eşle", systemImage: "qrcode.viewfinder").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.primary)
                        .accessibilityIdentifier("pairButton")
                    }
                    StepCard(step: "⋯", title: "Ya da elle ekle",
                             text: "Mevcut bir SSH sunucusunu adres ve kullanıcıyla ekle; public key'i authorized_keys'e kendin yapıştır.") {
                        Button { addManual = true } label: { Text("Host ekle").frame(maxWidth: .infinity) }
                            .buttonStyle(.outline)
                    }

                    HStack(spacing: PA.Space.sm) {
                        Image(systemName: "lock.shield").foregroundStyle(PA.accent)
                        Text("Özel anahtar Keychain'de kalır; backend yalnızca olayları ve onayları taşır.")
                            .font(PAFont.bodySmall).foregroundStyle(PA.text2)
                    }
                    .padding(.horizontal, PA.Space.xs)
                }
                .screenPadding()
                .padding(.bottom, PA.Space.x3)
            }
        }
        .sheet(isPresented: $addManual) { ManualHostView() }
    }
}

struct StepCard<Action: View>: View {
    var step: String
    var title: String
    var text: String
    @ViewBuilder var action: Action
    var body: some View {
        PocketCard {
            VStack(alignment: .leading, spacing: PA.Space.md) {
                HStack(spacing: PA.Space.md) {
                    Text(step).font(PAFont.mono(14, .bold)).foregroundStyle(PA.accent)
                        .frame(width: 32, height: 32)
                        .background(Tone.accent.soft, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    Text(title).font(PAFont.titleMedium).foregroundStyle(PA.text)
                }
                Text(text).font(PAFont.bodyMedium).foregroundStyle(PA.text2)
                action
            }
        }
    }
}
