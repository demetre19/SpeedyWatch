import SwiftUI

extension Color {
    static let speedyBackground = Color(red: 15 / 255, green: 15 / 255, blue: 15 / 255)
    static let speedyPanel = Color(red: 30 / 255, green: 30 / 255, blue: 30 / 255)
    static let speedyButton = Color(red: 48 / 255, green: 48 / 255, blue: 48 / 255)
    static let speedyAccent = Color(red: 1, green: 0, blue: 51 / 255)
    static let speedyMuted = Color(red: 185 / 255, green: 185 / 255, blue: 185 / 255)
}

private enum ActiveSheet: String, Identifiable {
    case transcript, quiz, saved, settings
    var id: String { rawValue }
}

struct ContentView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var summaries: SavedSummaryStore
    @EnvironmentObject private var webController: YouTubeWebController

    @State private var activeSheet: ActiveSheet?
    @State private var speed = 1.0
    @State private var customSpeed = "1"
    @State private var adSkipping = true
    @State private var initialized = false
    @FocusState private var customSpeedFocused: Bool

    private let presets = [0.5, 1, 1.5, 2, 2.5, 3, 4]

    var body: some View {
        VStack(spacing: 0) {
            navigationBar
            ZStack(alignment: .top) {
                YouTubeWebView(controller: webController)
                if webController.isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .padding(8)
                        .background(.black.opacity(0.75), in: Capsule())
                        .padding(.top, 8)
                        .accessibilityLabel("Loading YouTube")
                }
            }
            playbackControls
        }
        .background(Color.speedyBackground)
        .onAppear {
            guard !initialized else { return }
            initialized = true
            applySpeed(settings.defaultPlaybackSpeed)
            webController.setAdSkipping(adSkipping)
        }
        .onChange(of: settings.defaultPlaybackSpeed) { _, newValue in
            applySpeed(newValue)
        }
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .transcript:
                TranscriptView()
                    .environmentObject(settings)
                    .environmentObject(summaries)
                    .environmentObject(webController)
            case .quiz:
                QuizView()
                    .environmentObject(settings)
                    .environmentObject(summaries)
                    .environmentObject(webController)
            case .saved:
                SavedSummariesView { url in
                    webController.load(url)
                    activeSheet = nil
                }
                .environmentObject(summaries)
            case .settings:
                SettingsView()
                    .environmentObject(settings)
            }
        }
        .alert("SpeedyWatch", isPresented: Binding(
            get: { webController.errorMessage != nil },
            set: { if !$0 { webController.dismissError() } }
        )) {
            Button("OK", role: .cancel) { webController.dismissError() }
        } message: {
            Text(webController.errorMessage ?? "")
        }
    }

    private var navigationBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                toolbarButton("house.fill", label: "YouTube home", action: webController.loadHome)
                toolbarButton("chevron.backward", label: "Back", action: webController.goBack)
                toolbarButton("chevron.forward", label: "Forward", action: webController.goForward)
                toolbarButton("arrow.clockwise", label: "Reload", action: webController.reload)
                toolbarButton("captions.bubble", label: "YouTube subtitles") { activeSheet = .transcript }
                toolbarButton("questionmark.bubble", label: "Create video quiz") { activeSheet = .quiz }
                toolbarButton("bookmark", label: "Saved summaries and quizzes") { activeSheet = .saved }
                toolbarButton("gearshape", label: "Settings") { activeSheet = .settings }
            }
            .padding(.horizontal, 4)
        }
        .frame(height: 48)
        .background(Color.speedyBackground)
    }

    private var playbackControls: some View {
        VStack(spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(presets, id: \.self) { rate in
                        Button(SpeedFormatting.rate(rate)) { applySpeed(rate) }
                            .buttonStyle(SpeedButtonStyle(selected: abs(rate - speed) < 0.001))
                            .accessibilityHint(rate == speed ? "Selected" : "Sets playback speed")
                    }
                    TextField("2.7", text: $customSpeed)
                        .keyboardType(.decimalPad)
                        .focused($customSpeedFocused)
                        .multilineTextAlignment(.center)
                        .textFieldStyle(.plain)
                        .frame(width: 66, height: 44)
                        .background(Color.speedyButton, in: RoundedRectangle(cornerRadius: 6))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.speedyMuted.opacity(0.65)))
                        .accessibilityLabel("Custom playback speed")
                    toolbarButton("checkmark", label: "Set custom speed", action: applyCustomSpeed)
                }
            }

            HStack(spacing: 6) {
                Button("−0.1") { applySpeed(speed - 0.1) }
                    .buttonStyle(SpeedButtonStyle())
                    .accessibilityLabel("Decrease speed by 0.1")
                Text("\(SpeedFormatting.rate(speed)) · ads \(adSkipping ? "blocked" : "allowed")")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.speedyAccent))
                    .accessibilityLabel("Playback speed \(SpeedFormatting.rate(speed)), ads \(adSkipping ? "blocked" : "allowed")")
                Button("+0.1") { applySpeed(speed + 0.1) }
                    .buttonStyle(SpeedButtonStyle())
                    .accessibilityLabel("Increase speed by 0.1")
                Button("Ads: \(adSkipping ? "ON" : "OFF")") {
                    adSkipping.toggle()
                    webController.setAdSkipping(adSkipping)
                }
                .buttonStyle(SpeedButtonStyle(selected: adSkipping))
                .accessibilityHint("Toggles best-effort YouTube ad skipping")
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 7)
        .background(Color.speedyPanel)
    }

    private func toolbarButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 42, height: 42)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
        .accessibilityLabel(label)
    }

    private func applyCustomSpeed() {
        customSpeedFocused = false
        guard let value = Double(customSpeed.replacingOccurrences(of: ",", with: ".")), value >= 0.25, value <= 4 else {
            customSpeed = SpeedFormatting.value(speed)
            return
        }
        applySpeed(value)
    }

    private func applySpeed(_ value: Double) {
        speed = (min(4, max(0.25, value)) * 100).rounded() / 100
        customSpeed = SpeedFormatting.value(speed)
        webController.setSpeed(speed)
    }
}

private struct SpeedButtonStyle: ButtonStyle {
    var selected = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(selected ? Color.speedyAccent : Color.speedyButton, in: RoundedRectangle(cornerRadius: 6))
            .opacity(configuration.isPressed ? 0.72 : 1)
    }
}
