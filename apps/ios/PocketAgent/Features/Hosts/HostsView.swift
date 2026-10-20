// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

/// Hub: open sessions, hosts and agent activity (Android HubScreen).
struct HostsView: View {
    @EnvironmentObject var model: AppModel
    @Binding var showPair: Bool
    @State private var addManual = false

    var body: some View {
        NavigationStack {
            PocketScreen {
                ScrollView {
                    VStack(alignment: .leading, spacing: PA.Space.lg) {
                        header
                        if model.pendingApprovals > 0 {
                            Banner(message: "\(model.pendingApprovals) ajan isteği onay bekliyor. Gelen kutusuna bak.",
                                   tone: .warn, icon: "hand.raised.fill")
                        }
                        if !model.openTerminals.isEmpty { openSessions }
                        hosts
                    }
                    .screenPadding()
                    .padding(.bottom, PA.Space.x3)
                }
                .refreshable { await model.refreshInbox() }
            }
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
            .sheet(isPresented: $addManual) { ManualHostView() }
        }
        .tint(PA.accent)
    }

    private var header: some View {
        HStack(spacing: PA.Space.md) {
            BrandMark(size: 30)
            Text("Pocket Agent").font(PAFont.titleLarge).foregroundStyle(PA.text)
            Spacer()
            Menu {
                Button { showPair = true } label: { Label("QR veya kodla eşle", systemImage: "qrcode.viewfinder") }
                Button { addManual = true } label: { Label("Host'u elle ekle", systemImage: "server.rack") }
            } label: {
                Image(systemName: "plus").font(.system(size: 17, weight: .semibold)).foregroundStyle(PA.text)
                    .frame(width: 40, height: 40).background(PA.card, in: Circle())
            }
            .accessibilityIdentifier("addHost")
            .accessibilityLabel("Ekle")
        }
        .frame(minHeight: 64)
    }

    private var openSessions: some View {
        VStack(spacing: PA.Space.xs) {
            SectionHeader("Açık oturumlar", count: model.openTerminals.count)
            VStack(spacing: 0) {
                ForEach(Array(model.openTerminals.enumerated()), id: \.element.id) { i, t in
                    NavigationLink(value: Route.terminal(t.id)) { OpenTerminalRow(session: t) }
                        .buttonStyle(.plain)
                        .contextMenu { Button("Oturumu kapat", role: .destructive) { model.closeTerminal(t) } }
                    if i < model.openTerminals.count - 1 { InsetDivider() }
                }
            }
            .pocketCard()
        }
    }

    private var hosts: some View {
        VStack(spacing: PA.Space.xs) {
            SectionHeader("Host'lar", count: model.hosts.count) {
                Button("Eşle") { showPair = true }.buttonStyle(.ghost)
            }
            if model.hosts.isEmpty {
                EmptyState(icon: "server.rack", title: "Henüz host yok",
                           message: "Host'ta `pocket-agent pair` çalıştır, sonra QR'ı tara ya da kodu yaz.",
                           actionTitle: "QR veya kodla eşle") { showPair = true }
                    .pocketCard()
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(model.hosts.enumerated()), id: \.element.id) { i, host in
                        NavigationLink(value: Route.host(host.id)) { HostRow(host: host) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("host-\(host.name)")
                            .contextMenu { Button("Host'u unut", role: .destructive) { model.forget(host) } }
                        if i < model.hosts.count - 1 { InsetDivider() }
                    }
                }
                .pocketCard()
            }
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
        ListRow(host.name, subtitle: host.address, subtitleMono: true) {
            HostAvatar(name: host.name, color: Theme.accent(host.accent).opacity(0.22))
        } trailing: {
            HStack(spacing: PA.Space.sm) {
                statusView
                Chevron()
            }
        }
    }
    @ViewBuilder private var statusView: some View {
        switch model.connectionStatus[host.id] {
        case .connected: StatusDot(color: PA.live)
        case .connecting: StatusDot(color: PA.busy, pulsing: true)
        case .failed: StatusDot(color: PA.danger)
        case nil:
            if let d = host.lastConnected { Text(d.relative).font(PAFont.labelSmall).foregroundStyle(PA.text2) }
            else { StatusDot(color: PA.text2, hollow: true) }
        }
    }
}

struct OpenTerminalRow: View {
    @ObservedObject var session: TerminalSession
    var body: some View {
        ListRow(session.title, subtitle: session.host.address, subtitleMono: true) {
            IconTile(icon: session.tmuxName == nil ? "terminal" : "rectangle.stack", tone: toneFor(session.state), size: 40)
        } trailing: {
            HStack(spacing: PA.Space.sm) {
                switch session.state {
                case .connected: StatusChip(text: "Canlı", tone: .live, pulsing: true)
                case .connecting: StatusChip(text: "Bağlanıyor", tone: .busy, pulsing: true)
                case .closed: StatusChip(text: "Kapandı", tone: .neutral)
                case .failed: StatusChip(text: "Hata", tone: .danger)
                }
                Chevron()
            }
        }
    }
    private func toneFor(_ s: TerminalSession.State) -> Tone {
        switch s {
        case .connected: .live
        case .connecting: .busy
        case .closed: .neutral
        case .failed: .danger
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
    @State private var openError: String?
    @State private var activeTerminal: TerminalSession?

    var body: some View {
        PocketScreen {
            VStack(spacing: 0) {
                PocketTopBar(host.name, subtitle: host.address, back: { dismiss() }) {
                    IconAction(icon: "arrow.clockwise", label: "Yenile") { Task { await load() } }
                        .accessibilityIdentifier("refreshTmux")
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: PA.Space.lg) {
                        hero
                        if let openError { ErrorBanner(message: openError) }
                        tmuxSection
                        securitySection
                    }
                    .screenPadding()
                    .padding(.bottom, PA.Space.x3)
                }
                .refreshable { await load() }
            }
        }
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

    private var hero: some View {
        PocketCard {
            VStack(alignment: .leading, spacing: PA.Space.lg) {
                HStack(spacing: PA.Space.lg) {
                    HostAvatar(name: host.name, size: 58, color: Theme.accent(host.accent).opacity(0.22))
                    VStack(alignment: .leading, spacing: 6) {
                        Text(host.name).font(PAFont.titleLarge).foregroundStyle(PA.text)
                        Text(host.address).font(PAFont.mono(12.5)).foregroundStyle(PA.text2)
                        connectionChip
                    }
                    Spacer()
                }
                HStack(spacing: PA.Space.sm) {
                    quickAction("Shell", "terminal") { await open(nil) }
                    NavigationLink(value: Route.files(host.id)) { quickLabel("Dosyalar", "folder") }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("openFiles")
                    quickAction("Yeni tmux", "plus.rectangle.on.rectangle") { askName = true }
                }
            }
        }
    }

    @ViewBuilder private var connectionChip: some View {
        switch model.connectionStatus[host.id] {
        case .connected: StatusChip(text: "Bağlı", tone: .live)
        case .connecting: StatusChip(text: "Bağlanıyor", tone: .busy, pulsing: true)
        case .failed: StatusChip(text: "Bağlantı hatası", tone: .danger)
        case nil: StatusChip(text: "Bağlı değil", tone: .neutral)
        }
    }

    private func quickLabel(_ title: String, _ icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 18, weight: .medium)).foregroundStyle(PA.accent)
            Text(title).font(PAFont.labelMedium).foregroundStyle(PA.text)
        }
        .frame(maxWidth: .infinity).frame(height: 64)
        .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
        .contentShape(Rectangle())
    }

    private func quickAction(_ title: String, _ icon: String, _ action: @escaping () async -> Void) -> some View {
        Button { Task { await action() } } label: { quickLabel(title, icon) }
            .buttonStyle(.plain)
            .accessibilityIdentifier("action-\(title)")
    }

    private var tmuxSection: some View {
        VStack(spacing: PA.Space.xs) {
            SectionHeader("tmux oturumları", count: sessions.count) {
                Button("Yeni") { askName = true }.buttonStyle(.ghost)
            }
            VStack(spacing: 0) {
                switch state {
                case .idle, .loading:
                    HStack(spacing: PA.Space.md) {
                        ProgressView().tint(PA.accent)
                        Text("tmux oturumları aranıyor…").font(PAFont.bodyMedium).foregroundStyle(PA.text2)
                    }.padding(PA.Space.lg)
                case let .failed(msg):
                    ErrorBanner(message: msg) { Task { await load() } }.padding(PA.Space.sm)
                case .loaded:
                    if !hasTmux {
                        Banner(message: "Host'ta tmux yok. Kalıcı oturumlar için tmux kur.", tone: .neutral, icon: "info.circle")
                            .padding(PA.Space.sm)
                    } else if sessions.isEmpty {
                        EmptyState(icon: "rectangle.stack", title: "Açık tmux oturumu yok",
                                   message: "Yeni bir oturum başlat; bağlantı kopsa da iş sunucuda sürer.",
                                   actionTitle: "Yeni tmux") { askName = true }
                    }
                    ForEach(Array(sessions.enumerated()), id: \.element.id) { i, s in
                        Button { Task { await open(s.name) } } label: { TmuxRow(session: s) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("tmux-\(s.name)")
                            .contextMenu { Button("Sonlandır", role: .destructive) { Task { await kill(s.name) } } }
                        if i < sessions.count - 1 { InsetDivider() }
                    }
                }
            }
            .pocketCard()
        }
    }

    private var securitySection: some View {
        VStack(spacing: PA.Space.xs) {
            SectionHeader("Güvenlik")
            PocketCard {
                VStack(alignment: .leading, spacing: PA.Space.md) {
                    ValueBox(label: "Host anahtarı", value: host.pinnedHostKeys.first ?? "İlk bağlantıda sorulur")
                    HStack(spacing: PA.Space.md) {
                        if let b = host.backendURL { ValueBox(label: "Backend", value: URL(string: b)?.host ?? b) }
                        ValueBox(label: "Eşlendi", value: host.pairedAt.formatted(date: .abbreviated, time: .shortened), mono: false)
                    }
                    if case let .failed(msg) = model.connectionStatus[host.id], msg.contains("anahtar") {
                        Button("Host anahtarını sıfırla ve yeniden doğrula") { model.resetHostKey(host) }
                            .buttonStyle(TonalButtonStyle(tone: .warn))
                    }
                    Button("Host'u unut") { confirmForget = true }.buttonStyle(TonalButtonStyle(tone: .danger))
                }
            }
        }
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
        do { activeTerminal = try await model.openTerminal(host: host, tmux: tmux) }
        catch { openError = error.localizedDescription }
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
        var sub = "\(session.windows) pencere"
        if let a = session.activity { sub += " · \(a.relative)" }
        return ListRow(session.name, subtitle: sub, titleMono: true) {
            IconTile(icon: "rectangle.stack.fill", tone: session.attached ? .live : .neutral, size: 38)
        } trailing: {
            HStack(spacing: PA.Space.sm) {
                if session.attached { Tag(text: "bağlı", tone: .live) }
                Chevron()
            }
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
            PocketScreen {
                VStack(spacing: 0) {
                    PocketTopBar("Host'u elle ekle", back: { dismiss() }) {
                        Button("Kaydet") {
                            model.upsert(Host(name: name.isEmpty ? hostname : name, hostname: hostname,
                                              port: Int(port) ?? 22, user: user, accent: model.hosts.count))
                            dismiss()
                        }
                        .buttonStyle(.tonal)
                        .disabled(hostname.isEmpty || user.isEmpty)
                    }
                    ScrollView {
                        VStack(alignment: .leading, spacing: PA.Space.lg) {
                            SectionHeader("Bağlantı")
                            PocketCard {
                                VStack(spacing: PA.Space.sm) {
                                    PocketField(placeholder: "Ad (isteğe bağlı)", text: $name, icon: "tag")
                                    PocketField(placeholder: "Adres (ör. 10.0.0.2)", text: $hostname, mono: true, icon: "network")
                                        .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                                    HStack(spacing: PA.Space.sm) {
                                        PocketField(placeholder: "Kullanıcı", text: $user, mono: true, icon: "person")
                                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                                        PocketField(placeholder: "Port", text: $port, mono: true, icon: "number")
                                            .keyboardType(.numberPad).frame(width: 120)
                                    }
                                }
                            }
                            SectionHeader("Bu cihazın public key'i")
                            PocketCard {
                                VStack(alignment: .leading, spacing: PA.Space.md) {
                                    Text(model.identity?.publicKeyOpenSSH ?? "")
                                        .font(PAFont.mono(12)).foregroundStyle(PA.text).textSelection(.enabled)
                                    Button(copied ? "Kopyalandı" : "Public key'i kopyala") {
                                        UIPasteboard.general.string = model.identity?.publicKeyOpenSSH
                                        copied = true
                                    }.buttonStyle(.tonal)
                                    Text("Elle eklenen host'ta bu satırı ~/.ssh/authorized_keys'e ekle. Kodla eşleme bunu otomatik yapar.")
                                        .font(PAFont.bodySmall).foregroundStyle(PA.text2)
                                }
                            }
                        }
                        .screenPadding().padding(.bottom, PA.Space.x3)
                    }
                }
            }
        }
    }
}
