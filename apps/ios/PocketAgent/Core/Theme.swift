// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

enum Theme {
    static let accents: [Color] = [
        Color(red: 0.36, green: 0.55, blue: 1.0),
        Color(red: 0.62, green: 0.42, blue: 1.0),
        Color(red: 0.16, green: 0.78, blue: 0.62),
        Color(red: 1.0, green: 0.58, blue: 0.24),
        Color(red: 0.95, green: 0.36, blue: 0.52),
        Color(red: 0.2, green: 0.72, blue: 0.95),
    ]
    static func accent(_ i: Int) -> Color { accents[abs(i) % accents.count] }

    static let terminalBackground = Color(red: 0.06, green: 0.07, blue: 0.10)
    static let brandGradient = LinearGradient(
        colors: [Color(red: 0.30, green: 0.45, blue: 1.0), Color(red: 0.58, green: 0.36, blue: 0.98)],
        startPoint: .topLeading, endPoint: .bottomTrailing)

    static func icon(for kind: AgentEvent.Kind) -> (String, Color) {
        switch kind {
        case .approvalRequired: ("hand.raised.fill", .orange)
        case .taskComplete: ("checkmark.seal.fill", .green)
        case .taskFailed: ("xmark.octagon.fill", .red)
        case .question: ("questionmark.bubble.fill", .blue)
        case .sessionEnded: ("stop.circle.fill", .gray)
        case .other: ("bell.fill", .purple)
        }
    }

    static func label(for kind: AgentEvent.Kind) -> String {
        switch kind {
        case .approvalRequired: "Onay bekliyor"
        case .taskComplete: "Tamamlandı"
        case .taskFailed: "Başarısız"
        case .question: "Soru"
        case .sessionEnded: "Oturum bitti"
        case .other: "Olay"
        }
    }
}

struct Card<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct StatusDot: View {
    var color: Color
    var pulsing = false
    @State private var on = false
    var body: some View {
        Circle().fill(color).frame(width: 9, height: 9)
            .overlay(Circle().stroke(color.opacity(0.4), lineWidth: pulsing && on ? 6 : 0))
            .animation(pulsing ? .easeInOut(duration: 0.9).repeatForever(autoreverses: true) : .default, value: on)
            .onAppear { on = true }
    }
}

struct EmptyState: View {
    var icon: String
    var title: String
    var message: String
    var actionTitle: String?
    var action: (() -> Void)?
    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: icon)
        } description: {
            Text(message)
        } actions: {
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
    }
}

struct ErrorBanner: View {
    var message: String
    var retry: (() -> Void)?
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            Text(message).font(.callout).frame(maxWidth: .infinity, alignment: .leading)
            if let retry { Button("Tekrar dene", action: retry).font(.callout.bold()) }
        }
        .padding(12)
        .background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("errorBanner")
    }
}

extension Date {
    var relative: String {
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.unitsStyle = .short
        return f.localizedString(for: self, relativeTo: Date())
    }
}
