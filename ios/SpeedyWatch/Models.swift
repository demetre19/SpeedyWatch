import Foundation

struct TranscriptEntry: Codable, Hashable, Identifiable, Sendable {
    let start: TimeInterval
    let duration: TimeInterval
    let text: String

    var id: String { "\(start)-\(text)" }

    var timestamp: String {
        let total = max(0, Int(start))
        let hours = total / 3_600
        let minutes = (total % 3_600) / 60
        let seconds = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, seconds)
            : String(format: "%d:%02d", minutes, seconds)
    }
}

struct TranscriptPackage: Sendable {
    let entries: [TranscriptEntry]
    let videoTitle: String
    let videoURL: URL
}

struct CaptionTrackOption: Identifiable, Hashable, Sendable {
    let languageCode: String
    let displayName: String
    let isAutomatic: Bool

    var id: String { "\(languageCode)-\(isAutomatic)" }
}

enum PlaybackProfile: String, CaseIterable, Codable, Identifiable, Sendable {
    case normal
    case careful
    case lecture
    case podcast

    var id: Self { self }

    var displayName: String {
        switch self {
        case .normal: "Normal"
        case .careful: "Careful"
        case .lecture: "Lecture"
        case .podcast: "Podcast"
        }
    }

    var speed: Double {
        switch self {
        case .normal: 1
        case .careful: 0.8
        case .lecture: 1.5
        case .podcast: 2
        }
    }
}

struct OpenRouterModel: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let contextLength: Int
    let promptPrice: Double?
    let completionPrice: Double?

    var displayName: String { name.isEmpty ? id : name }
    var isFree: Bool { promptPrice == 0 && completionPrice == 0 }
    var hasLongContext: Bool { contextLength >= 100_000 }
    var contextLabel: String {
        guard contextLength > 0 else { return "Context unknown" }
        if contextLength >= 1_000_000 {
            return "\(formatted(contextLength.doubleValue / 1_000_000))M context"
        }
        return "\(Int((Double(contextLength) / 1_000).rounded()))K context"
    }
    var pricingLabel: String {
        guard let promptPrice, let completionPrice else { return "Pricing unavailable" }
        if isFree { return "Free" }
        return "$\(formatted(promptPrice * 1_000_000))/M input • $\(formatted(completionPrice * 1_000_000))/M output"
    }
    var guidance: String { "\(contextLabel) • \(pricingLabel)" }
    var searchText: String {
        "\(name) \(id) \(guidance) \(isFree ? "free" : "paid")".lowercased()
    }

    private func formatted(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...4)))
    }
}

private extension Int {
    var doubleValue: Double { Double(self) }
}

struct OpenRouterMessage: Equatable, Sendable {
    enum Role: String, Sendable {
        case user, assistant
    }

    let role: Role
    let content: String
}

struct TranscriptChatTurn: Identifiable, Equatable, Sendable {
    let id = UUID()
    let question: String
    let answer: String
}

struct SavedSummary: Codable, Hashable, Identifiable, Sendable {
    let id: UUID
    let videoTitle: String
    let summaryLabel: String
    let summaryText: String
    let sourceURL: URL
    let createdAt: Date

    var searchText: String {
        "\(videoTitle) \(summaryLabel) \(summaryText)".lowercased()
    }
}

struct TextSharePayload {
    let subject: String
    let text: String

    static func make(
        videoTitle: String,
        contentLabel: String,
        content: String,
        sourceURL: URL
    ) -> TextSharePayload? {
        let value = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, YouTubeURLPolicy.isSupportedSource(sourceURL) else { return nil }

        let trimmedTitle = videoTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let title = trimmedTitle.isEmpty ? "YouTube Video" : trimmedTitle
        let label = contentLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        let subject = label.isEmpty ? title : "\(title) - \(label)"
        let text = "\(subject)\n\n\(value)\n\nOriginal URL:\n\(sourceURL.absoluteString)"
        return TextSharePayload(subject: subject, text: text)
    }
}

enum SpeedyWatchError: LocalizedError {
    case invalidVideo
    case noCaptions
    case invalidCaptionURL
    case invalidResponse(String)
    case missingConfiguration
    case emptyPrompt(String)
    case persistence(String)

    var errorDescription: String? {
        switch self {
        case .invalidVideo:
            return "Open a YouTube video first"
        case .noCaptions:
            return "No subtitles are available for this video"
        case .invalidCaptionURL:
            return "YouTube returned an invalid caption URL"
        case .invalidResponse(let message):
            return message
        case .missingConfiguration:
            return "Configure OpenRouter in Settings first"
        case .emptyPrompt(let label):
            return "\(label) prompt is empty"
        case .persistence(let message):
            return message
        }
    }
}

enum SpeedFormatting {
    static func value(_ rate: Double) -> String {
        if rate.rounded() == rate { return String(format: "%.0f", rate) }
        if (rate * 10).rounded() == rate * 10 { return String(format: "%.1f", rate) }
        return String(format: "%.2f", rate)
    }

    static func rate(_ rate: Double) -> String { "\(value(rate))x" }
}

enum AIRequestData {
    static func summary(for transcript: TranscriptPackage) -> String {
        let body = transcript.entries.map { "\($0.timestamp) \($0.text)" }.joined(separator: "\n")
        return """
        Source: YouTube Subtitles
        Title: \(transcript.videoTitle)
        URL: \(transcript.videoURL.absoluteString)

        Transcript:
        \(body)
        """
    }

    static func quiz(for transcript: TranscriptPackage, questionCount: Int) -> String {
        let body = transcript.entries.map(\.text).joined(separator: "\n")
        return """
        Source: YouTube Subtitles
        Title: \(transcript.videoTitle)
        URL: \(transcript.videoURL.absoluteString)
        Requested question count: \(questionCount)

        Transcript:
        \(body)
        """
    }
    static func followUpMessages(
        for transcript: TranscriptPackage,
        summary: String,
        turns: [TranscriptChatTurn],
        question: String
    ) -> [OpenRouterMessage] {
        var messages = [
            OpenRouterMessage(role: .user, content: self.summary(for: transcript)),
            OpenRouterMessage(role: .assistant, content: summary)
        ]
        for turn in turns {
            messages.append(OpenRouterMessage(role: .user, content: "Question:\n\(turn.question)"))
            messages.append(OpenRouterMessage(role: .assistant, content: turn.answer))
        }
        messages.append(OpenRouterMessage(role: .user, content: "Question:\n\(question)"))
        return messages
    }

}

enum YouTubeURLPolicy {
    static func isAllowedNavigation(_ url: URL) -> Bool {
        if url.scheme == "about" { return true }
        guard url.scheme?.lowercased() == "https", let host = url.host?.lowercased() else {
            return false
        }
        return host == "youtube.com"
            || host.hasSuffix(".youtube.com")
            || host == "youtu.be"
            || host == "accounts.google.com"
            || host == "consent.google.com"
    }

    static func isSupportedSource(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "https", let host = url.host?.lowercased() else {
            return false
        }
        return host == "youtube.com" || host.hasSuffix(".youtube.com") || host == "youtu.be"
    }

    static func canonicalVideoURL(from url: URL) -> URL? {
        guard url.scheme?.lowercased() == "https", let host = url.host?.lowercased() else {
            return nil
        }
        let segments = url.path.split(separator: "/").map(String.init)
        var videoID: String?
        if host == "youtu.be" {
            videoID = segments.first
        } else if host == "youtube.com" || host.hasSuffix(".youtube.com") {
            switch segments.first {
            case "watch":
                videoID = queryValue("v", in: url)
            case "shorts", "live", "embed", "v", "e":
                videoID = segments.dropFirst().first
            case "attribution_link":
                if let path = queryValue("u", in: url),
                   let nested = URL(string: "https://www.youtube.com\(path)") {
                    return canonicalVideoURL(from: nested)
                }
            default:
                break
            }
        } else if (host == "youtube-nocookie.com" || host.hasSuffix(".youtube-nocookie.com")),
                  segments.first == "embed" {
            videoID = segments.dropFirst().first
        }
        guard let videoID,
              videoID.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil else {
            return nil
        }
        return URL(string: "https://www.youtube.com/watch?v=\(videoID)")
    }

    private static func queryValue(_ name: String, in url: URL) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == name })?
            .value
    }

    static func isTrustedCaption(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "https", let host = url.host?.lowercased() else {
            return false
        }
        return (host == "youtube.com" || host.hasSuffix(".youtube.com"))
            && url.path.hasPrefix("/api/timedtext")
    }
}
