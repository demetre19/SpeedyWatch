import Foundation
import Security

@MainActor
final class AppSettings: ObservableObject {
    static let preferredModelID = "inception/mercury-2"

    static let defaultSummaryOnePrompt = """
    You are a concise video content summariser. Provide a clear, well-structured summary of the following YouTube video transcript. Include:
    - A brief overview of the video topic (2-3 sentences)
    - Key points as bullet points
    - Any notable conclusions or takeaways

    Keep the summary factual and focused. Do not add opinions or information not present in the transcript.
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

    @Published private(set) var apiKey: String
    @Published private(set) var modelID: String
    @Published private(set) var summaryOnePrompt: String
    @Published private(set) var summaryTwoPrompt: String
    @Published private(set) var quizPrompt: String
    @Published private(set) var defaultPlaybackSpeed: Double

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
        summaryOnePrompt = defaults.string(forKey: "summary_one_prompt") ?? ""
        summaryTwoPrompt = defaults.string(forKey: "summary_two_prompt") ?? ""
        quizPrompt = defaults.string(forKey: "quiz_prompt") ?? ""
        let savedSpeed = defaults.double(forKey: "default_playback_speed")
        defaultPlaybackSpeed = savedSpeed >= 0.25 && savedSpeed <= 4 ? savedSpeed : 1
    }

    func save(
        apiKey: String,
        modelID: String,
        summaryOnePrompt: String,
        summaryTwoPrompt: String,
        quizPrompt: String,
        defaultPlaybackSpeed: Double
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
        self.apiKey = normalizedKey
        self.modelID = normalizedModel
        self.summaryOnePrompt = summaryOnePrompt
        self.summaryTwoPrompt = summaryTwoPrompt
        self.quizPrompt = quizPrompt
        self.defaultPlaybackSpeed = speed
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
            return OpenRouterModel(
                id: id,
                name: rawName.isEmpty ? id : rawName,
                contextLength: item["context_length"] as? Int ?? 0
            )
        }.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
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
