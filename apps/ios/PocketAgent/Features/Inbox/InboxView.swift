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
            Group {
                if model.accounts.isEmpty {
                    EmptyState(icon: "antenna.radiowaves.left.and.right.slash", title: "Backend bağlı değil",
                               message: "Agent olayları için bir host'u kodla eşle; backend token'ı otomatik gelir.")
                } else {
                    List {
                        Section {
                            Picker("Filtre", selection: $filter) {
                                Text("Tümü").tag(0)
                                Text("Onay bekleyen (\(model.pendingApprovals))").tag(1)
                            }.pickerStyle(.segmented)
                        }
                        .listRowBackground(Color.clear)
                        if case let .failed(msg) = model.inboxState {
                            Section { ErrorBanner(message: msg) { Task { await model.refreshInbox() } } }
                        }
                        if model.inboxState == .loading {
                            Section { HStack { ProgressView(); Text("Olaylar yükleniyor…").foregroundStyle(.secondary) } }
                        } else if shown.isEmpty {
                            Section {
                                VStack(spacing: 10) {
                                    Image(systemName: "tray").font(.largeTitle).foregroundStyle(.tertiary)
                                    Text(filter == 1 ? "Bekleyen onay yok" : "Son 24 saatte olay yok").font(.headline)
                                    Text("Claude/Codex hook'ları olay gönderdiğinde burada görünür.")
                                        .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                                }.frame(maxWidth: .infinity).padding(.vertical, 30)
                            }
                            .accessibilityIdentifier("inboxEmpty")
                        }
                        ForEach(shown) { e in
                            EventRow(event: e, decided: model.decided.contains(e.id)) {
                                Task { await approve(e) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Gelen kutusu")
            .refreshable { await model.refreshInbox() }
            .overlay(alignment: .bottom) {
                if let toast {
                    Text(toast).font(.callout.weight(.semibold)).padding(.horizontal, 18).padding(.vertical, 12)
                        .background(.thinMaterial, in: Capsule()).padding(.bottom, 16)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
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
        let (icon, color) = Theme.icon(for: event.kind)
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon).font(.title3).foregroundStyle(color).frame(width: 28)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(Theme.label(for: event.kind)).font(.subheadline.weight(.semibold))
                        Text(event.source.capitalized).font(.caption2.bold())
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(color.opacity(0.15), in: Capsule()).foregroundStyle(color)
                        Spacer()
                        Text(event.createdAt.relative).font(.caption).foregroundStyle(.secondary)
                    }
                    Text(event.message.isEmpty ? "—" : event.message).font(.callout).foregroundStyle(.primary)
                        .lineLimit(4)
                }
            }
            if event.kind == .approvalRequired {
                HStack {
                    Spacer()
                    if decided {
                        Label("Karar verildi", systemImage: "checkmark.circle.fill").font(.footnote.bold()).foregroundStyle(.green)
                    } else {
                        Button(action: onApprove) {
                            Label("Onayla", systemImage: "checkmark").font(.footnote.bold()).foregroundStyle(.white)
                        }
                        .buttonStyle(.borderedProminent).tint(.orange)
                        .accessibilityIdentifier("approve-\(event.eventID)")
                    }
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("event-\(event.eventID)")
    }
}
