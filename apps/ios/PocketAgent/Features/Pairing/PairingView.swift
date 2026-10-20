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
            ScrollView {
                VStack(spacing: 20) {
                    if let step {
                        progressView(step)
                    } else {
                        Picker("Yöntem", selection: $mode) {
                            Text("QR tara").tag(0)
                            Text("Kod gir").tag(1)
                        }
                        .pickerStyle(.segmented)
                        if mode == 0 { qrSection } else { codeSection }
                    }
                    if let error {
                        ErrorBanner(message: error, retry: step == nil ? nil : nil)
                    }
                    howItWorks
                }
                .padding(20)
            }
            .navigationTitle("Host eşle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") { task?.cancel(); dismiss() }
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
        .onAppear { model.pairingSheetVisible = true }
        .onDisappear { model.pairingSheetVisible = false }
    }

    private var qrSection: some View {
        VStack(spacing: 14) {
            if scannerAvailable {
                QRScanner { text in
                    if let t = PairingLink.parse(text, fallbackBackend: backend) { start(t) }
                }
                .frame(height: 320)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            } else {
                Card {
                    VStack(spacing: 10) {
                        Image(systemName: "camera.metering.unknown").font(.largeTitle).foregroundStyle(.secondary)
                        Text("Bu cihazda kamera tarayıcı yok.").font(.headline)
                        Text("QR'ın ekran görüntüsünü seç ya da kodu elle gir.").font(.subheadline).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity)
                }
            }
            PhotosPicker(selection: $photo, matching: .images) {
                Label("Fotoğraflardan QR seç", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity)
            }.buttonStyle(.bordered)
            Button {
                if let s = UIPasteboard.general.string, let t = PairingLink.parse(s, fallbackBackend: backend) {
                    start(t)
                } else {
                    error = "Panoda eşleme bağlantısı ya da kodu yok."
                }
            } label: {
                Label("Panodan yapıştır", systemImage: "doc.on.clipboard").frame(maxWidth: .infinity)
            }.buttonStyle(.bordered)
        }
    }

    private var codeSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Backend adresi").font(.subheadline.weight(.semibold))
            TextField("https://agent.ornek.com", text: $backend)
                .textFieldStyle(.roundedBorder).keyboardType(.URL)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .font(.body.monospaced())
                .accessibilityIdentifier("backendField")
            Text("Eşleme kodu").font(.subheadline.weight(.semibold))
            TextField("XXXX-XXXX", text: $code)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.characters).autocorrectionDisabled()
                .font(.title2.monospaced().weight(.semibold)).multilineTextAlignment(.center)
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
                Label("Eşle", systemImage: "link").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 4)
            }
            .buttonStyle(.borderedProminent).controlSize(.large)
            .disabled(PairingLink.normalize(code) == nil || backend.isEmpty)
            .accessibilityIdentifier("submitPair")
        }
    }

    private func progressView(_ step: AppModel.PairStep) -> some View {
        Card(padding: 22) {
            VStack(alignment: .leading, spacing: 18) {
                row("Kod doğrulanıyor, public key gönderiliyor", done: step != .claiming, active: step == .claiming)
                let waiting: Bool = { if case .waitingApproval = step { return true }; return false }()
                row("Host'ta onay bekleniyor", done: step == .connecting || step == .done, active: waiting)
                if case let .waitingApproval(fp) = step {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Host terminalinde bu parmak izini karşılaştır ve onayla:").font(.footnote).foregroundStyle(.secondary)
                        Text(fp).font(.footnote.monospaced()).textSelection(.enabled)
                            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                            .background(.background.tertiary, in: RoundedRectangle(cornerRadius: 10))
                            .accessibilityIdentifier("pairFingerprint")
                    }
                }
                row("SSH bağlantısı kuruluyor", done: step == .done, active: step == .connecting)
                if step == .done {
                    Label("Eşleşti", systemImage: "checkmark.circle.fill").font(.title3.bold()).foregroundStyle(.green)
                        .accessibilityIdentifier("pairDone")
                }
            }
        }
    }

    private func row(_ text: String, done: Bool, active: Bool) -> some View {
        HStack(spacing: 12) {
            ZStack {
                if done {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                } else if active {
                    ProgressView()
                } else {
                    Image(systemName: "circle").foregroundStyle(.tertiary)
                }
            }.frame(width: 24)
            Text(text).foregroundStyle(done || active ? .primary : .secondary)
        }
    }

    private var howItWorks: some View {
        DisclosureGroup("Nasıl çalışır?") {
            VStack(alignment: .leading, spacing: 8) {
                Text("1. Host'ta `pocket-agent pair` çalıştır; QR ve XXXX-XXXX kodu çıkar (5 dk geçerli).")
                Text("2. Telefon kendi Ed25519 anahtarını Keychain'de üretir ve backend'e yalnız public key'i gönderir.")
                Text("3. Host cihaz adını ve anahtar parmak izini gösterir; onaylarsan authorized_keys'e eklenir.")
                Text("4. Uygulama bu cihaza özgü backend token'ını alır; özel anahtar hiçbir yere gitmez.")
            }.font(.footnote).foregroundStyle(.secondary).padding(.top, 6)
        }
        .padding(.top, 8)
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
        VStack(spacing: 20) {
            Image(systemName: "lock.shield.fill").font(.system(size: 54)).foregroundStyle(.orange)
            Text("Yeni host anahtarı").font(.title2.bold())
            Text("\(prompt.host.address) ilk kez görülüyor. Parmak izini host'ta `ssh-keygen -lf /etc/ssh/ssh_host_\(prompt.keyType.replacingOccurrences(of: "ssh-", with: ""))_key.pub` ile karşılaştır.")
                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            VStack(alignment: .leading, spacing: 6) {
                Text(prompt.keyType).font(.caption.bold()).foregroundStyle(.secondary)
                Text(prompt.fingerprint).font(.callout.monospaced()).textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("hostKeyFingerprint")
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14))
            HStack(spacing: 12) {
                Button(role: .cancel) { prompt.respond(false) } label: {
                    Text("Vazgeç").frame(maxWidth: .infinity)
                }.buttonStyle(.bordered).controlSize(.large)
                Button { prompt.respond(true) } label: {
                    Text("Güven ve bağlan").frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).controlSize(.large)
                .accessibilityIdentifier("trustHostKey")
            }
        }
        .padding(24)
        .presentationDetents([.medium, .large])
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
