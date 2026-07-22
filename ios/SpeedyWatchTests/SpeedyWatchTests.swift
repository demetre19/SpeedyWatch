import XCTest
@testable import SpeedyWatch

final class SpeedyWatchTests: XCTestCase {
    func testYouTubeURLBoundaries() throws {
        XCTAssertTrue(YouTubeURLPolicy.isAllowedNavigation(try XCTUnwrap(URL(string: "https://m.youtube.com/watch?v=dQw4w9WgXcQ"))))
        XCTAssertTrue(YouTubeURLPolicy.isAllowedNavigation(try XCTUnwrap(URL(string: "https://accounts.google.com/ServiceLogin"))))
        XCTAssertFalse(YouTubeURLPolicy.isAllowedNavigation(try XCTUnwrap(URL(string: "http://youtube.com/watch?v=dQw4w9WgXcQ"))))
        XCTAssertFalse(YouTubeURLPolicy.isAllowedNavigation(try XCTUnwrap(URL(string: "https://youtube.com.example.org/watch?v=dQw4w9WgXcQ"))))
        XCTAssertTrue(YouTubeURLPolicy.isTrustedCaption(try XCTUnwrap(URL(string: "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ"))))
        XCTAssertFalse(YouTubeURLPolicy.isTrustedCaption(try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))))
    }

    func testVideoIDRequiresWatchURLAndElevenSafeCharacters() throws {
        XCTAssertEqual(
            TranscriptService.videoID(from: try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))),
            "dQw4w9WgXcQ"
        )
        XCTAssertNil(TranscriptService.videoID(from: try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=short"))))
        XCTAssertNil(TranscriptService.videoID(from: try XCTUnwrap(URL(string: "https://www.youtube.com/shorts/dQw4w9WgXcQ"))))
    }

    func testJSON3TranscriptParsingNormalizesTextAndTimestamps() throws {
        let data = try JSONSerialization.data(withJSONObject: [
            "events": [
                ["tStartMs": 65_250, "dDurationMs": 2_500, "segs": [["utf8": "Hello  "], ["utf8": "world\nagain"]]],
                ["tStartMs": 70_000]
            ]
        ])
        let entries = try TranscriptService.parseJSON3(data)
        XCTAssertEqual(entries, [TranscriptEntry(start: 65.25, duration: 2.5, text: "Hello world again")])
        XCTAssertEqual(entries[0].timestamp, "1:05")
    }

    func testAIRequestDataContainsOnlyNeutralSourceData() throws {
        let package = TranscriptPackage(
            entries: [TranscriptEntry(start: 3, duration: 1, text: "First fact")],
            videoTitle: "Example",
            videoURL: try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        )
        XCTAssertEqual(AIRequestData.summary(for: package), """
        Source: YouTube Subtitles
        Title: Example
        URL: https://www.youtube.com/watch?v=dQw4w9WgXcQ

        Transcript:
        0:03 First fact
        """)
        XCTAssertEqual(AIRequestData.quiz(for: package, questionCount: 6), """
        Source: YouTube Subtitles
        Title: Example
        URL: https://www.youtube.com/watch?v=dQw4w9WgXcQ
        Requested question count: 6

        Transcript:
        First fact
        """)
    }
    func testFollowUpMessagesPreserveTranscriptAndConversationOrder() throws {
        let package = TranscriptPackage(
            entries: [TranscriptEntry(start: 3, duration: 1, text: "First fact")],
            videoTitle: "Example",
            videoURL: try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        )
        let messages = AIRequestData.followUpMessages(
            for: package,
            summary: "Initial summary",
            turns: [TranscriptChatTurn(question: "What happened first?", answer: "The first fact.")],
            question: "What happened next?"
        )

        XCTAssertEqual(messages, [
            OpenRouterMessage(role: .user, content: AIRequestData.summary(for: package)),
            OpenRouterMessage(role: .assistant, content: "Initial summary"),
            OpenRouterMessage(role: .user, content: "Question:\nWhat happened first?"),
            OpenRouterMessage(role: .assistant, content: "The first fact."),
            OpenRouterMessage(role: .user, content: "Question:\nWhat happened next?")
        ])
    }


    @MainActor
    func testStoredPromptsAndModelAreNotImplicitlySeeded() {
        let suiteName = "SpeedyWatchTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let settings = AppSettings(defaults: defaults)
        XCTAssertEqual(settings.modelID, "")
        XCTAssertEqual(settings.summaryOnePrompt, "")
        XCTAssertEqual(settings.summaryTwoPrompt, "")
        XCTAssertEqual(settings.quizPrompt, "")
    }

    @MainActor
    func testSavedContentPersistsNewestFirstAndSearchesQuizzesAndSummaries() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let fileURL = directory.appendingPathComponent("saved-summaries.json")
        let source = try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        let store = SavedSummaryStore(fileURL: fileURL)
        try store.save(videoTitle: "First", summaryLabel: "Summary One", summaryText: "Alpha heading", sourceURL: source)
        try store.save(videoTitle: "Second", summaryLabel: "Summary Two", summaryText: "Beta body", sourceURL: source)
        try store.save(videoTitle: "Quiz video", summaryLabel: "Quiz · 10 questions", summaryText: "Gamma question", sourceURL: source)
        XCTAssertEqual(store.entries.map(\.videoTitle), ["Quiz video", "Second", "First"])
        XCTAssertEqual(store.filtered(by: "alpha").map(\.videoTitle), ["First"])
        XCTAssertEqual(store.filtered(by: "10 questions").map(\.videoTitle), ["Quiz video"])

        let reopened = SavedSummaryStore(fileURL: fileURL)
        XCTAssertEqual(reopened.entries.count, 3)
        try reopened.delete(try XCTUnwrap(reopened.entries.first))
        XCTAssertEqual(SavedSummaryStore(fileURL: fileURL).entries.map(\.videoTitle), ["Second", "First"])
        try? FileManager.default.removeItem(at: directory)
    }

    @MainActor
    func testFailedSaveDoesNotPublishUnpersistedSummary() throws {
        let blocker = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try Data("not a directory".utf8).write(to: blocker)
        defer { try? FileManager.default.removeItem(at: blocker) }
        let source = try XCTUnwrap(URL(string: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        let store = SavedSummaryStore(fileURL: blocker.appendingPathComponent("saved-summaries.json"))

        XCTAssertThrowsError(
            try store.save(videoTitle: "Example", summaryLabel: "Summary One", summaryText: "Body", sourceURL: source)
        )
        XCTAssertTrue(store.entries.isEmpty)
    }
}
