// SPDX-License-Identifier: GPL-3.0-or-later
import PhotosUI
import SwiftUI
import Vision
import VisionKit

struct PairingView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var mode = 1
    @State private var backend = ""
    @State private var code = ""
    @State private var step: AppModel.PairStep?
    @State private var error: String?
    @State private var task: Task<Void, Never>?
    @State private var photo: PhotosPickerItem?

    private var scannerAvailable: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    var body: some View {
        NavigationStack {
            PocketScreen {
                VStack(spacing: 0) {
                    PocketTopBar("Host eşle", subtitle: "QR tara ya da kodu yaz", back: { task?.cancel(); dismiss() })
                    ScrollView {
                        VStack(alignment: .leading, spacing: PA.Space.lg) {
                            if let step {
                                progressView(step)
                            } else {
                                modePicker
                                if mode == 0 { qrSection } else { codeSection }
                            }
                            if let error { ErrorBanner(message: error) }
                            howItWorks
                        }
                        .screenPadding().padding(.bottom, PA.Space.x3)
                    }
                }
            }
            .onAppear {
                if backend.isEmpty { backend = model.lastBackend }
                if let link = model.pendingPairLink {
                    model.pendingPairLink = nil
                    start(link)
                }
            }
            .onChange(of: photo) { _, item in Task { await decodePhoto(item) } }
            .hostKeyPrompt(active: true)
        }
        .tint(PA.accent)
        .onAppear { model.pairingSheetVisible = true }
        .onDisappear { model.pairingSheetVisible = false }
    }

    private var modePicker: some View {
        HStack(spacing: PA.Space.xs) {
            modeChip("QR tara", icon: "qrcode.viewfinder", tag: 0)
            modeChip("Kod gir", icon: "number", tag: 1)
        }
        .padding(PA.Space.xs)
        .background(PA.card, in: Capsule())
    }
    private func modeChip(_ title: String, icon: String, tag: Int) -> some View {
        Button { withAnimation(.snappy) { mode = tag } } label: {
            Label(title, systemImage: icon).font(PAFont.labelLarge)
                .foregroundStyle(mode == tag ? PA.onAccent : PA.text)
                .frame(maxWidth: .infinity).frame(height: 40)
                .background(mode == tag ? PA.accent : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("pairMode-\(tag)")
    }

    private var qrSection: some View {
        VStack(spacing: PA.Space.md) {
            if scannerAvailable {
                QRScanner { text in
                    if let t = PairingLink.parse(text, fallbackBackend: backend) { start(t) }
                }
                .frame(height: 320)
                .clipShape(RoundedRectangle(cornerRadius: PA.Radius.card, style: .continuous))
            } else {
                EmptyState(icon: "camera.metering.unknown", title: "Kamera tarayıcı yok",
                           message: "QR'ın ekran görüntüsünü seç ya da kodu elle gir.")
                    .pocketCard()
            }
            PhotosPicker(selection: $photo, matching: .images) {
                Label("Fotoğraflardan QR seç", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity)
            }.buttonStyle(.outline)
            Button {
                if let s = UIPasteboard.general.string, let t = PairingLink.parse(s, fallbackBackend: backend) {
                    start(t)
                } else {
                    error = "Panoda eşleme bağlantısı ya da kodu yok."
                }
            } label: {
                Label("Panodan yapıştır", systemImage: "doc.on.clipboard").frame(maxWidth: .infinity)
            }.buttonStyle(.outline)
        }
    }

    private var codeSection: some View {
        PocketCard {
            VStack(alignment: .leading, spacing: PA.Space.md) {
                Text("Backend adresi").font(PAFont.labelMedium).foregroundStyle(PA.text2)
                PocketField(placeholder: "https://agent.ornek.com", text: $backend, mono: true, icon: "network")
                    .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .accessibilityIdentifier("backendField")
                Text("Eşleme kodu").font(PAFont.labelMedium).foregroundStyle(PA.text2).padding(.top, PA.Space.xs)
                TextField("XXXX-XXXX", text: $code)
                    .font(PAFont.mono(26, .bold)).foregroundStyle(PA.text).multilineTextAlignment(.center)
                    .textInputAutocapitalization(.characters).autocorrectionDisabled()
                    .frame(height: 64)
                    .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
                    .accessibilityIdentifier("codeField")
                    .onChange(of: code) { _, v in
                        let clean = v.uppercased().filter { $0.isLetter || $0.isNumber }.prefix(8)
                        let formatted = clean.count > 4 ? clean.prefix(4) + "-" + clean.dropFirst(4) : Substring(clean)
                        if formatted != v { code = String(formatted) }
                    }
                Button {
                    let (b, err) = PairingLink.normalizeBackend(backend)
                    if let err { error = err; return }
                    guard let c = PairingLink.normalize(code) else { error = "Kod 8 karakter olmalı (XXXX-XXXX)."; return }
                    start(.init(backend: b, code: c))
                } label: {
                    Label("Eşle", systemImage: "link").frame(maxWidth: .infinity)
                }
                .buttonStyle(.primary)
                .disabled(PairingLink.normalize(code) == nil || backend.isEmpty)
                .accessibilityIdentifier("submitPair")
            }
        }
    }

    private func progressView(_ step: AppModel.PairStep) -> some View {
        PocketCard(padding: PA.Space.xl) {
            VStack(alignment: .leading, spacing: PA.Space.lg) {
                row("Kod doğrulanıyor, public key gönderiliyor", done: step != .claiming, active: step == .claiming)
                let waiting: Bool = { if case .waitingApproval = step { return true }; return false }()
                row("Host'ta onay bekleniyor", done: step == .connecting || step == .done, active: waiting)
                if case let .waitingApproval(fp) = step {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Host terminalinde bu parmak izini karşılaştır ve onayla:").font(PAFont.bodySmall).foregroundStyle(PA.text2)
                        Text(fp).font(PAFont.mono(12.5)).foregroundStyle(PA.text).textSelection(.enabled)
                            .padding(PA.Space.md).frame(maxWidth: .infinity, alignment: .leading)
                            .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.key, style: .continuous))
                            .accessibilityIdentifier("pairFingerprint")
                    }
                }
                row("SSH bağlantısı kuruluyor", done: step == .done, active: step == .connecting)
                if step == .done {
                    StatusChip(text: "Eşleşti", tone: .live).accessibilityIdentifier("pairDone")
                }
            }
        }
    }

    private func row(_ text: String, done: Bool, active: Bool) -> some View {
        HStack(spacing: PA.Space.md) {
            ZStack {
                if done {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(PA.live)
                } else if active {
                    ProgressView().tint(PA.accent)
                } else {
                    StatusDot(color: PA.text2, size: 10, hollow: true)
                }
            }.frame(width: 24)
            Text(text).font(PAFont.bodyMedium).foregroundStyle(done || active ? PA.text : PA.text2)
        }
    }

    private var howItWorks: some View {
        PocketCard {
            DisclosureGroup {
                VStack(alignment: .leading, spacing: PA.Space.sm) {
                    Text("1. Host'ta `pocket-agent pair` çalıştır; QR ve XXXX-XXXX kodu çıkar (5 dk geçerli).")
                    Text("2. Telefon kendi Ed25519 anahtarını Keychain'de üretir ve backend'e yalnız public key'i gönderir.")
                    Text("3. Host cihaz adını ve anahtar parmak izini gösterir; onaylarsan authorized_keys'e eklenir.")
                    Text("4. Uygulama bu cihaza özgü backend token'ını alır; özel anahtar hiçbir yere gitmez.")
                }.font(PAFont.bodySmall).foregroundStyle(PA.text2).padding(.top, PA.Space.sm)
            } label: {
                HStack(spacing: PA.Space.md) {
                    IconTile(icon: "questionmark", tone: .neutral, size: 32)
                    Text("Nasıl çalışır?").font(PAFont.titleMedium).foregroundStyle(PA.text)
                }
            }
            .tint(PA.text2)
        }
    }

    private func start(_ target: PairingLink.Target) {
        guard step == nil else { return }
        error = nil
        backend = target.backend
        code = target.code
        task = Task {
            do {
                _ = try await model.pair(backend: target.backend, code: target.code) { s in
                    Task { @MainActor in withAnimation { step = s } }
                }
                try? await Task.sleep(nanoseconds: 900_000_000)
                dismiss()
            } catch is CancellationError {
            } catch {
                withAnimation {
                    self.error = error.localizedDescription
                    step = nil
                }
            }
        }
    }

    private func decodePhoto(_ item: PhotosPickerItem?) async {
        guard let item, let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data)?.cgImage else { return }
        let req = VNDetectBarcodesRequest()
        req.symbologies = [.qr]
        try? VNImageRequestHandler(cgImage: image).perform([req])
        let text = req.results?.compactMap(\.payloadStringValue).first
        if let text, let t = PairingLink.parse(text, fallbackBackend: backend) {
            start(t)
        } else {
            error = "Görselde eşleme QR'ı bulunamadı."
        }
    }
}

struct QRScanner: UIViewControllerRepresentable {
    var onCode: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])],
                                           qualityLevel: .balanced, isHighlightingEnabled: true)
        vc.delegate = context.coordinator
        try? vc.startScanning()
        return vc
    }

    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onCode: (String) -> Void
        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }
        func dataScanner(_ s: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            for case let .barcode(b) in items {
                if let v = b.payloadStringValue { onCode(v); return }
            }
        }
    }
}

struct HostKeyPromptView: View {
    let prompt: AppModel.HostKeyPrompt
    var body: some View {
        ZStack {
            PA.bg.ignoresSafeArea()
            VStack(spacing: PA.Space.xl) {
                IconTile(icon: "lock.shield.fill", tone: .warn, size: 64)
                Text("Yeni host anahtarı").font(PAFont.titleLarge).foregroundStyle(PA.text)
                Text("\(prompt.host.address) ilk kez görülüyor. Parmak izini host'ta `ssh-keygen -lf /etc/ssh/ssh_host_\(prompt.keyType.replacingOccurrences(of: "ssh-", with: ""))_key.pub` ile karşılaştır.")
                    .font(PAFont.bodyMedium).foregroundStyle(PA.text2).multilineTextAlignment(.center)
                VStack(alignment: .leading, spacing: 6) {
                    Tag(text: prompt.keyType, tone: .neutral)
                    Text(prompt.fingerprint).font(PAFont.mono(13)).foregroundStyle(PA.text).textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("hostKeyFingerprint")
                }
                .padding(PA.Space.lg).frame(maxWidth: .infinity, alignment: .leading)
                .background(PA.card, in: RoundedRectangle(cornerRadius: PA.Radius.card, style: .continuous))
                HStack(spacing: PA.Space.md) {
                    Button { prompt.respond(false) } label: { Text("Vazgeç").frame(maxWidth: .infinity) }
                        .buttonStyle(.outline)
                    Button { prompt.respond(true) } label: { Text("Güven ve bağlan").frame(maxWidth: .infinity) }
                        .buttonStyle(.primary)
                        .accessibilityIdentifier("trustHostKey")
                }
            }
            .padding(PA.Space.xxl)
        }
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(PA.Radius.sheet)
    }
}
extension View {
    /// Presents the TOFU host-key prompt from this view when `active`.
    func hostKeyPrompt(active: Bool) -> some View { modifier(HostKeyPromptModifier(active: active)) }
}

struct HostKeyPromptModifier: ViewModifier {
    @EnvironmentObject var model: AppModel
    let active: Bool
    func body(content: Content) -> some View {
        content.sheet(item: Binding(get: { active ? model.hostKeyPrompt : nil }, set: { _ in })) { prompt in
            HostKeyPromptView(prompt: prompt).interactiveDismissDisabled()
        }
    }
}
