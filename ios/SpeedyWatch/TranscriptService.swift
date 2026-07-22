import Foundation

struct TranscriptService: Sendable {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    @MainActor
    func load(from controller: YouTubeWebController) async throws -> TranscriptPackage {
        guard let pageURL = controller.liveURL,
              let videoID = Self.videoID(from: pageURL) else {
            throw SpeedyWatchError.invalidVideo
        }

        let metadata = try? await controller.captionMetadata()
        let title = ((metadata?["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? controller.pageTitle
        let cookies = await controller.cookies()
        let userAgent = await controller.userAgent()

        if let rawURL = metadata?["baseUrl"] as? String,
           let signedURL = URL(string: rawURL),
           YouTubeURLPolicy.isTrustedCaption(signedURL),
           let entries = try? await downloadCaption(from: signedURL, cookies: cookies, userAgent: userAgent),
           !entries.isEmpty {
            return TranscriptPackage(entries: entries, videoTitle: title, videoURL: pageURL)
        }

        let fallbackURL = try await innerTubeCaptionURL(videoID: videoID)
        let entries = try await downloadCaption(from: fallbackURL, cookies: cookies, userAgent: userAgent)
        guard !entries.isEmpty else { throw SpeedyWatchError.noCaptions }
        return TranscriptPackage(entries: entries, videoTitle: title, videoURL: pageURL)
    }

    static func videoID(from url: URL) -> String? {
        guard YouTubeURLPolicy.isSupportedSource(url), url.path == "/watch",
              let value = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?.first(where: { $0.name == "v" })?.value,
              value.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil else {
            return nil
        }
        return value
    }

    static func parseJSON3(_ data: Data) throws -> [TranscriptEntry] {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let events = object["events"] as? [[String: Any]] else {
            throw SpeedyWatchError.invalidResponse("Caption response contained no events")
        }
        return events.compactMap { event in
            guard let segments = event["segs"] as? [[String: Any]] else { return nil }
            let text = segments.compactMap { $0["utf8"] as? String }.joined()
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { return nil }
            let startMilliseconds = (event["tStartMs"] as? NSNumber)?.doubleValue ?? 0
            let durationMilliseconds = (event["dDurationMs"] as? NSNumber)?.doubleValue ?? 0
            return TranscriptEntry(
                start: startMilliseconds / 1_000,
                duration: durationMilliseconds / 1_000,
                text: text
            )
        }
    }

    private func innerTubeCaptionURL(videoID: String) async throws -> URL {
        let endpoint = URL(string: "https://www.youtube.com/youtubei/v1/player?prettyPrint=false")!
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("com.google.android.youtube/20.10.38 (Linux; U; Android 14)", forHTTPHeaderField: "User-Agent")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "context": ["client": ["clientName": "ANDROID", "clientVersion": "20.10.38"]],
            "videoId": videoID
        ])
        let data = try await boundedData(for: request, maximumBytes: 4 * 1_024 * 1_024)
        guard let response = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SpeedyWatchError.noCaptions
        }
        if let playability = response["playabilityStatus"] as? [String: Any],
           let status = playability["status"] as? String, status != "OK" {
            throw SpeedyWatchError.invalidResponse("YouTube video is unavailable")
        }
        let captions = response["captions"] as? [String: Any]
        let renderer = captions?["playerCaptionsTracklistRenderer"] as? [String: Any]
        let tracks = renderer?["captionTracks"] as? [[String: Any]] ?? []
        guard let track = selectTrack(tracks),
              let rawURL = track["baseUrl"] as? String,
              let url = URL(string: rawURL),
              YouTubeURLPolicy.isTrustedCaption(url) else {
            throw SpeedyWatchError.noCaptions
        }
        return url
    }

    private func selectTrack(_ tracks: [[String: Any]]) -> [String: Any]? {
        guard !tracks.isEmpty else { return nil }
        let deviceLanguage = Locale.current.language.languageCode?.identifier ?? ""
        if let match = tracks.first(where: { language($0, matches: deviceLanguage) }) { return match }
        if let english = tracks.first(where: { language($0, matches: "en") }) { return english }
        return tracks.first(where: { ($0["kind"] as? String) != "asr" }) ?? tracks.first
    }

    private func language(_ track: [String: Any], matches language: String) -> Bool {
        guard !language.isEmpty, let code = (track["languageCode"] as? String)?.lowercased() else { return false }
        let target = language.lowercased()
        return code == target || code.hasPrefix("\(target)-")
    }

    private func downloadCaption(from url: URL, cookies: [HTTPCookie], userAgent: String) async throws -> [TranscriptEntry] {
        guard YouTubeURLPolicy.isTrustedCaption(url) else { throw SpeedyWatchError.invalidCaptionURL }
        var candidates: [URL] = []
        if var components = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            var items = components.queryItems ?? []
            items.removeAll { $0.name == "fmt" }
            items.append(URLQueryItem(name: "fmt", value: "json3"))
            components.queryItems = items
            if let jsonURL = components.url { candidates.append(jsonURL) }
        }
        if !candidates.contains(url) { candidates.append(url) }

        var lastError: Error = SpeedyWatchError.noCaptions
        for candidate in candidates {
            do {
                var request = URLRequest(url: candidate)
                request.timeoutInterval = 30
                request.cachePolicy = .reloadIgnoringLocalCacheData
                request.setValue("application/json,text/plain,*/*", forHTTPHeaderField: "Accept")
                request.setValue("https://www.youtube.com/", forHTTPHeaderField: "Referer")
                request.setValue("https://www.youtube.com", forHTTPHeaderField: "Origin")
                if !userAgent.isEmpty { request.setValue(userAgent, forHTTPHeaderField: "User-Agent") }
                let cookieHeaders = HTTPCookie.requestHeaderFields(with: cookies)
                cookieHeaders.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
                let data = try await boundedData(for: request, maximumBytes: 8 * 1_024 * 1_024)
                let entries = try Self.parseJSON3(data)
                if !entries.isEmpty { return entries }
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private func boundedData(for request: URLRequest, maximumBytes: Int) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        guard data.count <= maximumBytes else {
            throw SpeedyWatchError.invalidResponse("YouTube response exceeded the allowed size")
        }
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw SpeedyWatchError.invalidResponse("YouTube request failed")
        }
        return data
    }
}
