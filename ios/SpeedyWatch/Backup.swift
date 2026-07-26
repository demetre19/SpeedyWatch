import Foundation
import SwiftUI
import UniformTypeIdentifiers

struct SpeedyWatchBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    static let maximumBytes = 16 * 1_024 * 1_024

    var data: Data

    init(data: Data = Data()) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              data.count <= Self.maximumBytes else {
            throw SpeedyWatchError.persistence("Backup file is too large")
        }
        self.data = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

@MainActor
enum SpeedyWatchBackupService {
    private static let schemaVersion = 1

    private struct Payload: Codable {
        let schemaVersion: Int
        let exportedAt: Int64
        let containsSecrets: Bool
        let settings: BackupSettings
        let savedItems: [BackupItem]
    }

    private struct BackupSettings: Codable {
        let modelId: String
        let summaryOnePrompt: String
        let summaryTwoPrompt: String
        let quizPrompt: String
        let defaultPlaybackSpeed: Double
        let lockIconEnabled: Bool?
        let playbackProfile: PlaybackProfile?
        let adaptiveSpeedEnabled: Bool?
        let sponsorBlockEnabled: Bool?
        let sponsorCategoryEnabled: Bool?
        let selfPromotionCategoryEnabled: Bool?
        let interactionCategoryEnabled: Bool?
    }

    private struct BackupItem: Codable {
        let videoTitle: String
        let contentLabel: String
        let content: String
        let sourceURL: String
        let createdAt: Int64
    }

    static func create(settings: AppSettings, store: SavedSummaryStore) throws -> SpeedyWatchBackupDocument {
        let payload = Payload(
            schemaVersion: schemaVersion,
            exportedAt: Int64(Date().timeIntervalSince1970 * 1_000),
            containsSecrets: false,
            settings: BackupSettings(
                modelId: settings.modelID,
                summaryOnePrompt: settings.summaryOnePrompt,
                summaryTwoPrompt: settings.summaryTwoPrompt,
                quizPrompt: settings.quizPrompt,
                defaultPlaybackSpeed: settings.defaultPlaybackSpeed,
                lockIconEnabled: nil,
                playbackProfile: settings.playbackProfile,
                adaptiveSpeedEnabled: settings.adaptiveSpeedEnabled,
                sponsorBlockEnabled: settings.sponsorBlockEnabled,
                sponsorCategoryEnabled: settings.sponsorCategoryEnabled,
                selfPromotionCategoryEnabled: settings.selfPromotionCategoryEnabled,
                interactionCategoryEnabled: settings.interactionCategoryEnabled
            ),
            savedItems: store.entries.map {
                BackupItem(
                    videoTitle: $0.videoTitle,
                    contentLabel: $0.summaryLabel,
                    content: $0.summaryText,
                    sourceURL: $0.sourceURL.absoluteString,
                    createdAt: Int64($0.createdAt.timeIntervalSince1970 * 1_000)
                )
            }
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(payload)
        guard data.count <= SpeedyWatchBackupDocument.maximumBytes else {
            throw SpeedyWatchError.persistence("Backup is too large to export")
        }
        return SpeedyWatchBackupDocument(data: data)
    }

    static func restore(
        data: Data,
        settings: AppSettings,
        store: SavedSummaryStore
    ) throws {
        guard data.count <= SpeedyWatchBackupDocument.maximumBytes else {
            throw SpeedyWatchError.persistence("Backup file is too large")
        }
        let payload: Payload
        do {
            payload = try JSONDecoder().decode(Payload.self, from: data)
        } catch {
            throw SpeedyWatchError.persistence("Backup file is not valid SpeedyWatch JSON")
        }
        guard payload.schemaVersion == schemaVersion,
              payload.savedItems.count <= 10_000,
              payload.settings.modelId.count <= 300,
              payload.settings.summaryOnePrompt.count <= 100_000,
              payload.settings.summaryTwoPrompt.count <= 100_000,
              payload.settings.quizPrompt.count <= 100_000,
              payload.settings.defaultPlaybackSpeed >= 0.25,
              payload.settings.defaultPlaybackSpeed <= 4 else {
            throw SpeedyWatchError.persistence("Backup version or settings are invalid")
        }
        let restored = try payload.savedItems.map { item -> SavedSummary in
            guard item.videoTitle.count <= 2_000,
                  item.contentLabel.count <= 500,
                  item.content.count <= 1_000_000,
                  item.createdAt > 0,
                  let sourceURL = URL(string: item.sourceURL),
                  YouTubeURLPolicy.isSupportedSource(sourceURL) else {
                throw SpeedyWatchError.persistence("A saved item in the backup is invalid")
            }
            return SavedSummary(
                id: UUID(),
                videoTitle: item.videoTitle,
                summaryLabel: item.contentLabel,
                summaryText: item.content,
                sourceURL: sourceURL,
                createdAt: Date(timeIntervalSince1970: TimeInterval(item.createdAt) / 1_000)
            )
        }

        let previousEntries = store.entries
        try store.replaceAll(restored)
        do {
            try settings.restoreBackup(
                modelID: payload.settings.modelId,
                summaryOnePrompt: payload.settings.summaryOnePrompt,
                summaryTwoPrompt: payload.settings.summaryTwoPrompt,
                quizPrompt: payload.settings.quizPrompt,
                defaultPlaybackSpeed: payload.settings.defaultPlaybackSpeed,
                playbackProfile: payload.settings.playbackProfile ?? .normal,
                adaptiveSpeedEnabled: payload.settings.adaptiveSpeedEnabled ?? false
            )
            settings.setSponsorBlock(
                enabled: payload.settings.sponsorBlockEnabled ?? false,
                sponsor: payload.settings.sponsorCategoryEnabled ?? true,
                selfPromotion: payload.settings.selfPromotionCategoryEnabled ?? true,
                interaction: payload.settings.interactionCategoryEnabled ?? false
            )
        } catch {
            try? store.replaceAll(previousEntries)
            throw error
        }
    }
}
