// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct HostsView: View {
    @EnvironmentObject var model: AppModel
    @Binding var showPair: Bool
    @State private var addManual = false

    var body: some View {
        NavigationStack {
            List {
                if !model.openTerminals.isEmpty {
                    Section("Açık terminaller") {
                        ForEach(model.openTerminals) { t in
                            NavigationLink(value: Route.terminal(t.id)) {
                                OpenTerminalRow(session: t)
                            }
                            .swipeActions { Button("Kapat", role: .destructive) { model.closeTerminal(t) } }
                        }
                    }
                }
                Section("Host'lar") {
                    ForEach(model.hosts) { host in
                        NavigationLink(value: Route.host(host.id)) { HostRow(host: host) }
                            .accessibilityIdentifier("host-\(host.name)")
                    }
                    .onDelete { idx in idx.map { model.hosts[$0] }.forEach(model.forget) }
                    if model.hosts.isEmpty {
                        Text("Henüz host yok. Sağ üstten eşle.").foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Oturumlar")
            .navigationDestination(for: Route.self) { route in
                switch route {
                case let .host(id):
                    if let h = model.hosts.first(where: { $0.id == id }) { HostDetailView(host: h) }
                case let .terminal(id):
                    if let t = model.openTerminals.first(where: { $0.id == id }) { TerminalScreen(session: t) }
                case let .files(id):
                    if let h = model.hosts.first(where: { $0.id == id }) { FilesView(host: h) }
                }
            }
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button { showPair = true } label: { Label("Kodla eşle", systemImage: "qrcode.viewfinder") }
                        Button { addManual = true } label: { Label("Elle ekle", systemImage: "plus.rectangle.on.rectangle") }
                    } label: { Image(systemName: "plus.circle.fill").font(.title3) }
                    .accessibilityIdentifier("addHost")
                }
            }
            .sheet(isPresented: $addManual) { ManualHostView() }
        }
    }
}

enum Route: Hashable {
    case host(UUID)
    case terminal(UUID)
    case files(UUID)
}

struct HostRow: View {
    @EnvironmentObject var model: AppModel
    let host: Host
    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Theme.accent(host.accent).gradient)
                Image(systemName: "server.rack").foregroundStyle(.white)
            }.frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 3) {
                Text(host.name).font(.headline)
                Text(host.address).font(.caption.monospaced()).foregroundStyle(.secondary)
            }
            Spacer()
            statusView
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder private var statusView: some View {
        switch model.connectionStatus[host.id] {
        case .connected: StatusDot(color: .green)
        case .connecting: ProgressView().controlSize(.small)
        case .failed: Image(systemName: "exclamationmark.circle.fill").foregroundStyle(.orange)
        case nil:
            if let d = host.lastConnected { Text(d.relative).font(.caption2).foregroundStyle(.tertiary) }
        }
    }
}

struct OpenTerminalRow: View {
    @ObservedObject var session: TerminalSession
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: session.tmuxName == nil ? "apple.terminal" : "rectangle.stack")
                .foregroundStyle(Theme.accent(session.host.accent))
            VStack(alignment: .leading) {
                Text(session.title).font(.subheadline.weight(.semibold))
                Text(session.host.name).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            switch session.state {
            case .connected: StatusDot(color: .green, pulsing: true)
            case .connecting: ProgressView().controlSize(.small)
            case .closed: Text("Kapalı").font(.caption).foregroundStyle(.secondary)
            case .failed: Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            }
        }
    }
}

struct HostDetailView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let host: Host
    @State private var sessions: [TmuxSession] = []
    @State private var state: AppModel.LoadState = .idle
    @State private var hasTmux = true
    @State private var newName = ""
    @State private var askName = false
    @State private var confirmForget = false
    @State private var path = [Route]()
    @State private var openError: String?

    var body: some View {
        List {
            Section {
                HStack(spacing: 16) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.accent(host.accent).gradient)
                        Image(systemName: "server.rack").font(.title2).foregroundStyle(.white)
                    }.frame(width: 58, height: 58)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(host.name).font(.title3.bold())
                        Text(host.address).font(.footnote.monospaced()).foregroundStyle(.secondary)
                        connectionLabel
                    }
                }
                .padding(.vertical, 6)
                HStack(spacing: 10) {
                    quickAction("Shell", "apple.terminal") { await open(nil) }
                    NavigationLink(value: Route.files(host.id)) {
                        VStack(spacing: 6) {
                            Image(systemName: "folder.fill").font(.title3)
                            Text("Dosyalar").font(.caption.weight(.semibold))
                        }.frame(maxWidth: .infinity).padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("openFiles")
                    quickAction("Yeni tmux", "plus.rectangle.on.rectangle") { askName = true }
                }
                .buttonStyle(.plain)
            }
            if let openError { Section { ErrorBanner(message: openError) } }
            Section {
                switch state {
                case .idle, .loading:
                    HStack { ProgressView(); Text("tmux oturumları aranıyor…").foregroundStyle(.secondary) }
                case let .failed(msg):
                    ErrorBanner(message: msg) { Task { await load() } }
                case .loaded:
                    if !hasTmux {
                        Label("Host'ta tmux yok. Kalıcı oturumlar için tmux kur.", systemImage: "info.circle")
                            .foregroundStyle(.secondary)
                    } else if sessions.isEmpty {
                        Text("Açık tmux oturumu yok. \"Yeni tmux\" ile başlat.").foregroundStyle(.secondary)
                    }
                    ForEach(sessions) { s in
                        Button { Task { await open(s.name) } } label: { TmuxRow(session: s) }
                            .accessibilityIdentifier("tmux-\(s.name)")
                            .swipeActions {
                                Button("Sonlandır", role: .destructive) { Task { await kill(s.name) } }
                            }
                    }
                }
            } header: {
                HStack {
                    Text("tmux oturumları")
                    Spacer()
                    Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityIdentifier("refreshTmux")
                }
            }
            Section("Güvenlik") {
                LabeledContent("Host anahtarı") {
                    Text(host.pinnedHostKeys.first ?? "İlk bağlantıda sorulur")
                        .font(.caption.monospaced()).lineLimit(1).truncationMode(.middle)
                }
                if let b = host.backendURL { LabeledContent("Backend", value: URL(string: b)?.host ?? b) }
                LabeledContent("Eşlendi", value: host.pairedAt.formatted(date: .abbreviated, time: .shortened))
                if case let .failed(msg) = model.connectionStatus[host.id], msg.contains("anahtar") {
                    Button("Host anahtarını sıfırla ve yeniden doğrula", role: .destructive) { model.resetHostKey(host) }
                }
                Button("Host'u unut", role: .destructive) { confirmForget = true }
            }
        }
        .navigationTitle(host.name)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
        .alert("Yeni tmux oturumu", isPresented: $askName) {
            TextField("ad", text: $newName).textInputAutocapitalization(.never).autocorrectionDisabled()
            Button("Oluştur") {
                let n = newName.trimmingCharacters(in: .whitespaces)
                newName = ""
                Task { await open(n.isEmpty ? "pa-\(Int.random(in: 100...999))" : n) }
            }
            Button("Vazgeç", role: .cancel) {}
        }
        .confirmationDialog("\(host.name) unutulsun mu?", isPresented: $confirmForget, titleVisibility: .visible) {
            Button("Unut", role: .destructive) { model.forget(host); dismiss() }
        } message: {
            Text("Host'taki yetkiyi kaldırmak için orada `pocket-agent unpair` çalıştır.")
        }
        .navigationDestination(isPresented: Binding(get: { activeTerminal != nil }, set: { if !$0 { activeTerminal = nil } })) {
            if let t = activeTerminal { TerminalScreen(session: t) }
        }
    }

    @State private var activeTerminal: TerminalSession?

    @ViewBuilder private var connectionLabel: some View {
        switch model.connectionStatus[host.id] {
        case .connected: Label("Bağlı", systemImage: "circle.fill").font(.caption).foregroundStyle(.green)
        case .connecting: Label("Bağlanıyor", systemImage: "circle.dotted").font(.caption).foregroundStyle(.secondary)
        case .failed: Label("Bağlantı hatası", systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(.orange)
        case nil: Label("Bağlı değil", systemImage: "circle").font(.caption).foregroundStyle(.secondary)
        }
    }

    private func quickAction(_ title: String, _ icon: String, _ action: @escaping () async -> Void) -> some View {
        Button { Task { await action() } } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.title3)
                Text(title).font(.caption.weight(.semibold))
            }.frame(maxWidth: .infinity).padding(.vertical, 10)
        }
        .buttonStyle(.bordered)
        .accessibilityIdentifier("action-\(title)")
    }

    private func load() async {
        if sessions.isEmpty { state = .loading }
        do {
            let c = try await model.connection(for: host)
            hasTmux = await c.hasTmux()
            sessions = hasTmux ? try await c.listTmux() : []
            state = .loaded
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    private func open(_ tmux: String?) async {
        openError = nil
        do {
            activeTerminal = try await model.openTerminal(host: host, tmux: tmux)
        } catch {
            openError = error.localizedDescription
        }
    }

    private func kill(_ name: String) async {
        do {
            try await model.connection(for: host).killTmux(name)
            model.openTerminals.filter { $0.host.id == host.id && $0.tmuxName == name }.forEach(model.closeTerminal)
            await load()
        } catch {
            openError = error.localizedDescription
        }
    }
}

struct TmuxRow: View {
    let session: TmuxSession
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "rectangle.stack.fill").foregroundStyle(.tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(session.name).font(.body.monospaced().weight(.medium)).foregroundStyle(.primary)
                HStack(spacing: 6) {
                    Text("\(session.windows) pencere")
                    if let a = session.activity { Text("· \(a.relative)") }
                }.font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if session.attached {
                Text("bağlı").font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 3)
                    .background(.green.opacity(0.18), in: Capsule()).foregroundStyle(.green)
            }
            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }
    }
}

struct ManualHostView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var hostname = ""
    @State private var port = "22"
    @State private var user = ""
    @State private var copied = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Bağlantı") {
                    TextField("Ad (isteğe bağlı)", text: $name)
                    TextField("Adres (ör. 10.0.0.2)", text: $hostname).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                    TextField("Port", text: $port).keyboardType(.numberPad)
                    TextField("Kullanıcı", text: $user).textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                Section {
                    Text(model.identity?.publicKeyOpenSSH ?? "").font(.caption.monospaced()).textSelection(.enabled)
                    Button(copied ? "Kopyalandı" : "Public key'i kopyala") {
                        UIPasteboard.general.string = model.identity?.publicKeyOpenSSH
                        copied = true
                    }
                } header: { Text("Bu cihazın public key'i") } footer: {
                    Text("Elle eklenen host'ta bu satırı ~/.ssh/authorized_keys'e ekle. Kodla eşleme bunu otomatik yapar.")
                }
            }
            .navigationTitle("Elle ekle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        model.upsert(Host(name: name.isEmpty ? hostname : name, hostname: hostname,
                                          port: Int(port) ?? 22, user: user, accent: model.hosts.count))
                        dismiss()
                    }.disabled(hostname.isEmpty || user.isEmpty)
                }
            }
        }
    }
}
