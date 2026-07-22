import XCTest

final class SpeedyWatchUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        let key = try XCTUnwrap(ProcessInfo.processInfo.environment["OPENROUTER_API_KEY"])
        XCTAssertFalse(key.isEmpty)
        XCTAssertFalse(key.contains("$("))
        app = XCUIApplication()
        app.launchEnvironment["SPEEDYWATCH_TEST_URL"] = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        app.launchEnvironment["OPENROUTER_API_KEY"] = key
        app.launch()
    }

    func testCompleteParityFlow() throws {
        XCTAssertTrue(app.buttons["YouTube home"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 30))
        verifyBrowserNavigation()

        app.buttons["2x"].tap()
        XCTAssertTrue(app.staticTexts["Playback speed 2x, ads blocked"].waitForExistence(timeout: 5))
        let customSpeed = app.textFields["Custom playback speed"]
        XCTAssertTrue(customSpeed.waitForExistence(timeout: 5))
        customSpeed.tap()
        customSpeed.tap(withNumberOfTaps: 3, numberOfTouches: 1)
        customSpeed.typeText("2.7")
        XCTAssertEqual(customSpeed.value as? String, "2.7")
        app.buttons["Set custom speed"].tap()
        XCTAssertTrue(app.staticTexts["Playback speed 2.7x, ads blocked"].waitForExistence(timeout: 5))
        app.buttons["Decrease speed by 0.1"].tap()
        XCTAssertTrue(app.staticTexts["Playback speed 2.6x, ads blocked"].waitForExistence(timeout: 5))
        app.buttons["Increase speed by 0.1"].tap()
        XCTAssertTrue(app.staticTexts["Playback speed 2.7x, ads blocked"].waitForExistence(timeout: 5))
        app.buttons["Ads: ON"].tap()
        XCTAssertTrue(app.staticTexts["Playback speed 2.7x, ads allowed"].waitForExistence(timeout: 5))
        app.buttons["Ads: OFF"].tap()

        configureSettings()
        verifyTranscriptSummaryAndSave()
        verifySavedSummary()
        verifyQuiz()
    }
    func testAIChatAndQuizBookmarkFlow() {
        XCTAssertTrue(app.buttons["YouTube home"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 30))
        configureSettings()
        verifyTranscriptSummaryAndSave()
        verifyQuiz()
    }


    private func verifyBrowserNavigation() {
        app.buttons["Reload"].tap()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 20))
        app.buttons["YouTube home"].tap()
        app.buttons["Back"].tap()
        app.buttons["Forward"].tap()
        app.buttons["Back"].tap()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 20))
    }

    private func configureSettings() {
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.textFields["Default speed"].exists)
        XCTAssertTrue(app.buttons["Show API key"].exists)
        app.buttons["Show API key"].tap()
        XCTAssertTrue(app.buttons["Hide API key"].exists)
        app.buttons["Hide API key"].tap()
        let modelStatus = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "text models loaded")).firstMatch
        if !modelStatus.waitForExistence(timeout: 45) {
            app.buttons["Check key and refresh models"].tap()
            XCTAssertTrue(modelStatus.waitForExistence(timeout: 45))
        }
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "key check passed")).firstMatch.exists)
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 8))
    }

    private func verifyTranscriptSummaryAndSave() {
        app.buttons["YouTube subtitles"].tap()
        XCTAssertTrue(app.navigationBars["YouTube Subs"].waitForExistence(timeout: 8))
        let subtitlesStatus = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "subtitles")).firstMatch
        XCTAssertTrue(subtitlesStatus.waitForExistence(timeout: 60))
        XCTAssertTrue(app.searchFields["Search subtitles"].exists)
        app.searchFields["Search subtitles"].tap()
        app.searchFields["Search subtitles"].typeText("never")
        let matchingSubtitle = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "never")).firstMatch
        XCTAssertTrue(matchingSubtitle.waitForExistence(timeout: 8))
        matchingSubtitle.tap()
        XCTAssertTrue(app.buttons["YouTube subtitles"].waitForExistence(timeout: 8))
        app.buttons["YouTube subtitles"].tap()
        XCTAssertTrue(app.navigationBars["YouTube Subs"].waitForExistence(timeout: 8))
        XCTAssertTrue(subtitlesStatus.waitForExistence(timeout: 60))

        app.buttons["Summary One"].tap()
        XCTAssertTrue(app.buttons["Copy summary"].waitForExistence(timeout: 180))
        let transcriptQuestion = app.textFields["Transcript question"]
        XCTAssertTrue(transcriptQuestion.waitForExistence(timeout: 8))
        transcriptQuestion.tap()
        transcriptQuestion.typeText("What is the first main point?")
        app.buttons["transcript-chat-send"].tap()
        let chatStatus = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "chat")).firstMatch
        XCTAssertTrue(chatStatus.waitForExistence(timeout: 180))
        app.buttons["Save summary"].tap()
        XCTAssertTrue(app.alerts["SpeedyWatch"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.alerts["SpeedyWatch"].staticTexts["Summary saved"].exists)
        app.alerts["SpeedyWatch"].buttons["OK"].tap()
        app.buttons["Transcript"].tap()
        app.buttons["Summary Two"].tap()
        XCTAssertTrue(app.buttons["Copy summary"].waitForExistence(timeout: 180))
        app.buttons["Close"].tap()
    }

    private func verifySavedSummary() {
        app.buttons["Saved summaries and quizzes"].tap()
        XCTAssertTrue(app.navigationBars["Saved"].waitForExistence(timeout: 8))
        app.searchFields["Search saved content"].tap()
        app.searchFields["Search saved content"].typeText("Summary One")
        let saved = app.cells.firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 8))
        saved.tap()
        XCTAssertTrue(app.buttons["Open original video"].waitForExistence(timeout: 8))
        app.buttons["Open original video"].tap()
        XCTAssertTrue(app.buttons["Saved summaries and quizzes"].waitForExistence(timeout: 8))
    }

    private func verifyQuiz() {
        app.buttons["Create video quiz"].tap()
        XCTAssertTrue(app.navigationBars["Quiz"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.buttons["6 questions"].waitForExistence(timeout: 60))
        XCTAssertTrue(app.buttons["10 questions"].exists)
        XCTAssertTrue(app.buttons["12 questions"].exists)
        XCTAssertTrue(app.buttons["20 questions"].exists)
        app.buttons["6 questions"].tap()
        app.buttons["Create 6 questions"].tap()
        let completeStatus = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "6 pre-watch questions")).firstMatch
        XCTAssertTrue(completeStatus.waitForExistence(timeout: 180))
        XCTAssertTrue(app.buttons["Save quiz"].waitForExistence(timeout: 8))
        app.buttons["Save quiz"].tap()
        XCTAssertTrue(app.alerts["SpeedyWatch"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.alerts["SpeedyWatch"].staticTexts["Quiz saved"].exists)
        app.alerts["SpeedyWatch"].buttons["OK"].tap()
        app.buttons["Close"].tap()
        app.buttons["Saved summaries and quizzes"].tap()
        XCTAssertTrue(app.navigationBars["Saved"].waitForExistence(timeout: 8))
        let savedSearch = app.searchFields["Search saved content"]
        XCTAssertTrue(savedSearch.waitForExistence(timeout: 8))
        savedSearch.tap()
        savedSearch.typeText("Quiz · 6 questions")
        let savedQuiz = app.cells.firstMatch
        XCTAssertTrue(savedQuiz.waitForExistence(timeout: 8))
        savedQuiz.tap()
        XCTAssertTrue(app.buttons["Open original video"].waitForExistence(timeout: 8))
    }
}
