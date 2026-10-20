// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct InboxView: View {
    @EnvironmentObject var model: AppModel
    @State private var filter = 0
    @State private var toast: String?

    private var shown: [AgentEvent] {
        let live = model.events.filter { $0.expiresAt > Date() }
        return filter == 1 ? live.filter { $0.kind == .approvalRequired && !model.decided.contains($0.id) } : live
    }

    var body: some View {
        NavigationStack {
            PocketScreen {
                VStack(spacing: 0) {
                    PocketTopBar("Gelen kutusu", subtitle: model.accounts.isEmpty ? nil : "Son 24 saat · \(shown.count) olay") {
                        IconAction(icon: "arrow.clockwise", label: "Yenile") { Task { await model.refreshInbox() } }
                    }
                    ScrollView {
                        VStack(alignment: .leading, spacing: PA.Space.lg) {
                            if model.accounts.isEmpty {
                                EmptyState(icon: "antenna.radiowaves.left.and.right.slash", title: "Backend bağlı değil",
                                           message: "Agent olayları için bir host'u kodla eşle; backend token'ı otomatik gelir.")
                                    .pocketCard()
                            } else {
                                filterBar
                                if case let .failed(msg) = model.inboxState {
                                    ErrorBanner(message: msg) { Task { await model.refreshInbox() } }
                                }
                                if model.inboxState == .loading && model.events.isEmpty {
                                    HStack(spacing: PA.Space.md) {
                                        ProgressView().tint(PA.accent)
                                        Text("Olaylar yükleniyor…").font(PAFont.bodyMedium).foregroundStyle(PA.text2)
                                    }.padding(PA.Space.lg).pocketCard()
                                } else if shown.isEmpty {
                                    EmptyState(icon: "tray", title: filter == 1 ? "Bekleyen onay yok" : "Son 24 saatte olay yok",
                                               message: "Claude/Codex hook'ları olay gönderdiğinde burada görünür.")
                                        .pocketCard()
                                        .accessibilityIdentifier("inboxEmpty")
                                } else {
                                    VStack(spacing: PA.Space.sm) {
                                        ForEach(shown) { e in
                                            EventRow(event: e, decided: model.decided.contains(e.id)) { Task { await approve(e) } }
                                        }
                                    }
                                }
                            }
                        }
                        .screenPadding().padding(.bottom, PA.Space.x3)
                    }
                    .refreshable { await model.refreshInbox() }
                }
            }
            .overlay(alignment: .bottom) {
                if let toast {
                    Text(toast).font(PAFont.labelLarge).foregroundStyle(PA.text)
                        .padding(.horizontal, 18).padding(.vertical, 12)
                        .background(PA.raised, in: Capsule())
                        .overlay(Capsule().stroke(PA.line, lineWidth: 1))
                        .padding(.bottom, PA.Space.lg)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .tint(PA.accent)
    }

    private var filterBar: some View {
        HStack(spacing: PA.Space.sm) {
            filterChip("Tümü", tag: 0)
            filterChip("Onay bekleyen", tag: 1, count: model.pendingApprovals)
            Spacer()
        }
    }

    private func filterChip(_ title: String, tag: Int, count: Int = 0) -> some View {
        Button { withAnimation(.snappy) { filter = tag } } label: {
            HStack(spacing: 6) {
                Text(title)
                if count > 0 { Text("\(count)").font(PAFont.labelSmall).padding(.horizontal, 6).padding(.vertical, 1)
                    .background(filter == tag ? PA.onAccent.opacity(0.2) : Tone.warn.soft, in: Capsule())
                    .foregroundStyle(filter == tag ? PA.onAccent : PA.warn) }
            }
            .font(PAFont.labelLarge)
            .foregroundStyle(filter == tag ? PA.onAccent : PA.text)
            .padding(.horizontal, PA.Space.lg).frame(height: 38)
            .background(filter == tag ? PA.accent : PA.card, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func approve(_ e: AgentEvent) async {
        do {
            let d = try await model.approve(e)
            show(d == .accepted ? "Onaylandı" : "Başka bir cihaz zaten karar verdi")
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } catch {
            show(error.localizedDescription)
        }
    }
    private func show(_ text: String) {
        withAnimation { toast = text }
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation { toast = nil }
        }
    }
}

struct EventRow: View {
    let event: AgentEvent
    let decided: Bool
    var onApprove: () -> Void

    var body: some View {
        let tone = Theme.tone(for: event.kind)
        let (icon, _) = Theme.icon(for: event.kind)
        PocketCard {
            VStack(alignment: .leading, spacing: PA.Space.md) {
                HStack(alignment: .top, spacing: PA.Space.md) {
                    IconTile(icon: icon, tone: tone, size: 40)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: PA.Space.sm) {
                            Text(Theme.label(for: event.kind)).font(PAFont.titleMedium).foregroundStyle(PA.text)
                            Tag(text: event.source.capitalized, tone: tone)
                        }
                        Text(event.opaqueSession.count >= 6 ? "\(event.createdAt.relative) · \(event.opaqueSession.prefix(8))" : event.createdAt.relative)
                            .font(PAFont.mono(11.5)).foregroundStyle(PA.text2)
                    }
                    Spacer(minLength: 0)
                    if event.kind == .approvalRequired && !decided { StatusDot(color: PA.warn, size: 8, pulsing: true).padding(.top, 6) }
                }
                Text(event.message.isEmpty ? "—" : event.message)
                    .font(PAFont.bodyMedium).foregroundStyle(PA.text).lineLimit(5)
                    .padding(PA.Space.md).frame(maxWidth: .infinity, alignment: .leading)
                    .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
                if event.kind == .approvalRequired {
                    HStack {
                        Spacer()
                        if decided {
                            StatusChip(text: "Karar verildi", tone: .live)
                        } else {
                            Button(action: onApprove) { Label("Onayla", systemImage: "checkmark") }
                                .buttonStyle(PrimaryButtonStyle(tone: .warn))
                                .accessibilityIdentifier("approve-\(event.eventID)")
                        }
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("event-\(event.eventID)")
    }
}
