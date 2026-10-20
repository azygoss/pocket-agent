// SPDX-License-Identifier: GPL-3.0-or-later
import Citadel
import Foundation
import NIOCore
import NIOSSH
import SwiftTerm

/// A PTY shell over an `SSHConnection`, optionally attached to a tmux
/// session (`tmux new-session -A` creates it if missing).
@MainActor
final class TerminalSession: ObservableObject, Identifiable {
    enum State: Equatable {
        case connecting
        case connected
        case closed
        case failed(String)
    }

    let id = UUID()
    let host: Host
    let tmuxName: String?
    @Published var state: State = .connecting
    @Published var title: String

    weak var view: TerminalView? {
        didSet { flushPending() }
    }
    private var pending: [UInt8] = []
    private var writer: TTYStdinWriter?
    private var task: Task<Void, Never>?
    private var size = (cols: 80, rows: 24)

    init(host: Host, tmuxName: String?) {
        self.host = host
        self.tmuxName = tmuxName
        self.title = tmuxName ?? host.name
    }

    static func attachCommand(_ tmux: String) -> String {
        SSHConnection.pathPrefix + "exec tmux new-session -A -s \(Shell.quote(tmux))\n"
    }

    func start(on connection: SSHConnection) {
        task?.cancel()
        state = .connecting
        let request = SSHChannelRequestEvent.PseudoTerminalRequest(
            wantReply: true, term: "xterm-256color",
            terminalCharacterWidth: size.cols, terminalRowHeight: size.rows,
            terminalPixelWidth: 0, terminalPixelHeight: 0,
            terminalModes: SSHTerminalModes([:]))
        let tmux = tmuxName
        task = Task { [weak self] in
            do {
                try await connection.client.withPTY(request) { inbound, outbound in
                    await MainActor.run {
                        self?.writer = outbound
                        self?.state = .connected
                    }
                    if let tmux {
                        try await outbound.write(ByteBuffer(string: Self.attachCommand(tmux)))
                    }
                    for try await chunk in inbound {
                        let bytes: [UInt8]
                        switch chunk {
                        case let .stdout(b), let .stderr(b): bytes = Array(b.readableBytesView)
                        }
                        await MainActor.run { self?.receive(bytes) }
                    }
                }
                await MainActor.run { self?.state = .closed }
            } catch is CancellationError {
                await MainActor.run { self?.state = .closed }
            } catch {
                await MainActor.run { self?.state = .failed(SSHConnection.readable(error)) }
            }
            await MainActor.run { self?.writer = nil }
        }
    }

    private func receive(_ bytes: [UInt8]) {
        if let view {
            view.feed(byteArray: bytes[...])
        } else {
            pending.append(contentsOf: bytes)
        }
    }

    private func flushPending() {
        guard let view, !pending.isEmpty else { return }
        view.feed(byteArray: pending[...])
        pending.removeAll()
    }

    func send(_ bytes: [UInt8]) {
        guard let writer else { return }
        Task { try? await writer.write(ByteBuffer(bytes: bytes)) }
    }

    func send(_ text: String) { send(Array(text.utf8)) }

    func resize(cols: Int, rows: Int) {
        guard cols > 0, rows > 0, (cols, rows) != size else { return }
        size = (cols, rows)
        guard let writer else { return }
        Task { try? await writer.changeSize(cols: cols, rows: rows, pixelWidth: 0, pixelHeight: 0) }
    }

    /// Detach from tmux (prefix + d) so the session keeps running on the host.
    func detach() {
        if tmuxName != nil { send([0x02, UInt8(ascii: "d")]) }
    }

    func stop() {
        task?.cancel()
        task = nil
        writer = nil
        if state != .closed { state = .closed }
    }
}
