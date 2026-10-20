// SPDX-License-Identifier: GPL-3.0-or-later
import Citadel
import NIOCore
import SwiftUI
import UniformTypeIdentifiers

struct FilesView: View {
    @EnvironmentObject var model: AppModel
    let host: Host
    @State private var path = ""
    @State private var files: [RemoteFile] = []
    @State private var state: AppModel.LoadState = .idle
    @State private var showHidden = false
    @State private var preview: FilePreview?
    @State private var renaming: RemoteFile?
    @State private var newName = ""
    @State private var askFolder = false
    @State private var importing = false
    @State private var busy: String?
    @State private var shareURL: URL?

    struct FilePreview: Identifiable {
        let id = UUID()
        let file: RemoteFile
        let text: String?
        let localURL: URL
    }

    var visible: [RemoteFile] { files.filter { showHidden || !$0.name.hasPrefix(".") } }

    var body: some View {
        List {
            Section {
                breadcrumbs
            }
            if let busy { Section { HStack { ProgressView(); Text(busy) } } }
            switch state {
            case .idle, .loading:
                Section { HStack { ProgressView(); Text("Yükleniyor…").foregroundStyle(.secondary) } }
            case let .failed(msg):
                Section { ErrorBanner(message: msg) { Task { await load() } } }
            case .loaded:
                if visible.isEmpty {
                    Section { Text("Klasör boş").foregroundStyle(.secondary) }
                }
                Section {
                    ForEach(visible) { f in
                        Button { Task { await tap(f) } } label: { FileRow(file: f) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("file-\(f.name)")
                            .swipeActions {
                                Button("Sil", role: .destructive) { Task { await remove(f) } }
                                Button("Ad") { renaming = f; newName = f.name }.tint(.blue)
                            }
                            .contextMenu {
                                Button { renaming = f; newName = f.name } label: { Label("Yeniden adlandır", systemImage: "pencil") }
                                if !f.isDirectory {
                                    Button { Task { await download(f, share: true) } } label: { Label("Paylaş / indir", systemImage: "square.and.arrow.up") }
                                }
                                Button(role: .destructive) { Task { await remove(f) } } label: { Label("Sil", systemImage: "trash") }
                            }
                    }
                }
            }
        }
        .navigationTitle(path == "" ? "Dosyalar" : (path as NSString).lastPathComponent)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { if path.isEmpty { await load() } }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { importing = true } label: { Label("Dosya yükle", systemImage: "square.and.arrow.down") }
                    Button { askFolder = true } label: { Label("Yeni klasör", systemImage: "folder.badge.plus") }
                    Toggle(isOn: $showHidden) { Label("Gizli dosyalar", systemImage: "eye") }
                } label: { Image(systemName: "ellipsis.circle") }
                .accessibilityIdentifier("filesMenu")
            }
        }
        .sheet(item: $preview) { p in FilePreviewView(preview: p) }
        .sheet(item: Binding(get: { shareURL.map { ShareItem(url: $0) } }, set: { _ in shareURL = nil })) { item in
            ShareSheet(url: item.url)
        }
        .alert("Yeniden adlandır", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("ad", text: $newName).textInputAutocapitalization(.never).autocorrectionDisabled()
            Button("Kaydet") { if let f = renaming { Task { await rename(f, to: newName) } } }
            Button("Vazgeç", role: .cancel) {}
        }
        .alert("Yeni klasör", isPresented: $askFolder) {
            TextField("ad", text: $newName).textInputAutocapitalization(.never).autocorrectionDisabled()
            Button("Oluştur") { Task { await mkdir(newName) } }
            Button("Vazgeç", role: .cancel) {}
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            if case let .success(urls) = result { Task { await upload(urls) } }
        }
    }

    private var breadcrumbs: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                let parts = path.split(separator: "/").map(String.init)
                Button { Task { await go("/") } } label: { Image(systemName: "externaldrive") }
                ForEach(parts.indices, id: \.self) { i in
                    Image(systemName: "chevron.right").font(.caption2).foregroundStyle(.tertiary)
                    Button(parts[i]) { Task { await go("/" + parts[0...i].joined(separator: "/")) } }
                        .font(.footnote.monospaced())
                }
            }
        }
        .buttonStyle(.borderless)
    }

    private func sftp() async throws -> SFTPClient {
        try await model.connection(for: host).sftp()
    }

    private func load() async {
        if files.isEmpty { state = .loading }
        do {
            let s = try await sftp()
            if path.isEmpty { path = try await s.getRealPath(atPath: ".") }
            let names = try await s.listDirectory(atPath: path)
            files = names.flatMap(\.components).compactMap { c in
                guard c.filename != "." && c.filename != ".." else { return nil }
                let perms = c.attributes.permissions ?? 0
                let isDir = perms & 0o170000 == 0o040000 || c.longname.hasPrefix("d")
                let isLink = perms & 0o170000 == 0o120000 || c.longname.hasPrefix("l")
                return RemoteFile(name: c.filename, path: (path as NSString).appendingPathComponent(c.filename),
                                  isDirectory: isDir, isSymlink: isLink, size: c.attributes.size ?? 0,
                                  modified: c.attributes.accessModificationTime?.modificationTime, permissions: perms)
            }
            .sorted { ($0.isDirectory ? 0 : 1, $0.name.lowercased()) < ($1.isDirectory ? 0 : 1, $1.name.lowercased()) }
            state = .loaded
        } catch {
            state = .failed(SSHConnection.readable(error))
        }
    }

    private func go(_ p: String) async {
        path = p
        files = []
        await load()
    }

    private func tap(_ f: RemoteFile) async {
        var isDir = f.isDirectory
        if !isDir && f.isSymlink { isDir = (try? await sftp().listDirectory(atPath: f.path)) != nil }
        if isDir {
            await go(f.path)
        } else {
            await download(f, share: false)
        }
    }

    private func download(_ f: RemoteFile, share: Bool) async {
        busy = "\(f.name) indiriliyor…"
        defer { busy = nil }
        do {
            let s = try await sftp()
            let maxBytes: UInt64 = 50 << 20
            guard f.size <= maxBytes else { throw SSHFailure.command("Dosya çok büyük (50 MB sınırı).") }
            let buf = try await s.withFile(filePath: f.path, flags: .read) { try await $0.readAll() }
            let data = Data(buf.readableBytesView)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(f.name)
            try data.write(to: url, options: .atomic)
            if share {
                shareURL = url
            } else {
                let text = data.count <= 1 << 20 ? String(data: data, encoding: .utf8) : nil
                preview = FilePreview(file: f, text: text, localURL: url)
            }
        } catch {
            state = .failed(SSHConnection.readable(error))
        }
    }

    private func upload(_ urls: [URL]) async {
        for u in urls {
            busy = "\(u.lastPathComponent) yükleniyor…"
            let scoped = u.startAccessingSecurityScopedResource()
            defer { if scoped { u.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: u)
                let s = try await sftp()
                let target = (path as NSString).appendingPathComponent(u.lastPathComponent)
                try await s.withFile(filePath: target, flags: [.write, .create, .truncate]) { file in
                    try await file.write(ByteBuffer(bytes: data), at: 0)
                }
            } catch {
                state = .failed(SSHConnection.readable(error))
            }
        }
        busy = nil
        await load()
    }

    private func remove(_ f: RemoteFile) async {
        do {
            let s = try await sftp()
            if f.isDirectory { try await s.rmdir(at: f.path) } else { try await s.remove(at: f.path) }
            await load()
        } catch {
            state = .failed("Silinemedi: \(SSHConnection.readable(error)) (klasör boş olmalı)")
        }
    }

    private func rename(_ f: RemoteFile, to name: String) async {
        guard !name.isEmpty, name != f.name, !name.contains("/") else { return }
        do {
            try await sftp().rename(at: f.path, to: (path as NSString).appendingPathComponent(name))
            await load()
        } catch {
            state = .failed(SSHConnection.readable(error))
        }
    }

    private func mkdir(_ name: String) async {
        defer { newName = "" }
        guard !name.isEmpty, !name.contains("/") else { return }
        do {
            try await sftp().createDirectory(atPath: (path as NSString).appendingPathComponent(name))
            await load()
        } catch {
            state = .failed(SSHConnection.readable(error))
        }
    }
}

struct ShareItem: Identifiable {
    let url: URL
    var id: URL { url }
}

struct FileRow: View {
    let file: RemoteFile
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.title3).foregroundStyle(file.isDirectory ? Color.accentColor : .secondary).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(file.name).foregroundStyle(.primary).lineLimit(1)
                HStack(spacing: 6) {
                    if !file.isDirectory { Text(ByteCountFormatter.string(fromByteCount: Int64(file.size), countStyle: .file)) }
                    if let m = file.modified { Text(m.relative) }
                }.font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if file.isDirectory { Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary) }
        }
        .contentShape(Rectangle())
    }

    private var icon: String {
        if file.isDirectory { return "folder.fill" }
        if file.isSymlink { return "link" }
        switch (file.name as NSString).pathExtension.lowercased() {
        case "swift", "go", "kt", "js", "ts", "py", "rs", "c", "h", "sh", "rb": return "chevron.left.forwardslash.chevron.right"
        case "md", "txt", "log": return "doc.text"
        case "png", "jpg", "jpeg", "gif", "heic", "webp": return "photo"
        case "json", "yaml", "yml", "toml": return "curlybraces"
        case "zip", "gz", "tar", "tgz": return "archivebox"
        default: return "doc"
        }
    }
}

struct FilePreviewView: View {
    let preview: FilesView.FilePreview
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            Group {
                if let text = preview.text {
                    ScrollView([.vertical, .horizontal]) {
                        Text(text).font(.caption.monospaced()).textSelection(.enabled).padding()
                            .accessibilityIdentifier("filePreviewText")
                    }
                } else if let img = UIImage(contentsOfFile: preview.localURL.path) {
                    ScrollView { Image(uiImage: img).resizable().scaledToFit() }
                } else {
                    EmptyState(icon: "doc.questionmark", title: "Önizleme yok",
                               message: "Bu dosya türü gösterilemiyor; paylaş ile başka uygulamada aç.")
                }
            }
            .navigationTitle(preview.file.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } }
                ToolbarItem(placement: .primaryAction) { ShareLink(item: preview.localURL) }
            }
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
