// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftTerm
import SwiftUI

struct TerminalScreen: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var session: TerminalSession
    @AppStorage("terminalFontSize") private var fontSize = 13.0
    @State private var ctrl = false

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Theme.terminalBackground.ignoresSafeArea(edges: .horizontal)
                TerminalRepresentable(session: session, fontSize: fontSize, ctrl: $ctrl)
                    .padding(.horizontal, 4)
                    .accessibilityIdentifier("terminal")
                overlay
            }
            KeyBar(session: session, ctrl: $ctrl)
        }
        .background(Theme.terminalBackground)
        .navigationTitle(session.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.terminalBackground, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(session.title).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                    Text(session.host.address).font(.caption2.monospaced()).foregroundStyle(.white.opacity(0.6))
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { fontSize = min(fontSize + 1, 24) } label: { Label("Yazı büyüt", systemImage: "textformat.size.larger") }
                    Button { fontSize = max(fontSize - 1, 8) } label: { Label("Yazı küçült", systemImage: "textformat.size.smaller") }
                    if session.tmuxName != nil {
                        Button { session.detach() } label: { Label("tmux'tan ayrıl (detach)", systemImage: "rectangle.portrait.and.arrow.right") }
                    }
                    Button {
                        Task {
                            if let c = try? await model.connection(for: session.host) { session.start(on: c) }
                        }
                    } label: { Label("Yeniden bağlan", systemImage: "arrow.clockwise") }
                    Button(role: .destructive) { model.closeTerminal(session) } label: { Label("Kapat", systemImage: "xmark") }
                } label: { Image(systemName: "ellipsis.circle").foregroundStyle(.white) }
                .accessibilityIdentifier("terminalMenu")
            }
        }
    }

    @ViewBuilder private var overlay: some View {
        switch session.state {
        case .connecting:
            ProgressView("Bağlanıyor…").tint(.white).foregroundStyle(.white)
                .padding(16).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        case .closed, .failed:
            VStack(spacing: 12) {
                Image(systemName: session.state == .closed ? "powerplug" : "exclamationmark.triangle.fill")
                    .font(.largeTitle).foregroundStyle(.orange)
                Text(failureText).font(.callout).multilineTextAlignment(.center).foregroundStyle(.white)
                Button("Yeniden bağlan") {
                    Task {
                        if let c = try? await model.connection(for: session.host, fresh: true) { session.start(on: c) }
                    }
                }.buttonStyle(.borderedProminent)
            }
            .padding(20).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18)).padding(30)
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

    func makeCoordinator() -> Coordinator { Coordinator(session: session, ctrl: $ctrl) }

    func makeUIView(context: Context) -> TerminalView {
        let v = TerminalView(frame: .zero)
        v.terminalDelegate = context.coordinator
        v.nativeBackgroundColor = UIColor(Theme.terminalBackground)
        v.nativeForegroundColor = UIColor(white: 0.92, alpha: 1)
        v.font = UIFont.monospacedSystemFont(ofSize: fontSize, weight: .regular)
        v.optionAsMetaKey = true
        v.inputAccessoryView = nil
        session.view = v
        DispatchQueue.main.async { _ = v.becomeFirstResponder() }
        return v
    }

    func updateUIView(_ v: TerminalView, context: Context) {
        context.coordinator.ctrl = $ctrl
        if v.font.pointSize != fontSize {
            v.font = UIFont.monospacedSystemFont(ofSize: fontSize, weight: .regular)
        }
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

/// Keys the iOS keyboard lacks: Esc, Tab, Ctrl, arrows and common symbols.
struct KeyBar: View {
    @ObservedObject var session: TerminalSession
    @Binding var ctrl: Bool

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                key("esc") { session.send([0x1b]) }
                key("tab") { session.send([0x09]) }
                Button { ctrl.toggle() } label: {
                    Text("ctrl").font(.system(.footnote, design: .monospaced).weight(.semibold))
                        .frame(minWidth: 44, minHeight: 34)
                        .background(ctrl ? Color.accentColor : Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(.white)
                }.accessibilityIdentifier("key-ctrl")
                key("^C") { session.send([0x03]) }
                key("↑") { session.send("\u{1b}[A") }
                key("↓") { session.send("\u{1b}[B") }
                key("←") { session.send("\u{1b}[D") }
                key("→") { session.send("\u{1b}[C") }
                ForEach(["|", "~", "/", "-", "_", "$", "`"], id: \.self) { s in key(s) { session.send(s) } }
                key("⌨︎") {
                    if let v = session.view {
                        _ = v.isFirstResponder ? v.resignFirstResponder() : v.becomeFirstResponder()
                    }
                }
            }
            .padding(.horizontal, 8).padding(.vertical, 6)
        }
        .background(Color.black.opacity(0.55))
    }

    private func key(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            action()
        }) {
            Text(label).font(.system(.footnote, design: .monospaced).weight(.semibold))
                .frame(minWidth: 36, minHeight: 34).padding(.horizontal, 4)
                .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                .foregroundStyle(.white)
        }
        .accessibilityIdentifier("key-\(label)")
    }
}
