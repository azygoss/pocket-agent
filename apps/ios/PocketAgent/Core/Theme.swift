// SPDX-License-Identifier: GPL-3.0-or-later
// Pocket design system: mirrors apps/android/.../ui/theme (PocketTokens) so both apps look alike.
import SwiftUI

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(UIColor { t in
            let v = t.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
                blue: CGFloat(v & 0xFF) / 255, alpha: 1)
        })
    }
    init(hex v: UInt32) { self.init(light: v, dark: v) }
}

enum PA {
    // "Pocket" dark theme / "Pocket Paper" light theme from ConsoleThemes.kt
    static let bg = Color(light: 0xFFFFFF, dark: 0x0F1317)
    static let card = Color(light: 0xF6F6F6, dark: 0x1B1F23)
    static let raised = Color(light: 0xEDEEF0, dark: 0x24292D)
    static let overlay = Color(light: 0xE3E5E8, dark: 0x2D3338)
    static let line = Color(light: 0xE1E4E8, dark: 0x323940)
    static let text = Color(light: 0x14181C, dark: 0xE6EBEF)
    static let text2 = Color(light: 0x5B6570, dark: 0x8F9AA5)
    static let accent = Color(light: 0x0A7A52, dark: 0x3DDC97)
    static let onAccent = Color(light: 0xFFFFFF, dark: 0x06140E)
    static let live = Color(light: 0x0A7A52, dark: 0x3DDC97)
    static let busy = Color(light: 0x2E6FD6, dark: 0x63A4FF)
    static let warn = Color(light: 0xB7791F, dark: 0xF2B64C)
    static let danger = Color(light: 0xC5423B, dark: 0xFF7A72)
    static let violet = Color(light: 0x7A4ED1, dark: 0xC792EA)
    static let termBg = Color(hex: 0x0B0E11)

    enum Radius {
        static let key: CGFloat = 10
        static let field: CGFloat = 14
        static let card: CGFloat = 22
        static let sheet: CGFloat = 28
    }
    enum Space {
        static let xs: CGFloat = 4, sm: CGFloat = 8, md: CGFloat = 12, lg: CGFloat = 16
        static let xl: CGFloat = 20, xxl: CGFloat = 24, section: CGFloat = 28, x3: CGFloat = 32
    }
}

enum PAFont {
    static func ui(_ size: CGFloat, _ w: Font.Weight = .regular) -> Font {
        let name: String = switch w {
        case .bold, .heavy, .black: "IBMPlexSans-Bold"
        case .semibold: "IBMPlexSans-SmBld"
        case .medium: "IBMPlexSans-Medm"
        default: "IBMPlexSans"
        }
        return .custom(name, size: size)
    }
    static func mono(_ size: CGFloat, _ w: Font.Weight = .regular) -> Font {
        .custom(w == .bold || w == .semibold ? "IBMPlexMono-Bold" : "IBMPlexMono-Regular", size: size)
    }
    static let terminalFamily = "JetBrainsMono-Regular"

    static let headline = ui(28, .semibold)
    static let titleLarge = ui(20, .semibold)
    static let titleMedium = ui(16, .semibold)
    static let titleSmall = ui(15, .semibold)
    static let body = ui(16)
    static let bodyMedium = ui(14)
    static let bodySmall = ui(12.5)
    static let labelLarge = ui(14, .medium)
    static let labelMedium = ui(12, .medium)
    static let labelSmall = ui(11, .medium)
}

/// Semantic colour pairing used by chips, tiles and tags (Android `Tone`).
struct Tone: Equatable {
    var fg: Color
    static let live = Tone(fg: PA.live)
    static let busy = Tone(fg: PA.busy)
    static let warn = Tone(fg: PA.warn)
    static let danger = Tone(fg: PA.danger)
    static let violet = Tone(fg: PA.violet)
    static let accent = Tone(fg: PA.accent)
    static let neutral = Tone(fg: PA.text2)
    var soft: Color { fg.opacity(0.17) }
}

// MARK: - Legacy helpers kept for call sites

enum Theme {
    static let accents: [Color] = [PA.accent, PA.busy, PA.violet, PA.warn, PA.danger, Color(hex: 0x4FC3F7)]
    static func accent(_ i: Int) -> Color { accents[abs(i) % accents.count] }
    static let terminalBackground = PA.termBg

    static func tone(for kind: AgentEvent.Kind) -> Tone {
        switch kind {
        case .approvalRequired: .warn
        case .taskComplete: .live
        case .taskFailed: .danger
        case .question: .busy
        case .sessionEnded: .neutral
        case .other: .violet
        }
    }
    static func icon(for kind: AgentEvent.Kind) -> (String, Color) {
        let name: String = switch kind {
        case .approvalRequired: "hand.raised.fill"
        case .taskComplete: "checkmark.seal.fill"
        case .taskFailed: "xmark.octagon.fill"
        case .question: "questionmark.bubble.fill"
        case .sessionEnded: "stop.circle.fill"
        case .other: "bell.fill"
        }
        return (name, tone(for: kind).fg)
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

// MARK: - Primitives

struct PocketCard<Content: View>: View {
    var padding: CGFloat = PA.Space.lg
    var fill: Color = PA.card
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(fill, in: RoundedRectangle(cornerRadius: PA.Radius.card, style: .continuous))
    }
}
typealias Card = PocketCard

struct SectionHeader<Trailing: View>: View {
    var title: String
    var count: Int?
    @ViewBuilder var trailing: Trailing
    init(_ title: String, count: Int? = nil, @ViewBuilder trailing: () -> Trailing = { EmptyView() }) {
        self.title = title; self.count = count; self.trailing = trailing()
    }
    var body: some View {
        HStack(spacing: PA.Space.sm) {
            Text(title).font(PAFont.titleSmall).foregroundStyle(PA.text)
            if let count, count > 0 { Text("\(count)").font(PAFont.labelMedium).foregroundStyle(PA.text2) }
            Spacer()
            trailing
        }
        .frame(minHeight: 40)
        .padding(.horizontal, PA.Space.xs)
    }
}

struct StatusDot: View {
    var color: Color
    var size: CGFloat = 8
    var pulsing = false
    var hollow = false
    @State private var on = false
    var body: some View {
        Circle()
            .fill(hollow ? Color.clear : color)
            .overlay(Circle().stroke(color, lineWidth: hollow ? 1.5 : 0))
            .frame(width: size, height: size)
            .opacity(pulsing && on ? 0.35 : 1)
            .animation(pulsing ? .easeInOut(duration: 0.9).repeatForever(autoreverses: true) : .default, value: on)
            .onAppear { on = true }
    }
}

struct StatusChip: View {
    var text: String
    var tone: Tone
    var pulsing = false
    var body: some View {
        HStack(spacing: 6) {
            StatusDot(color: tone.fg, size: 7, pulsing: pulsing)
            Text(text).font(PAFont.labelMedium).foregroundStyle(tone.fg)
        }
        .padding(.horizontal, 10).padding(.vertical, 5)
        .background(tone.soft, in: Capsule())
    }
}

struct Tag: View {
    var text: String
    var tone: Tone = .neutral
    var body: some View {
        Text(text).font(PAFont.labelSmall).foregroundStyle(tone.fg)
            .padding(.horizontal, 7).padding(.vertical, 3)
            .background(tone.soft, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}

struct HostAvatar: View {
    var name: String
    var size: CGFloat = 44
    var color: Color = PA.raised
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
            .fill(color)
            .frame(width: size, height: size)
            .overlay(
                Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
                    .font(PAFont.ui(size * 0.42, .semibold)).foregroundStyle(PA.text2))
    }
}

struct IconTile: View {
    var icon: String
    var tone: Tone = .accent
    var size: CGFloat = 40
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .fill(tone.soft)
            .frame(width: size, height: size)
            .overlay(Image(systemName: icon).font(.system(size: size * 0.45, weight: .medium)).foregroundStyle(tone.fg))
    }
}

struct BrandMark: View {
    var size: CGFloat = 30
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .fill(PA.accent)
            .frame(width: size, height: size)
            .overlay {
                HStack(spacing: size * 0.08) {
                    Image(systemName: "chevron.right").font(.system(size: size * 0.42, weight: .bold))
                    RoundedRectangle(cornerRadius: 1).frame(width: size * 0.26, height: size * 0.09).offset(y: size * 0.18)
                }
                .foregroundStyle(PA.onAccent)
            }
    }
}

struct InsetDivider: View {
    var inset: CGFloat = 72
    var body: some View { Rectangle().fill(PA.line).frame(height: 1).padding(.leading, inset) }
}

struct ListRow<Leading: View, Trailing: View>: View {
    var title: String
    var subtitle: String?
    var subtitleMono = false
    var titleMono = false
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing
    init(_ title: String, subtitle: String? = nil, subtitleMono: Bool = false, titleMono: Bool = false,
         @ViewBuilder leading: () -> Leading = { EmptyView() }, @ViewBuilder trailing: () -> Trailing = { EmptyView() }) {
        self.title = title; self.subtitle = subtitle; self.subtitleMono = subtitleMono; self.titleMono = titleMono
        self.leading = leading(); self.trailing = trailing()
    }
    var body: some View {
        HStack(spacing: PA.Space.md) {
            leading
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(titleMono ? PAFont.mono(15) : PAFont.titleMedium).foregroundStyle(PA.text).lineLimit(1)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle).font(subtitleMono ? PAFont.mono(12.5) : PAFont.bodySmall).foregroundStyle(PA.text2).lineLimit(1)
                }
            }
            Spacer(minLength: PA.Space.sm)
            trailing
        }
        .padding(.horizontal, PA.Space.lg).padding(.vertical, PA.Space.md)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }
}

struct Chevron: View {
    var body: some View { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(PA.text2) }
}

// MARK: - Buttons

struct PrimaryButtonStyle: ButtonStyle {
    var tone: Tone? = nil
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration c: Configuration) -> some View {
        c.label.font(PAFont.labelLarge)
            .foregroundStyle(tone == nil ? PA.onAccent : Color.white)
            .padding(.horizontal, PA.Space.xl).frame(minHeight: 46)
            .background((tone?.fg ?? PA.accent).opacity(c.isPressed ? 0.8 : 1), in: Capsule())
            .opacity(enabled ? 1 : 0.45)
    }
}
struct TonalButtonStyle: ButtonStyle {
    var tone: Tone = .accent
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration c: Configuration) -> some View {
        c.label.font(PAFont.labelLarge).foregroundStyle(tone.fg)
            .padding(.horizontal, PA.Space.lg).frame(minHeight: 40)
            .background(tone.soft.opacity(c.isPressed ? 0.6 : 1), in: Capsule())
            .opacity(enabled ? 1 : 0.45)
    }
}
struct OutlineButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration c: Configuration) -> some View {
        c.label.font(PAFont.labelLarge).foregroundStyle(PA.text)
            .padding(.horizontal, PA.Space.xl).frame(minHeight: 46)
            .background(Capsule().stroke(PA.line, lineWidth: 1))
            .background(c.isPressed ? PA.raised : Color.clear, in: Capsule())
            .opacity(enabled ? 1 : 0.45)
    }
}
struct GhostButtonStyle: ButtonStyle {
    var tone: Tone = .accent
    func makeBody(configuration c: Configuration) -> some View {
        c.label.font(PAFont.labelLarge).foregroundStyle(tone.fg).padding(.horizontal, PA.Space.md).frame(minHeight: 40)
            .opacity(c.isPressed ? 0.6 : 1)
    }
}
extension ButtonStyle where Self == PrimaryButtonStyle { static var primary: PrimaryButtonStyle { .init() } }
extension ButtonStyle where Self == TonalButtonStyle { static var tonal: TonalButtonStyle { .init() } }
extension ButtonStyle where Self == OutlineButtonStyle { static var outline: OutlineButtonStyle { .init() } }
extension ButtonStyle where Self == GhostButtonStyle { static var ghost: GhostButtonStyle { .init() } }

struct IconAction: View {
    var icon: String
    var label: String
    var tone: Color = PA.text
    var fill: Color? = nil
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 17, weight: .medium)).foregroundStyle(tone)
                .frame(width: 40, height: 40)
                .background(fill ?? Color.clear, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Screen chrome

struct PocketTopBar<Actions: View>: View {
    var title: String
    var subtitle: String?
    var back: (() -> Void)?
    @ViewBuilder var actions: Actions
    init(_ title: String, subtitle: String? = nil, back: (() -> Void)? = nil, @ViewBuilder actions: () -> Actions = { EmptyView() }) {
        self.title = title; self.subtitle = subtitle; self.back = back; self.actions = actions()
    }
    var body: some View {
        HStack(spacing: PA.Space.sm) {
            if let back { IconAction(icon: "chevron.left", label: "Geri", action: back).accessibilityIdentifier("back") }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(PAFont.titleLarge).foregroundStyle(PA.text).lineLimit(1)
                if let subtitle { Text(subtitle).font(PAFont.bodySmall).foregroundStyle(PA.text2).lineLimit(1) }
            }
            Spacer()
            actions
        }
        .padding(.horizontal, back == nil ? PA.Space.lg : PA.Space.sm)
        .frame(minHeight: 64)
    }
}

struct PocketScreen<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        ZStack { PA.bg.ignoresSafeArea(); content }
            .toolbar(.hidden, for: .navigationBar)
    }
}

struct EmptyState: View {
    var icon: String
    var title: String
    var message: String
    var actionTitle: String?
    var action: (() -> Void)?
    var body: some View {
        VStack(spacing: PA.Space.lg) {
            IconTile(icon: icon, tone: .accent, size: 64)
            Text(title).font(PAFont.titleLarge).foregroundStyle(PA.text)
            Text(message).font(PAFont.bodyMedium).foregroundStyle(PA.text2).multilineTextAlignment(.center)
            if let actionTitle, let action { Button(actionTitle, action: action).buttonStyle(.primary) }
        }
        .padding(PA.Space.x3)
        .frame(maxWidth: .infinity)
    }
}

struct Banner: View {
    var message: String
    var tone: Tone = .warn
    var icon = "exclamationmark.triangle.fill"
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(tone.fg).padding(.top, 2)
            Text(message).font(PAFont.bodyMedium).foregroundStyle(PA.text).frame(maxWidth: .infinity, alignment: .leading)
            if let actionTitle, let action { Button(actionTitle, action: action).buttonStyle(GhostButtonStyle(tone: tone)) }
        }
        .padding(PA.Space.md)
        .background(tone.soft, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
    }
}

struct ErrorBanner: View {
    var message: String
    var retry: (() -> Void)?
    var body: some View {
        Banner(message: message, tone: .warn, actionTitle: retry == nil ? nil : "Tekrar dene", action: retry)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("errorBanner")
    }
}

struct CodeLine: View {
    var code: String
    var body: some View {
        HStack(spacing: PA.Space.sm) {
            Text(code).font(PAFont.mono(13)).foregroundStyle(PA.text).lineLimit(1).truncationMode(.middle)
            Spacer()
            Button { UIPasteboard.general.string = code } label: {
                Image(systemName: "doc.on.doc").font(.system(size: 14)).foregroundStyle(PA.text2)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, PA.Space.md).padding(.vertical, 10)
        .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.key, style: .continuous))
    }
}

struct ValueBox: View {
    var label: String
    var value: String
    var mono = true
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(PAFont.labelSmall).foregroundStyle(PA.text2)
            Text(value).font(mono ? PAFont.mono(13) : PAFont.bodyMedium).foregroundStyle(PA.text).textSelection(.enabled)
        }
        .padding(PA.Space.md).frame(maxWidth: .infinity, alignment: .leading)
        .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
    }
}

struct PocketField: View {
    var placeholder: String
    @Binding var text: String
    var mono = false
    var icon: String? = nil
    var body: some View {
        HStack(spacing: PA.Space.sm) {
            if let icon { Image(systemName: icon).foregroundStyle(PA.text2) }
            TextField(placeholder, text: $text)
                .font(mono ? PAFont.mono(15) : PAFont.body).foregroundStyle(PA.text)
        }
        .padding(.horizontal, PA.Space.lg).frame(minHeight: 50)
        .background(PA.raised, in: RoundedRectangle(cornerRadius: PA.Radius.field, style: .continuous))
    }
}

extension View {
    func pocketCard(padding: CGFloat = 0) -> some View {
        self.padding(padding).frame(maxWidth: .infinity, alignment: .leading)
            .background(PA.card, in: RoundedRectangle(cornerRadius: PA.Radius.card, style: .continuous))
    }
    func screenPadding() -> some View { padding(.horizontal, PA.Space.lg) }
}

extension Date {
    var relative: String {
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.unitsStyle = .short
        return f.localizedString(for: self, relativeTo: Date())
    }
}
