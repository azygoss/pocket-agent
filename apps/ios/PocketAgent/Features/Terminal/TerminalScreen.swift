// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftTerm
import SwiftUI

struct TerminalScreen: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var session: TerminalSession
    @AppStorage("terminalFontSize") private var fontSize = 13.0
    @State private var ctrl = false

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack {
                TerminalRepresentable(session: session, fontSize: fontSize, ctrl: $ctrl)
                    .padding(.horizontal, 4)
                    .accessibilityIdentifier("terminal")
                overlay
            }
            KeyBar(session: session, ctrl: $ctrl)
        }
        .background(Theme.terminalBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
    }

    private var topBar: some View {
        HStack(spacing: PA.Space.sm) {
            IconAction(icon: "chevron.left", label: "Geri", tone: SwiftUI.Color(hex: 0xE6EBEF)) { dismiss() }.accessibilityIdentifier("back")
            StatusDot(color: stateColor, size: 8, pulsing: session.state == .connecting)
            VStack(alignment: .leading, spacing: 1) {
                Text(session.title).font(PAFont.titleMedium).foregroundStyle(SwiftUI.Color(hex: 0xE6EBEF)).lineLimit(1)
                Text(session.host.address).font(PAFont.mono(11.5)).foregroundStyle(SwiftUI.Color(hex: 0x8F9AA5)).lineLimit(1)
            }
            Spacer()
            if session.tmuxName != nil { Tag(text: "tmux", tone: .live) }
            Menu {
                Button { fontSize = min(fontSize + 1, 24) } label: { Label("Yazı büyüt", systemImage: "textformat.size.larger") }
                Button { fontSize = max(fontSize - 1, 8) } label: { Label("Yazı küçült", systemImage: "textformat.size.smaller") }
                if session.tmuxName != nil {
                    Button { session.detach() } label: { Label("tmux'tan ayrıl (detach)", systemImage: "rectangle.portrait.and.arrow.right") }
                }
                Button {
                    Task { if let c = try? await model.connection(for: session.host) { session.start(on: c) } }
                } label: { Label("Yeniden bağlan", systemImage: "arrow.clockwise") }
                Button(role: .destructive) { model.closeTerminal(session); dismiss() } label: { Label("Kapat", systemImage: "xmark") }
            } label: {
                Image(systemName: "ellipsis").font(.system(size: 17, weight: .semibold)).foregroundStyle(SwiftUI.Color(hex: 0xE6EBEF))
                    .frame(width: 40, height: 40)
            }
            .accessibilityIdentifier("terminalMenu")
        }
        .padding(.horizontal, PA.Space.sm)
        .frame(minHeight: 52)
        .background(SwiftUI.Color(hex: 0x0F1317))
    }

    private var stateColor: SwiftUI.Color {
        switch session.state {
        case .connected: PA.live
        case .connecting: PA.busy
        case .closed: PA.text2
        case .failed: PA.danger
        }
    }

    @ViewBuilder private var overlay: some View {
        switch session.state {
        case .connecting:
            HStack(spacing: PA.Space.md) {
                ProgressView().tint(PA.accent)
                Text("Bağlanıyor…").font(PAFont.bodyMedium).foregroundStyle(SwiftUI.Color(hex: 0xE6EBEF))
            }
            .padding(PA.Space.lg)
            .background(SwiftUI.Color(hex: 0x1B1F23), in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
        case .closed, .failed:
            VStack(spacing: PA.Space.lg) {
                IconTile(icon: session.state == .closed ? "powerplug" : "exclamationmark.triangle.fill",
                         tone: session.state == .closed ? .neutral : .warn, size: 56)
                Text(failureText).font(PAFont.bodyMedium).multilineTextAlignment(.center).foregroundStyle(SwiftUI.Color(hex: 0xE6EBEF))
                Button("Yeniden bağlan") {
                    Task { if let c = try? await model.connection(for: session.host, fresh: true) { session.start(on: c) } }
                }.buttonStyle(.primary)
            }
            .padding(PA.Space.xxl)
            .background(SwiftUI.Color(hex: 0x1B1F23), in: RoundedRectangle(cornerRadius: PA.Radius.card, style: .continuous))
            .padding(PA.Space.x3)
            .accessibilityIdentifier("terminalClosed")
        case .connected:
            EmptyView()
        }
    }

    private var failureText: String {
        if case let .failed(m) = session.state { return "Bağlantı koptu: \(m)" }
        return session.tmuxName == nil ? "Oturum kapandı." : "Bağlantı kapandı. tmux oturumu sunucuda çalışmaya devam ediyor."
    }
}

struct TerminalRepresentable: UIViewRepresentable {
    let session: TerminalSession
    var fontSize: Double
    @Binding var ctrl: Bool

    static func font(_ size: Double) -> UIFont {
        UIFont(name: PAFont.terminalFamily, size: size) ?? UIFont.monospacedSystemFont(ofSize: size, weight: .regular)
    }

    func makeCoordinator() -> Coordinator { Coordinator(session: session, ctrl: $ctrl) }
    func makeUIView(context: Context) -> TerminalView {
        let v = TerminalView(frame: .zero)
        v.terminalDelegate = context.coordinator
        v.nativeBackgroundColor = UIColor(Theme.terminalBackground)
        v.nativeForegroundColor = UIColor(red: 0.90, green: 0.92, blue: 0.94, alpha: 1)
        v.caretColor = UIColor(red: 0.24, green: 0.86, blue: 0.59, alpha: 1)
        v.font = Self.font(fontSize)
        v.optionAsMetaKey = true
        v.inputAccessoryView = nil
        session.view = v
        DispatchQueue.main.async { _ = v.becomeFirstResponder() }
        return v
    }
    func updateUIView(_ v: TerminalView, context: Context) {
        context.coordinator.ctrl = $ctrl
        if v.font.pointSize != fontSize { v.font = Self.font(fontSize) }
        if session.view !== v { session.view = v }
    }

    final class Coordinator: NSObject, TerminalViewDelegate {
        let session: TerminalSession
        var ctrl: Binding<Bool>
        init(session: TerminalSession, ctrl: Binding<Bool>) {
            self.session = session
            self.ctrl = ctrl
        }
        func send(source: TerminalView, data: ArraySlice<UInt8>) {
            var bytes = Array(data)
            if ctrl.wrappedValue, bytes.count == 1, let b = bytes.first {
                let lower = b | 0x20
                if lower >= 0x61 && lower <= 0x7a { bytes = [lower - 0x60] }
                DispatchQueue.main.async { self.ctrl.wrappedValue = false }
            }
            Task { @MainActor in session.send(bytes) }
        }
        func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {
            Task { @MainActor in session.resize(cols: newCols, rows: newRows) }
        }
        func setTerminalTitle(source: TerminalView, title: String) {}
        func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}
        func scrolled(source: TerminalView, position: Double) {}
        func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {
            if let u = URL(string: link), ["http", "https"].contains(u.scheme) { UIApplication.shared.open(u) }
        }
        func bell(source: TerminalView) { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        func clipboardCopy(source: TerminalView, content: Data) {
            UIPasteboard.general.string = String(data: content, encoding: .utf8)
        }
        func iTermContent(source: TerminalView, content: ArraySlice<UInt8>) {}
        func rangeChanged(source: TerminalView, startY: Int, endY: Int) {}
    }
}

/// Keys the iOS keyboard lacks; grouped like Android's KeyToolbar.
struct KeyBar: View {
    @ObservedObject var session: TerminalSession
    @Binding var ctrl: Bool
    private let bar = SwiftUI.Color(hex: 0x24292D)
    private let cap = SwiftUI.Color(hex: 0x1B1F23)
    private let ink = SwiftUI.Color(hex: 0xE6EBEF)

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                keyCap("ctrl", latched: ctrl, id: "key-ctrl") { ctrl.toggle() }
                sep
                key("esc") { session.send([0x1b]) }
                key("⏎") { session.send([0x0d]) }
                key("↑") { session.send("\u{1b}[A") }
                key("↓") { session.send("\u{1b}[B") }
                key("←") { session.send("\u{1b}[D") }
                key("→") { session.send("\u{1b}[C") }
                key("tab") { session.send([0x09]) }
                key("⇧tab") { session.send("\u{1b}[Z") }
                sep
                key("^C") { session.send([0x03]) }
                key("^D") { session.send([0x04]) }
                key("^Z") { session.send([0x1a]) }
                key("^L") { session.send([0x0c]) }
                sep
                key("home") { session.send("\u{1b}[H") }
                key("end") { session.send("\u{1b}[F") }
                key("pgup") { session.send("\u{1b}[5~") }
                key("pgdn") { session.send("\u{1b}[6~") }
                sep
                ForEach(["|", "~", "-", "_", "/", "$", "`"], id: \.self) { s in key(s) { session.send(s) } }
                sep
                keyCap("", icon: "doc.on.clipboard", id: "key-paste") {
                    if let s = UIPasteboard.general.string { session.send(s) }
                }
                keyCap("", icon: "keyboard", id: "key-keyboard") {
                    if let v = session.view { _ = v.isFirstResponder ? v.resignFirstResponder() : v.becomeFirstResponder() }
                }
            }
            .padding(6)
        }
        .background(bar, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .padding(.horizontal, PA.Space.sm).padding(.vertical, 6)
        .background(SwiftUI.Color(hex: 0x0F1317))
    }

    private var sep: some View { Rectangle().fill(SwiftUI.Color(hex: 0x323940)).frame(width: 1, height: 22) }

    private func key(_ label: String, _ action: @escaping () -> Void) -> some View {
        keyCap(label, id: "key-\(label)", action)
    }

    private func keyCap(_ label: String, icon: String? = nil, latched: Bool = false, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            action()
        }) {
            Group {
                if let icon { Image(systemName: icon).font(.system(size: 15, weight: .medium)) }
                else { Text(label).font(PAFont.mono(13, .bold)) }
            }
            .frame(minWidth: 40, minHeight: 40).padding(.horizontal, 6)
            .background(latched ? PA.accent : cap, in: RoundedRectangle(cornerRadius: PA.Radius.key, style: .continuous))
            .foregroundStyle(latched ? PA.onAccent : ink)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}
