import SwiftUI
import UIKit

private struct MarkdownText: View {
    let value: String

    var body: some View {
        if let attributed = try? AttributedString(markdown: value) {
            Text(attributed)
                .textSelection(.enabled)
        } else {
            Text(value)
                .textSelection(.enabled)
        }
    }
}

struct TranscriptView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var summaries: SavedSummaryStore
    @EnvironmentObject private var webController: YouTubeWebController

    @State private var transcript: TranscriptPackage?
    @State private var query = ""
    @State private var loading = true
    @State private var status = "Loading subtitles…"
    @State private var summaryText = ""
    @State private var summaryLabel = ""
    @State private var summaryPrompt = ""
    @State private var chatQuestion = ""
    @State private var chatTurns: [TranscriptChatTurn] = []
    @State private var generating = false
    @State private var alertMessage: String?
    @State private var captionOptions: [CaptionTrackOption] = []
    @State private var selectedTrackID: String?
    @State private var paragraphMode = false
    @State private var followPlayback = false
    @State private var playbackTime: TimeInterval?

    private let service = TranscriptService()
    private let client = OpenRouterClient()

    private var readingEntries: [TranscriptEntry] {
        guard let entries = transcript?.entries else { return [] }
        return paragraphMode ? paragraphEntries(from: entries) : entries
    }

    private var filteredEntries: [TranscriptEntry] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return needle.isEmpty
            ? readingEntries
            : readingEntries.filter {
                "\($0.timestamp) \($0.text)".localizedCaseInsensitiveContains(needle)
            }
    }

    private var activeEntryID: TranscriptEntry.ID? {
        guard let playbackTime else { return nil }
        for (index, entry) in readingEntries.enumerated() {
            let nextStart = index + 1 < readingEntries.count
                ? readingEntries[index + 1].start
                : entry.start + max(entry.duration, 5)
            if playbackTime >= entry.start && playbackTime < nextStart {
                return entry.id
            }
        }
        return nil
    }

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ContentUnavailableView {
                        Label("Loading subtitles", systemImage: "captions.bubble")
                    } description: {
                        Text("SpeedyWatch is finding the caption track for this video.")
                    } actions: {
                        ProgressView()
                    }
                } else if transcript == nil {
                    ContentUnavailableView("No subtitles", systemImage: "captions.bubble", description: Text(status))
                } else if !summaryText.isEmpty {
                    summaryContent
                } else {
                    transcriptContent
                }
            }
            .background(Color.speedyBackground)
            .navigationTitle("YouTube Subs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if transcript != nil { summaryActions }
            }
            .task { await loadTranscript() }
            .task(id: followPlayback) { await followCurrentCaption() }
            .alert("SpeedyWatch", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) {
                Button("OK", role: .cancel) { alertMessage = nil }
            } message: {
                Text(alertMessage ?? "")
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private var transcriptContent: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Menu {
                        Button("Auto") {
                            Task { await loadTranscript() }
                            selectedTrackID = nil
                        }
                        ForEach(captionOptions) { option in
                            Button {
                                selectedTrackID = option.id
                                Task { await loadTranscript(preferredTrackID: option.id) }
                            } label: {
                                Text(option.displayName + (option.isAutomatic ? " (auto-generated)" : ""))
                            }
                        }
                    } label: {
                        Label(selectedLanguageName, systemImage: "globe")
                    }
                    .disabled(captionOptions.isEmpty)

                    Button {
                        paragraphMode.toggle()
                    } label: {
                        Label(paragraphMode ? "Paragraphs" : "Lines", systemImage: "text.alignleft")
                    }

                    Button {
                        followPlayback.toggle()
                    } label: {
                        Label("Follow", systemImage: followPlayback ? "location.fill" : "location")
                    }
                    .tint(followPlayback ? Color.speedyAccent : nil)

                    Button {
                        UIPasteboard.general.string = transcript?.entries
                            .map { "\($0.timestamp) \($0.text)" }
                            .joined(separator: "\n")
                        alertMessage = "Transcript copied"
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                    }
                }
                .buttonStyle(.bordered)
                .padding(.horizontal)
                .padding(.vertical, 8)
            }

            ScrollViewReader { proxy in
                List(filteredEntries) { entry in
                    Button {
                        webController.seek(to: entry.start)
                        dismiss()
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Text(entry.timestamp)
                                .font(.caption.monospacedDigit().weight(.semibold))
                                .foregroundStyle(Color.speedyAccent)
                                .frame(width: 52, alignment: .leading)
                            Text(entry.text)
                                .foregroundStyle(.primary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Seeks the video to \(entry.timestamp) and closes subtitles")
                    .listRowBackground(entry.id == activeEntryID ? Color.speedyAccent.opacity(0.18) : Color.speedyBackground)
                }
                .listStyle(.plain)
                .searchable(text: $query, prompt: "Search subtitles")
                .overlay(alignment: .bottom) {
                    Text("\(filteredEntries.count) of \(readingEntries.count) \(paragraphMode ? "paragraphs" : "subtitles")")
                        .font(.caption)
                        .foregroundStyle(Color.speedyMuted)
                        .padding(8)
                        .background(.black.opacity(0.8), in: Capsule())
                        .padding(.bottom, 8)
                }
                .onChange(of: activeEntryID) { _, entryID in
                    guard followPlayback, query.isEmpty, let entryID else { return }
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(entryID, anchor: .center)
                    }
                }
            }
        }
    }

    private var summaryContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(summaryLabel)
                    .font(.headline)
                    .foregroundStyle(Color.speedyAccent)
                MarkdownText(value: summaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ForEach(chatTurns) { turn in
                    Divider()
                    Text("You")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.speedyAccent)
                    Text(turn.question)
                        .textSelection(.enabled)
                    Text("AI")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.speedyMuted)
                    MarkdownText(value: turn.answer)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Text(status)
                    .font(.caption)
                    .foregroundStyle(Color.speedyMuted)
            }
            .padding()
        }
    }

    private var summaryActions: some View {
        VStack(spacing: 8) {
            if generating {
                ProgressView(status)
                    .frame(maxWidth: .infinity)
            }
            if !summaryText.isEmpty {
                HStack(spacing: 8) {
                    TextField("Ask about this video…", text: $chatQuestion)
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.send)
                        .onSubmit {
                            guard !generating else { return }
                            Task { await askFollowUp() }
                        }
                        .accessibilityLabel("Transcript question")
                    Button("Send") { Task { await askFollowUp() } }
                        .accessibilityIdentifier("transcript-chat-send")
                        .buttonStyle(.borderedProminent)
                        .tint(Color.speedyAccent)
                        .disabled(generating || chatQuestion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding(.horizontal)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button("Summary One") { Task { await generateSummary(label: "Summary One", prompt: settings.summaryOnePrompt) } }
                    Button("Summary Two") { Task { await generateSummary(label: "Summary Two", prompt: settings.summaryTwoPrompt) } }
                    if !summaryText.isEmpty {
                        Button("Transcript") {
                            summaryText = ""
                            summaryLabel = ""
                            summaryPrompt = ""
                            chatQuestion = ""
                            chatTurns = []
                            status = "\(transcript?.entries.count ?? 0) subtitles · tap a line to seek"
                        }
                        Button("Copy summary") {
                            UIPasteboard.general.string = summaryText
                            alertMessage = "Summary copied"
                        }
                        Button("Save summary") { saveSummary() }
                        if let transcript,
                           let payload = TextSharePayload.make(
                            videoTitle: transcript.videoTitle,
                            contentLabel: summaryLabel,
                            content: summaryText,
                            sourceURL: transcript.videoURL
                           ) {
                            ShareLink(item: payload.text, subject: Text(payload.subject)) {
                                Text("Share summary")
                            }
                        }
                    }
                }
                .buttonStyle(.bordered)
                .tint(Color.speedyAccent)
                .padding(.horizontal)
            }
        }
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
        .disabled(generating)
    }

    private func loadTranscript(preferredTrackID: String? = nil) async {
        loading = true
        do {
            let loaded = try await service.load(
                from: webController,
                preferredTrackID: preferredTrackID
            )
            transcript = loaded
            status = "\(loaded.entries.count) subtitles · tap a line to seek"
            if captionOptions.isEmpty {
                captionOptions = (try? await service.availableTracks(from: webController)) ?? []
            }
        } catch {
            status = error.localizedDescription
        }
        loading = false
    }

    private var selectedLanguageName: String {
        guard let selectedTrackID else { return "Language: Auto" }
        let name = captionOptions.first(where: { $0.id == selectedTrackID })?.displayName
            ?? "Selected"
        return "Language: \(name)"
    }

    private func paragraphEntries(from entries: [TranscriptEntry]) -> [TranscriptEntry] {
        guard !entries.isEmpty else { return [] }
        var paragraphs: [TranscriptEntry] = []
        var start = entries[0].start
        var end = entries[0].start + entries[0].duration
        var text = entries[0].text
        var count = 1
        for entry in entries.dropFirst() {
            let gap = entry.start - end
            if count >= 4 || gap > 2.5 || entry.start - start > 30 {
                paragraphs.append(TranscriptEntry(start: start, duration: max(0, end - start), text: text))
                start = entry.start
                text = entry.text
                count = 1
            } else {
                text += " " + entry.text
                count += 1
            }
            end = max(end, entry.start + entry.duration)
        }
        paragraphs.append(TranscriptEntry(start: start, duration: max(0, end - start), text: text))
        return paragraphs
    }

    private func followCurrentCaption() async {
        guard followPlayback else {
            playbackTime = nil
            return
        }
        while !Task.isCancelled && followPlayback {
            playbackTime = await webController.currentTime()
            try? await Task.sleep(for: .milliseconds(750))
        }
    }

    private func generateSummary(label: String, prompt: String) async {
        guard let transcript else { return }
        guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            alertMessage = SpeedyWatchError.emptyPrompt(label).localizedDescription
            return
        }
        guard !settings.apiKey.isEmpty, !settings.modelID.isEmpty else {
            alertMessage = SpeedyWatchError.missingConfiguration.localizedDescription
            return
        }
        generating = true
        summaryText = ""
        summaryLabel = ""
        summaryPrompt = ""
        chatQuestion = ""
        chatTurns = []
        status = "Creating \(label)…"
        let userMessage = AIRequestData.summary(for: transcript)
        do {
            summaryText = try await client.generate(
                apiKey: settings.apiKey,
                modelID: settings.modelID,
                systemPrompt: prompt,
                userMessage: userMessage
            )
            summaryLabel = label
            summaryPrompt = prompt
            status = "\(label) · \(settings.modelID)"
        } catch {
            alertMessage = error.localizedDescription
            status = "Summary failed"
        }
        generating = false
    }

    private func askFollowUp() async {
        guard !generating else { return }
        guard let transcript, !summaryText.isEmpty else { return }
        let question = chatQuestion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !question.isEmpty else { return }
        guard question.count <= 2_000 else {
            alertMessage = "Questions are limited to 2,000 characters"
            return
        }
        guard !summaryPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            alertMessage = SpeedyWatchError.emptyPrompt(summaryLabel).localizedDescription
            return
        }
        guard !settings.apiKey.isEmpty, !settings.modelID.isEmpty else {
            alertMessage = SpeedyWatchError.missingConfiguration.localizedDescription
            return
        }

        generating = true
        status = "Asking \(settings.modelID)…"
        let messages = AIRequestData.followUpMessages(
            for: transcript,
            summary: summaryText,
            turns: chatTurns,
            question: question
        )
        do {
            let answer = try await client.generate(
                apiKey: settings.apiKey,
                modelID: settings.modelID,
                systemPrompt: summaryPrompt,
                messages: messages
            )
            chatTurns.append(TranscriptChatTurn(question: question, answer: answer))
            chatQuestion = ""
            status = "\(summaryLabel) chat · \(settings.modelID)"
        } catch {
            alertMessage = error.localizedDescription
            status = "Question failed"
        }
        generating = false
    }

    private func saveSummary() {
        guard let transcript, !summaryText.isEmpty, !summaryLabel.isEmpty else { return }
        do {
            try summaries.save(
                videoTitle: transcript.videoTitle,
                summaryLabel: summaryLabel,
                summaryText: summaryText,
                sourceURL: transcript.videoURL
            )
            alertMessage = "Summary saved"
        } catch {
            alertMessage = error.localizedDescription
        }
    }
}

struct QuizView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var summaries: SavedSummaryStore
    @EnvironmentObject private var webController: YouTubeWebController

    @State private var transcript: TranscriptPackage?
    @State private var questionCount = 10
    @State private var output = ""
    @State private var outputLabel = ""
    @State private var status = "Loading subtitles…"
    @State private var loading = true
    @State private var generating = false
    @State private var alertMessage: String?

    private let service = TranscriptService()
    private let client = OpenRouterClient()
    private let counts = [6, 10, 12, 20]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(Color.speedyMuted)
                    HStack {
                        ForEach(counts, id: \.self) { count in
                            Button("\(count)") { questionCount = count }
                                .buttonStyle(.borderedProminent)
                                .tint(questionCount == count ? Color.speedyAccent : Color.speedyButton)
                                .accessibilityLabel("\(count) questions")
                                .accessibilityAddTraits(questionCount == count ? .isSelected : [])
                        }
                    }
                    Button {
                        Task { await generateQuiz() }
                    } label: {
                        if generating {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Create \(questionCount) questions").frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.speedyAccent)
                    .disabled(loading || generating || transcript == nil)

                    if output.isEmpty {
                        ContentUnavailableView(
                            "Pre-watch questions",
                            systemImage: "questionmark.bubble",
                            description: Text("Choose a count, then create a focused question guide from the current video's subtitles.")
                        )
                    } else {
                        MarkdownText(value: output)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HStack(spacing: 8) {
                            Button("Save quiz") { saveQuiz() }
                                .buttonStyle(.borderedProminent)
                                .tint(Color.speedyAccent)
                            if let transcript,
                               let payload = TextSharePayload.make(
                                videoTitle: transcript.videoTitle,
                                contentLabel: outputLabel,
                                content: output,
                                sourceURL: transcript.videoURL
                               ) {
                                ShareLink(item: payload.text, subject: Text(payload.subject)) {
                                    Text("Share quiz")
                                }
                                .buttonStyle(.bordered)
                                .tint(Color.speedyAccent)
                            }
                        }
                    }
                }
                .padding()
            }
            .background(Color.speedyBackground)
            .navigationTitle("Quiz")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Close") { dismiss() } } }
            .task { await loadTranscript() }
            .alert("SpeedyWatch", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) {
                Button("OK", role: .cancel) { alertMessage = nil }
            } message: { Text(alertMessage ?? "") }
        }
        .presentationDetents([.medium, .large])
    }

    private func loadTranscript() async {
        do {
            let loaded = try await service.load(from: webController)
            transcript = loaded
            status = "\(loaded.entries.count) subtitles ready"
        } catch {
            status = error.localizedDescription
        }
        loading = false
    }

    private func generateQuiz() async {
        guard let transcript else { return }
        let prompt = settings.quizPrompt
        guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            alertMessage = SpeedyWatchError.emptyPrompt("Quiz").localizedDescription
            return
        }
        guard !settings.apiKey.isEmpty, !settings.modelID.isEmpty else {
            alertMessage = SpeedyWatchError.missingConfiguration.localizedDescription
            return
        }
        let requestedCount = questionCount
        generating = true
        output = ""
        outputLabel = ""
        status = "Creating \(requestedCount) questions with \(settings.modelID)"
        let userMessage = AIRequestData.quiz(for: transcript, questionCount: requestedCount)
        do {
            output = try await client.generate(
                apiKey: settings.apiKey,
                modelID: settings.modelID,
                systemPrompt: prompt,
                userMessage: userMessage
            )
            outputLabel = "Quiz · \(requestedCount) questions"
            status = "\(requestedCount) pre-watch questions · \(settings.modelID)"
        } catch {
            alertMessage = error.localizedDescription
            status = "Quiz generation failed"
        }
        generating = false
    }
    private func saveQuiz() {
        guard let transcript, !output.isEmpty, !outputLabel.isEmpty else { return }
        do {
            try summaries.save(
                videoTitle: transcript.videoTitle,
                summaryLabel: outputLabel,
                summaryText: output,
                sourceURL: transcript.videoURL
            )
            alertMessage = "Quiz saved"
        } catch {
            alertMessage = error.localizedDescription
        }
    }

}

struct SavedSummariesView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var summaries: SavedSummaryStore
    @State private var query = ""
    @State private var pendingDelete: SavedSummary?
    @State private var alertMessage: String?

    let openSource: (URL) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if summaries.filtered(by: query).isEmpty {
                    ContentUnavailableView(
                        query.isEmpty ? "No saved items" : "No matches",
                        systemImage: "bookmark",
                        description: Text(query.isEmpty
                            ? "Save a summary or quiz to find it here."
                            : "Try a different title, type, heading, or phrase.")
                    )
                } else {
                    List {
                        ForEach(summaries.filtered(by: query)) { entry in
                            NavigationLink {
                                SavedSummaryDetail(entry: entry, openSource: openSource)
                            } label: {
                                VStack(alignment: .leading, spacing: 5) {
                                    Text(entry.videoTitle).font(.headline).lineLimit(2)
                                    Text(entry.summaryLabel).foregroundStyle(Color.speedyAccent)
                                    Text(entry.summaryText).font(.subheadline).foregroundStyle(.secondary).lineLimit(3)
                                    Text(entry.createdAt.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                .padding(.vertical, 4)
                            }
                            .swipeActions {
                                Button("Delete", role: .destructive) { pendingDelete = entry }
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Saved")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query, prompt: "Search saved content")
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Close") { dismiss() } } }
            .confirmationDialog(
                "Delete this saved item?",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) {
                    guard let pendingDelete else { return }
                    do { try summaries.delete(pendingDelete) }
                    catch { alertMessage = error.localizedDescription }
                    self.pendingDelete = nil
                }
                Button("Cancel", role: .cancel) { pendingDelete = nil }
            }
            .alert("SpeedyWatch", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) { Button("OK", role: .cancel) { alertMessage = nil } }
            message: { Text(alertMessage ?? "") }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct SavedSummaryDetail: View {
    let entry: SavedSummary
    let openSource: (URL) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(entry.summaryLabel).font(.headline).foregroundStyle(Color.speedyAccent)
                MarkdownText(value: entry.summaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Divider()
                Text(entry.sourceURL.absoluteString)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
                HStack(spacing: 8) {
                    Button("Open original video") {
                        guard YouTubeURLPolicy.isSupportedSource(entry.sourceURL) else { return }
                        openSource(entry.sourceURL)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.speedyAccent)
                    if let payload = TextSharePayload.make(
                        videoTitle: entry.videoTitle,
                        contentLabel: entry.summaryLabel,
                        content: entry.summaryText,
                        sourceURL: entry.sourceURL
                    ) {
                        ShareLink(item: payload.text, subject: Text(payload.subject)) {
                            Text("Share")
                        }
                        .buttonStyle(.bordered)
                        .tint(Color.speedyAccent)
                    }
                }
            }
            .padding()
        }
        .navigationTitle(entry.videoTitle)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var summaries: SavedSummaryStore

    @State private var apiKey = ""
    @State private var revealKey = false
    @State private var modelID = ""
    @State private var summaryOne = ""
    @State private var summaryTwo = ""
    @State private var quiz = ""
    @State private var defaultSpeed = "1"
    @State private var playbackProfile: PlaybackProfile = .normal
    @State private var adaptiveSpeedEnabled = false
    @State private var sponsorBlockEnabled = false
    @State private var sponsorCategoryEnabled = true
    @State private var selfPromotionCategoryEnabled = true
    @State private var interactionCategoryEnabled = false
    @State private var models: [OpenRouterModel] = []
    @State private var loadingModels = false
    @State private var status = ""
    @State private var alertMessage: String?
    @State private var prepared = false
    @State private var backupDocument = SpeedyWatchBackupDocument()
    @State private var showingBackupExporter = false
    @State private var showingBackupImporter = false

    private let client = OpenRouterClient()

    var body: some View {
        NavigationStack {
            Form {
                Section("Playback") {
                    Picker("Playback profile", selection: $playbackProfile) {
                        ForEach(PlaybackProfile.allCases) { profile in
                            Text("\(profile.displayName) · \(SpeedFormatting.rate(profile.speed))")
                                .tag(profile)
                        }
                    }
                    .onChange(of: playbackProfile) { _, profile in
                        defaultSpeed = SpeedFormatting.value(profile.speed)
                    }
                    Toggle("Adaptive caption-gap speed", isOn: $adaptiveSpeedEnabled)
                    Text("Adaptive speed adds 0.5x only during caption gaps, then returns to your chosen rate.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Toggle("SponsorBlock community skips", isOn: $sponsorBlockEnabled)
                    Toggle("Sponsors", isOn: $sponsorCategoryEnabled)
                        .disabled(!sponsorBlockEnabled)
                    Toggle("Self-promotion", isOn: $selfPromotionCategoryEnabled)
                        .disabled(!sponsorBlockEnabled)
                    Toggle("Interaction reminders", isOn: $interactionCategoryEnabled)
                        .disabled(!sponsorBlockEnabled)
                    Text("Optional community-submitted segments from SponsorBlock. Lookup uses only a four-character video-ID hash prefix.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    TextField("Default speed", text: $defaultSpeed)
                        .keyboardType(.decimalPad)
                    Text("Accepted range: 0.25x to 4x")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section("Backup") {
                    Text("Includes settings and saved summaries or quizzes. Your OpenRouter API key is never included.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Button("Export backup") { exportBackup() }
                    Button("Restore backup", role: .destructive) {
                        showingBackupImporter = true
                    }
                    Text("Restore replaces saved content, prompts, and preferences while leaving the API key unchanged.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("OpenRouter API key") {
                    HStack {
                        Group {
                            if revealKey { TextField("sk-or-…", text: $apiKey) }
                            else { SecureField("sk-or-…", text: $apiKey) }
                        }
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        Button { revealKey.toggle() } label: {
                            Image(systemName: revealKey ? "eye.slash" : "eye")
                        }
                        .accessibilityLabel(revealKey ? "Hide API key" : "Show API key")
                    }
                    Text(keyPreview).font(.caption.monospaced()).foregroundStyle(.secondary)
                    Button("Check key and refresh models") { Task { await refreshModels() } }
                        .disabled(loadingModels)
                    if loadingModels { ProgressView("Loading text models…") }
                    if !status.isEmpty { Text(status).font(.caption).foregroundStyle(.secondary) }
                }

                Section("Model") {
                    NavigationLink {
                        ModelSelectionView(models: models, selectedID: $modelID)
                    } label: {
                        LabeledContent("Selected model", value: selectedModelName)
                    }
                    .disabled(models.isEmpty)
                    Text(modelID.isEmpty ? "Refresh models to choose a text model." : modelID)
                        .font(.caption.monospaced()).foregroundStyle(.secondary)
                    if let selectedModel {
                        Text(selectedModel.guidance)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Summary One prompt") {
                    TextEditor(text: $summaryOne).frame(minHeight: 170)
                }
                Section("Summary Two prompt") {
                    TextEditor(text: $summaryTwo).frame(minHeight: 210)
                }
                Section("Quiz prompt") {
                    TextEditor(text: $quiz).frame(minHeight: 190)
                }

                Section {
                    HStack(spacing: 0) {
                        Text("Brought to you by the team from ")
                        Link("SEO Time Machines", destination: URL(string: "https://seotimemachines.com")!)
                            .foregroundStyle(Color.speedyAccent)
                    }
                    .font(.footnote)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
            .onAppear { prepareDrafts() }
            .task { if models.isEmpty { await refreshModels() } }
            .alert("SpeedyWatch", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) { Button("OK", role: .cancel) { alertMessage = nil } }
            message: { Text(alertMessage ?? "") }
            .fileExporter(
                isPresented: $showingBackupExporter,
                document: backupDocument,
                contentType: .json,
                defaultFilename: "SpeedyWatch-backup"
            ) { result in
                if case .failure = result {
                    alertMessage = "Backup could not be exported"
                }
            }
            .fileImporter(
                isPresented: $showingBackupImporter,
                allowedContentTypes: [.json],
                allowsMultipleSelection: false
            ) { result in
                restoreBackup(from: result)
            }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled()
    }

    private var selectedModel: OpenRouterModel? {
        models.first(where: { $0.id == modelID })
    }

    private var selectedModelName: String {
        selectedModel?.displayName ?? (modelID.isEmpty ? "None" : modelID)
    }

    private var keyPreview: String {
        let value = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return "Not configured" }
        guard value.count > 10 else { return String(repeating: "•", count: value.count) }
        return "\(value.prefix(5))••••\(value.suffix(4))"
    }

    private func prepareDrafts() {
        guard !prepared else { return }
        prepared = true
        apiKey = settings.apiKey
        modelID = settings.modelID
        summaryOne = settings.summaryOnePrompt.isEmpty ? AppSettings.defaultSummaryOnePrompt : settings.summaryOnePrompt
        summaryTwo = settings.summaryTwoPrompt.isEmpty ? AppSettings.defaultSummaryTwoPrompt : settings.summaryTwoPrompt
        quiz = settings.quizPrompt.isEmpty ? AppSettings.defaultQuizPrompt : settings.quizPrompt
        defaultSpeed = SpeedFormatting.value(settings.defaultPlaybackSpeed)
        playbackProfile = settings.playbackProfile
        adaptiveSpeedEnabled = settings.adaptiveSpeedEnabled
        sponsorBlockEnabled = settings.sponsorBlockEnabled
        sponsorCategoryEnabled = settings.sponsorCategoryEnabled
        selfPromotionCategoryEnabled = settings.selfPromotionCategoryEnabled
        interactionCategoryEnabled = settings.interactionCategoryEnabled
    }

    private func refreshModels() async {
        loadingModels = true
        status = ""
        do {
            models = try await client.fetchModels(apiKey: apiKey)
            if modelID.isEmpty || !models.contains(where: { $0.id == modelID }) {
                modelID = models.first(where: { $0.id == AppSettings.preferredModelID })?.id
                    ?? models.first?.id ?? ""
            }
            status = "\(models.count) text models loaded · key check passed"
        } catch {
            status = "Model refresh failed"
            alertMessage = error.localizedDescription
        }
        loadingModels = false
    }

    private func exportBackup() {
        do {
            backupDocument = try SpeedyWatchBackupService.create(settings: settings, store: summaries)
            showingBackupExporter = true
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    private func restoreBackup(from result: Result<[URL], Error>) {
        do {
            guard let url = try result.get().first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer {
                if accessed {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            let handle = try FileHandle(forReadingFrom: url)
            defer { try? handle.close() }
            let data = try handle.read(upToCount: SpeedyWatchBackupDocument.maximumBytes + 1) ?? Data()
            try SpeedyWatchBackupService.restore(data: data, settings: settings, store: summaries)
            prepared = false
            prepareDrafts()
            alertMessage = "Backup restored. The API key was left unchanged."
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    private func save() {
        guard let speed = Double(defaultSpeed.replacingOccurrences(of: ",", with: ".")), speed >= 0.25, speed <= 4 else {
            alertMessage = "Default speed must be between 0.25 and 4"
            return
        }
        do {
            try settings.save(
                apiKey: apiKey,
                modelID: modelID,
                summaryOnePrompt: summaryOne,
                summaryTwoPrompt: summaryTwo,
                quizPrompt: quiz,
                defaultPlaybackSpeed: speed,
                playbackProfile: playbackProfile,
                adaptiveSpeedEnabled: adaptiveSpeedEnabled
            )
            settings.setSponsorBlock(
                enabled: sponsorBlockEnabled,
                sponsor: sponsorCategoryEnabled,
                selfPromotion: selfPromotionCategoryEnabled,
                interaction: interactionCategoryEnabled
            )
            dismiss()
        } catch {
            alertMessage = error.localizedDescription
        }
    }
}

private struct ModelSelectionView: View {
    private enum Filter: String, CaseIterable, Identifiable {
        case all = "All"
        case free = "Free"
        case longContext = "Long context"

        var id: Self { self }
    }

    let models: [OpenRouterModel]
    @Binding var selectedID: String
    @State private var query = ""
    @State private var filter: Filter = .all

    private var filtered: [OpenRouterModel] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return models.filter { model in
            let matchesFilter: Bool
            switch filter {
            case .all:
                matchesFilter = true
            case .free:
                matchesFilter = model.isFree
            case .longContext:
                matchesFilter = model.hasLongContext
            }
            return matchesFilter && (needle.isEmpty || model.searchText.contains(needle))
        }
    }

    var body: some View {
        List {
            Picker("Filter models", selection: $filter) {
                ForEach(Filter.allCases) { option in
                    Text(option.rawValue).tag(option)
                }
            }
            .pickerStyle(.segmented)

            ForEach(filtered) { model in
                Button {
                    selectedID = model.id
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(model.displayName).foregroundStyle(.primary)
                            Text(model.id).font(.caption.monospaced()).foregroundStyle(.secondary)
                            Text(model.guidance)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if model.id == selectedID {
                            Image(systemName: "checkmark").foregroundStyle(Color.speedyAccent)
                        }
                    }
                }
                .accessibilityAddTraits(model.id == selectedID ? .isSelected : [])
            }
        }
        .navigationTitle("OpenRouter Model")
        .searchable(text: $query, prompt: "Search models")
    }
}
