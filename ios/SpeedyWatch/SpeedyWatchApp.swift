import SwiftUI

@main
struct SpeedyWatchApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var settings = AppSettings()
    @StateObject private var summaries = SavedSummaryStore()
    @StateObject private var webController = YouTubeWebController()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settings)
                .environmentObject(summaries)
                .environmentObject(webController)
                .preferredColorScheme(.dark)
                .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        }
        .onChange(of: scenePhase) { _, phase in
            UIApplication.shared.isIdleTimerDisabled = phase == .active
        }
    }
}
