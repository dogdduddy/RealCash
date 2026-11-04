import AppIntents
import Foundation
import os

/// App Intent that a Shortcuts automation ("알림 수신 시" trigger on iOS 27) calls with
/// the received notification's fields. It only records the payload; parsing happens
/// later from the exported JSON.
///
/// Property names are prefixed (`notificationTitle` etc.) so they never collide with
/// the protocol's static `title`. The labels shown in Shortcuts come from `@Parameter(title:)`.
struct RecordNotificationIntent: AppIntent {
    static var title: LocalizedStringResource = "결제 알림 기록"

    /// Run in the background; do not bring the app to the foreground.
    static var openAppWhenRun: Bool = false

    @Parameter(title: "제목")
    var notificationTitle: String?

    @Parameter(title: "부제목")
    var notificationSubtitle: String?

    @Parameter(title: "본문")
    var notificationBody: String?

    @Parameter(title: "앱 이름")
    var sourceApp: String?

    @Parameter(title: "알림 시각")
    var postedAt: Date?

    init() {}

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let record = NotificationRecord(
            origin: .shortcut,
            title: notificationTitle,
            subtitle: notificationSubtitle,
            body: notificationBody,
            sourceApp: sourceApp,
            postedAt: postedAt
        )
        NotificationStore.shared.append(record)

        let total = NotificationStore.shared.all().count
        let logger = Logger(subsystem: "com.jim.realcash.poc", category: "intent")
        logger.info("Recorded notification #\(total, privacy: .public) from \(record.sourceApp ?? "nil", privacy: .public)")

        let app = record.sourceApp ?? "알 수 없는 앱"
        let message = "RealCash PoC: \(app) 알림 기록됨 (총 \(total)건)"
        return .result(dialog: "\(message)")
    }
}

/// Makes the intent show up in the Shortcuts app (and Siri) right after install,
/// without the user creating anything first.
struct PoCShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: RecordNotificationIntent(),
            phrases: ["\(.applicationName)에 결제 알림 기록"],
            shortTitle: "결제 알림 기록",
            systemImageName: "bell.badge"
        )
    }
}
