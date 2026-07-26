import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private static let appGroup = "group.com.speedywatch.ios"
    private static let pendingURLKey = "pending_shared_video_url"

    private let messageLabel = UILabel()
    private let doneButton = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 15 / 255, green: 15 / 255, blue: 15 / 255, alpha: 1)

        messageLabel.text = "Checking the shared link…"
        messageLabel.textColor = .white
        messageLabel.font = .preferredFont(forTextStyle: .body)
        messageLabel.numberOfLines = 0
        messageLabel.textAlignment = .center

        doneButton.setTitle("Done", for: .normal)
        doneButton.titleLabel?.font = .preferredFont(forTextStyle: .headline)
        doneButton.tintColor = UIColor(red: 1, green: 0, blue: 51 / 255, alpha: 1)
        doneButton.isHidden = true
        doneButton.addTarget(self, action: #selector(finish), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [messageLabel, doneButton])
        stack.axis = .vertical
        stack.alignment = .fill
        stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            doneButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44)
        ])

        loadSharedVideoURL()
    }

    @objc private func finish() {
        extensionContext?.completeRequest(returningItems: nil)
    }

    private func loadSharedVideoURL() {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }
            .prefix(10)
        load(from: Array(providers), at: 0)
    }

    private func load(from providers: [NSItemProvider], at index: Int) {
        guard index < providers.count else {
            showResult("Share an HTTPS YouTube video link.")
            return
        }
        let provider = providers[index]
        let type: UTType?
        if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            type = .url
        } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            type = .plainText
        } else {
            type = nil
        }
        guard let type else {
            load(from: providers, at: index + 1)
            return
        }

        provider.loadItem(forTypeIdentifier: type.identifier, options: nil) { [weak self] item, _ in
            guard let self else { return }
            let text: String?
            if let url = item as? URL {
                text = url.absoluteString
            } else if let url = item as? NSURL {
                text = url.absoluteString
            } else if let value = item as? String {
                text = value
            } else {
                text = nil
            }
            if let text, let canonical = Self.canonicalVideoURL(in: text) {
                UserDefaults(suiteName: Self.appGroup)?.set(canonical.absoluteString, forKey: Self.pendingURLKey)
                self.showResult("Ready in SpeedyWatch. Open the app to watch this video.")
            } else {
                self.load(from: providers, at: index + 1)
            }
        }
    }

    private func showResult(_ message: String) {
        DispatchQueue.main.async { [weak self] in
            self?.messageLabel.text = message
            self?.doneButton.isHidden = false
        }
    }

    private static func canonicalVideoURL(in text: String) -> URL? {
        let pattern = #"https://[^\s<>\"']+"#
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        let matches = (try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]))?
            .matches(in: text, range: range) ?? []
        let candidates = [text.trimmingCharacters(in: .whitespacesAndNewlines)] + matches.compactMap {
            Range($0.range, in: text).map { String(text[$0]).trimmingCharacters(in: CharacterSet(charactersIn: ".,;:!?)]}")) }
        }
        for candidate in candidates {
            guard let input = URL(string: candidate), input.scheme?.lowercased() == "https",
                  let host = input.host?.lowercased() else { continue }
            var videoID: String?
            let parts = input.path.split(separator: "/").map(String.init)
            if host == "youtu.be" {
                videoID = parts.first
            } else if host == "youtube.com" || host.hasSuffix(".youtube.com") {
                if parts.first == "watch" {
                    videoID = URLComponents(url: input, resolvingAgainstBaseURL: false)?
                        .queryItems?.first(where: { $0.name == "v" })?.value
                } else if ["shorts", "live", "embed", "v", "e"].contains(parts.first ?? "") {
                    videoID = parts.dropFirst().first
                }
            } else if (host == "youtube-nocookie.com" || host.hasSuffix(".youtube-nocookie.com")), parts.first == "embed" {
                videoID = parts.dropFirst().first
            }
            if let videoID, videoID.range(of: #"^[A-Za-z0-9_-]{11}$"#, options: .regularExpression) != nil {
                return URL(string: "https://www.youtube.com/watch?v=\(videoID)")
            }
        }
        return nil
    }
}
