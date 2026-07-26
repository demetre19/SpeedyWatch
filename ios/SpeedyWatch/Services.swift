import Foundation
import Security
import CryptoKit

@MainActor
final class AppSettings: ObservableObject {
    static let preferredModelID = "inception/mercury-2"

    static let defaultSummaryOnePrompt = """
    You are a concise video content summariser. Provide a clear, well-structured summary of the following YouTube video transcript. Include:
    - A brief overview of the video topic (2-3 sentences)
    - Key points as bullet points
    - Any notable conclusions or takeaways

    This should be a detailed and through overview. With insightful and in-depth commentary and how it can be utilised in a business setting, do NOT include timestamps.

    Keep the summary factual and focused. Do not add opinions or information not present in the transcript.

    Do NOT create tables. Make nice readable heading:text paragraphs. Must be left aligned

    Give me step by step.instructions on how to implement it in a business. Be as detailed as possible.
    """

    static let defaultSummaryTwoPrompt = """
    You are a concise video content summariser. Provide a clear, well-structured summary of the following YouTube video transcript. Include:
    - A brief overview of the video topic (2-3 sentences)
    - Key points as bullet points
    - Any notable conclusions or takeaways

    Provide a detailed and thorough overview with insightful, in-depth commentary on how the ideas in the video can be utilised in a business setting. Ground every insight and business application in the transcript.

    Keep the summary factual and focused. Do not add opinions or information not present in the transcript.
    """

    static let defaultQuizPrompt = """
    You are a study tutor preparing a reader before they study a source. Use only facts, terms, and concepts present in the source. Return exactly the Requested question count from the request data as important pre-watch questions in Markdown. For each item, use a numbered heading for the question, then one short description explaining why the question matters and what the viewer should listen for. Do not answer the questions or include a summary, glossary, introduction, or conclusion.
    """

    private static let legacySummaryOnePrompt = """
    You are a concise video content summariser. Provide a clear, well-structured summary of the following YouTube video transcript. Include:
    - A brief overview of the video topic (2-3 sentences)
    - Key points as bullet points
    - Any notable conclusions or takeaways

    Keep the summary factual and focused. Do not add opinions or information not present in the transcript.
    """

    @Published private(set) var apiKey: String
    @Published private(set) var modelID: String
    @Published private(set) var summaryOnePrompt: String
    @Published private(set) var summaryTwoPrompt: String
    @Published private(set) var quizPrompt: String
    @Published private(set) var defaultPlaybackSpeed: Double
    @Published private(set) var playbackProfile: PlaybackProfile
    @Published private(set) var adaptiveSpeedEnabled: Bool
    @Published private(set) var sponsorBlockEnabled: Bool
    @Published private(set) var sponsorCategoryEnabled: Bool
    @Published private(set) var selfPromotionCategoryEnabled: Bool
    @Published private(set) var interactionCategoryEnabled: Bool

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let storedKey = (try? KeychainStore.read()) ?? ""
#if DEBUG
        let debugKey = ProcessInfo.processInfo.environment["OPENROUTER_API_KEY"] ?? ""
        apiKey = debugKey.isEmpty ? storedKey : debugKey
#else
        apiKey = storedKey
#endif
        modelID = defaults.string(forKey: "openrouter_model_id") ?? ""
        let storedSummaryOne = defaults.string(forKey: "summary_one_prompt") ?? ""
        summaryOnePrompt = storedSummaryOne == Self.legacySummaryOnePrompt
            ? Self.defaultSummaryOnePrompt
            : storedSummaryOne
        if storedSummaryOne == Self.legacySummaryOnePrompt {
            defaults.set(Self.defaultSummaryOnePrompt, forKey: "summary_one_prompt")
        }
        let storedSummaryTwo = defaults.string(forKey: "summary_two_prompt") ?? ""
        summaryTwoPrompt = storedSummaryTwo.isEmpty ? Self.defaultSummaryTwoPrompt : storedSummaryTwo
        let storedQuiz = defaults.string(forKey: "quiz_prompt") ?? ""
        quizPrompt = storedQuiz.isEmpty ? Self.defaultQuizPrompt : storedQuiz
        let savedSpeed = defaults.double(forKey: "default_playback_speed")
        defaultPlaybackSpeed = savedSpeed >= 0.25 && savedSpeed <= 4 ? savedSpeed : 1
        playbackProfile = PlaybackProfile(
            rawValue: defaults.string(forKey: "playback_profile") ?? "") ?? .normal
        adaptiveSpeedEnabled = defaults.bool(forKey: "adaptive_speed_enabled")
        sponsorBlockEnabled = defaults.bool(forKey: "sponsorblock_enabled")
        sponsorCategoryEnabled = defaults.object(forKey: "sponsorblock_sponsor") as? Bool ?? true
        selfPromotionCategoryEnabled =
            defaults.object(forKey: "sponsorblock_self_promotion") as? Bool ?? true
        interactionCategoryEnabled =
            defaults.object(forKey: "sponsorblock_interaction") as? Bool ?? false
    }

    func save(
        apiKey: String,
        modelID: String,
        summaryOnePrompt: String,
        summaryTwoPrompt: String,
        quizPrompt: String,
        defaultPlaybackSpeed: Double,
        playbackProfile: PlaybackProfile,
        adaptiveSpeedEnabled: Bool
    ) throws {
        let normalizedKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedModel = modelID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !summaryOnePrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SpeedyWatchError.emptyPrompt("Summary One")
        }
        guard !summaryTwoPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SpeedyWatchError.emptyPrompt("Summary Two")
        }
        guard !quizPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw SpeedyWatchError.emptyPrompt("Quiz")
        }
        let speed = min(4, max(0.25, defaultPlaybackSpeed))
        try KeychainStore.write(normalizedKey)
        defaults.set(normalizedModel, forKey: "openrouter_model_id")
        defaults.set(summaryOnePrompt, forKey: "summary_one_prompt")
        defaults.set(summaryTwoPrompt, forKey: "summary_two_prompt")
        defaults.set(quizPrompt, forKey: "quiz_prompt")
        defaults.set(speed, forKey: "default_playback_speed")
        defaults.set(playbackProfile.rawValue, forKey: "playback_profile")
        defaults.set(adaptiveSpeedEnabled, forKey: "adaptive_speed_enabled")
        self.apiKey = normalizedKey
        self.modelID = normalizedModel
        self.summaryOnePrompt = summaryOnePrompt
        self.summaryTwoPrompt = summaryTwoPrompt
        self.quizPrompt = quizPrompt
        self.defaultPlaybackSpeed = speed
        self.playbackProfile = playbackProfile
        self.adaptiveSpeedEnabled = adaptiveSpeedEnabled
    }

    func applyPlaybackProfile(_ profile: PlaybackProfile) {
        defaults.set(profile.rawValue, forKey: "playback_profile")
        defaults.set(profile.speed, forKey: "default_playback_speed")
        playbackProfile = profile
        defaultPlaybackSpeed = profile.speed
    }

    func setAdaptiveSpeedEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: "adaptive_speed_enabled")
        adaptiveSpeedEnabled = enabled
    }

    func setSponsorBlock(
        enabled: Bool,
        sponsor: Bool,
        selfPromotion: Bool,
        interaction: Bool
    ) {
        defaults.set(enabled, forKey: "sponsorblock_enabled")
        defaults.set(sponsor, forKey: "sponsorblock_sponsor")
        defaults.set(selfPromotion, forKey: "sponsorblock_self_promotion")
        defaults.set(interaction, forKey: "sponsorblock_interaction")
        sponsorBlockEnabled = enabled
        sponsorCategoryEnabled = sponsor
        selfPromotionCategoryEnabled = selfPromotion
        interactionCategoryEnabled = interaction
    }

    func restoreBackup(
        modelID: String,
        summaryOnePrompt: String,
        summaryTwoPrompt: String,
        quizPrompt: String,
        defaultPlaybackSpeed: Double,
        playbackProfile: PlaybackProfile,
        adaptiveSpeedEnabled: Bool
    ) throws {
        let model = modelID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard model.count <= 300,
              !summaryOnePrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !summaryTwoPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !quizPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              summaryOnePrompt.count <= 100_000,
              summaryTwoPrompt.count <= 100_000,
              quizPrompt.count <= 100_000,
              defaultPlaybackSpeed >= 0.25,
              defaultPlaybackSpeed <= 4 else {
            throw SpeedyWatchError.persistence("Backup settings are invalid")
        }
        defaults.set(model, forKey: "openrouter_model_id")
        defaults.set(summaryOnePrompt, forKey: "summary_one_prompt")
        defaults.set(summaryTwoPrompt, forKey: "summary_two_prompt")
        defaults.set(quizPrompt, forKey: "quiz_prompt")
        defaults.set(defaultPlaybackSpeed, forKey: "default_playback_speed")
        defaults.set(playbackProfile.rawValue, forKey: "playback_profile")
        defaults.set(adaptiveSpeedEnabled, forKey: "adaptive_speed_enabled")
        self.modelID = model
        self.summaryOnePrompt = summaryOnePrompt
        self.summaryTwoPrompt = summaryTwoPrompt
        self.quizPrompt = quizPrompt
        self.defaultPlaybackSpeed = defaultPlaybackSpeed
        self.playbackProfile = playbackProfile
        self.adaptiveSpeedEnabled = adaptiveSpeedEnabled
    }

    var apiKeyPreview: String {
        guard !apiKey.isEmpty else { return "Not configured" }
        guard apiKey.count > 10 else { return String(repeating: "•", count: apiKey.count) }
        return "\(apiKey.prefix(5))••••\(apiKey.suffix(4))"
    }
}

enum KeychainStore {
    private static let service = "com.speedywatch.ios"
    private static let account = "openrouter-api-key"

    static func read() throws -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw SpeedyWatchError.persistence("Stored API key could not be read")
        }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ value: String) throws {
        let identity: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(identity as CFDictionary)
        guard !value.isEmpty else { return }
        var item = identity
        item[kSecValueData as String] = Data(value.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(item as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw SpeedyWatchError.persistence("API key could not be stored securely")
        }
    }
}

struct SponsorBlockSegment: Sendable {
    let start: Double
    let end: Double
    let category: Int
}

struct SponsorBlockClient: Sendable {
    private struct Match: Decodable {
        let videoID: String
        let segments: [Segment]
    }

    private struct Segment: Decodable {
        let segment: [Double]
        let category: String
        let actionType: String?
    }

    func fetch(videoID: String, categories: Set<String>) async throws -> [SponsorBlockSegment] {
        guard videoID.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil,
              !categories.isEmpty else {
            return []
        }
        let digest = SHA256.hash(data: Data(videoID.utf8))
        let prefix = digest.prefix(2).map { String(format: "%02x", $0) }.joined()
        var components = URLComponents()
        components.scheme = "https"
        components.host = "sponsor.ajay.app"
        components.path = "/api/skipSegments/\(prefix)"
        components.queryItems = [
            URLQueryItem(name: "actionType", value: "skip"),
            URLQueryItem(name: "trimUUIDs", value: "true")
        ] + categories.sorted().map { URLQueryItem(name: "category", value: $0) }
        guard let url = components.url else {
            throw SpeedyWatchError.invalidResponse("SponsorBlock request could not be created")
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("SpeedyWatch/iOS", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard data.count <= 1_024 * 1_024,
              let http = response as? HTTPURLResponse else {
            throw SpeedyWatchError.invalidResponse("SponsorBlock response was invalid")
        }
        if http.statusCode == 404 {
            return []
        }
        guard http.statusCode == 200 else {
            throw SpeedyWatchError.invalidResponse("SponsorBlock lookup failed")
        }
        let matches = try JSONDecoder().decode([Match].self, from: data)
        return matches.first(where: { $0.videoID == videoID })?.segments.prefix(500).compactMap { item in
            guard item.segment.count >= 2,
                  (item.actionType ?? "skip") == "skip",
                  categories.contains(item.category),
                  let category = categoryCode(item.category) else {
                return nil
            }
            let start = item.segment[0]
            let end = item.segment[1]
            guard start.isFinite, end.isFinite, start >= 0, end > start, end <= 604_800 else {
                return nil
            }
            return SponsorBlockSegment(start: start, end: end, category: category)
        } ?? []
    }

    private func categoryCode(_ category: String) -> Int? {
        switch category {
        case "sponsor": 0
        case "selfpromo": 1
        case "interaction": 2
        default: nil
        }
    }
}

struct OpenRouterClient: Sendable {
    private let modelsURL = URL(string: "https://openrouter.ai/api/v1/models")!
    private let chatURL = URL(string: "https://openrouter.ai/api/v1/chat/completions")!
    private let maximumResponseBytes = 16 * 1_024 * 1_024

    func fetchModels(apiKey: String) async throws -> [OpenRouterModel] {
        var request = URLRequest(url: modelsURL)
        request.timeoutInterval = 30
        configure(&request, apiKey: apiKey)
        let object = try await send(request)
        guard let data = object["data"] as? [[String: Any]] else {
            throw SpeedyWatchError.invalidResponse("OpenRouter returned no model catalog")
        }
        return data.compactMap { item in
            guard supportsTextOutput(item), let id = (item["id"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else {
                return nil
            }
            let rawName = (item["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let pricing = item["pricing"] as? [String: Any]
            return OpenRouterModel(
                id: id,
                name: rawName.isEmpty ? id : rawName,
                contextLength: item["context_length"] as? Int ?? 0,
                promptPrice: validPrice(pricing?["prompt"]),
                completionPrice: validPrice(pricing?["completion"])
            )
        }.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }
    private func validPrice(_ value: Any?) -> Double? {
        let parsed: Double?
        if let number = value as? NSNumber {
            parsed = number.doubleValue
        } else if let text = value as? String {
            parsed = Double(text)
        } else {
            parsed = nil
        }
        guard let parsed, parsed.isFinite, parsed >= 0 else { return nil }
        return parsed
    }

    func generate(apiKey: String, modelID: String, systemPrompt: String, userMessage: String) async throws -> String {
        try await generate(
            apiKey: apiKey,
            modelID: modelID,
            systemPrompt: systemPrompt,
            messages: [OpenRouterMessage(role: .user, content: userMessage)]
        )
    }

    func generate(
        apiKey: String,
        modelID: String,
        systemPrompt: String,
        messages: [OpenRouterMessage]
    ) async throws -> String {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let model = modelID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, !model.isEmpty else { throw SpeedyWatchError.missingConfiguration }
        guard !messages.isEmpty else {
            throw SpeedyWatchError.invalidResponse("OpenRouter request has no messages")
        }
        var request = URLRequest(url: chatURL)
        request.httpMethod = "POST"
        request.timeoutInterval = 120
        configure(&request, apiKey: key)
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "model": model,
            "max_tokens": 4_096,
            "temperature": 0.7,
            "messages": [
                ["role": "system", "content": systemPrompt]
            ] + messages.map { ["role": $0.role.rawValue, "content": $0.content] }
        ])
        let object = try await send(request)
        guard let choices = object["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any] else {
            throw SpeedyWatchError.invalidResponse("OpenRouter returned no result")
        }
        let content: String
        if let value = message["content"] as? String {
            content = value
        } else if let parts = message["content"] as? [[String: Any]] {
            content = parts.compactMap { part in
                part["type"] as? String == "text" ? part["text"] as? String : nil
            }.joined()
        } else {
            content = ""
        }
        let normalized = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else {
            throw SpeedyWatchError.invalidResponse("OpenRouter returned an empty result")
        }
        return normalized
    }

    private func configure(_ request: inout URLRequest, apiKey: String) {
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("SpeedyWatch", forHTTPHeaderField: "X-OpenRouter-Title")
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if !key.isEmpty { request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization") }
    }

    private func send(_ request: URLRequest) async throws -> [String: Any] {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard data.count <= maximumResponseBytes else {
            throw SpeedyWatchError.invalidResponse("Response exceeded the allowed size")
        }
        guard let http = response as? HTTPURLResponse else {
            throw SpeedyWatchError.invalidResponse("OpenRouter returned an invalid response")
        }
        let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        if !(200..<300).contains(http.statusCode) || object["error"] != nil {
            let error = object["error"] as? [String: Any]
            let message = error?["message"] as? String
            throw SpeedyWatchError.invalidResponse(message?.isEmpty == false ? message! : "OpenRouter request failed (HTTP \(http.statusCode))")
        }
        return object
    }

    private func supportsTextOutput(_ model: [String: Any]) -> Bool {
        guard let architecture = model["architecture"] as? [String: Any] else { return true }
        if let outputs = architecture["output_modalities"] as? [String] {
            return outputs.contains { $0.caseInsensitiveCompare("text") == .orderedSame }
        }
        let modality = architecture["modality"] as? String ?? ""
        return modality.isEmpty || modality.hasSuffix("->text")
    }
}

@MainActor
final class SavedSummaryStore: ObservableObject {
    @Published private(set) var entries: [SavedSummary] = []
    @Published private(set) var loadError: String?

    private let fileURL: URL

    init(fileURL: URL? = nil) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            self.fileURL = support.appendingPathComponent("SpeedyWatch/saved-summaries.json")
        }
        load()
    }

    func save(videoTitle: String, summaryLabel: String, summaryText: String, sourceURL: URL) throws {
        let title = videoTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let label = summaryLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        let text = summaryText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, !label.isEmpty, !text.isEmpty else {
            throw SpeedyWatchError.persistence("Saved item details are incomplete")
        }
        guard YouTubeURLPolicy.isSupportedSource(sourceURL) else {
            throw SpeedyWatchError.persistence("Original YouTube URL is unavailable")
        }
        var updated = entries
        updated.insert(SavedSummary(
            id: UUID(), videoTitle: title, summaryLabel: label,
            summaryText: text, sourceURL: sourceURL, createdAt: Date()
        ), at: 0)
        try persist(updated)
        entries = updated
    }

    func delete(_ entry: SavedSummary) throws {
        let updated = entries.filter { $0.id != entry.id }
        try persist(updated)
        entries = updated
    }

    func replaceAll(_ restored: [SavedSummary]) throws {
        guard restored.count <= 10_000,
              restored.allSatisfy({
                  !$0.videoTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                      && !$0.summaryLabel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                      && !$0.summaryText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                      && $0.videoTitle.count <= 2_000
                      && $0.summaryLabel.count <= 500
                      && $0.summaryText.count <= 1_000_000
                      && YouTubeURLPolicy.isSupportedSource($0.sourceURL)
              }) else {
            throw SpeedyWatchError.persistence("Backup saved items are invalid")
        }
        let sorted = restored.sorted { $0.createdAt > $1.createdAt }
        try persist(sorted)
        entries = sorted
    }

    func filtered(by query: String) -> [SavedSummary] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return needle.isEmpty ? entries : entries.filter { $0.searchText.contains(needle) }
    }

    private func load() {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        do {
            entries = try JSONDecoder().decode([SavedSummary].self, from: Data(contentsOf: fileURL))
                .sorted { $0.createdAt > $1.createdAt }
        } catch {
            loadError = "Saved items could not be loaded"
        }
    }

    private func persist(_ updated: [SavedSummary]) throws {
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.sortedKeys]
            try encoder.encode(updated).write(to: fileURL, options: .atomic)
        } catch {
            throw SpeedyWatchError.persistence("Saved items could not be updated")
        }
    }
}
