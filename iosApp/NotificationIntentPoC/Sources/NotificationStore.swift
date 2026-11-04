import Foundation

/// One notification payload handed to the app by a Shortcuts automation
/// (or by the in-app test button). Values are stored exactly as received:
/// `nil` and `""` are kept distinct so we can see what Shortcuts really passes.
struct NotificationRecord: Codable, Identifiable, Equatable {
    /// Where the record came from.
    enum Origin: String, Codable {
        /// Delivered through `RecordNotificationIntent` (Shortcuts automation / Siri / Shortcuts app).
        case shortcut
        /// Created by the "테스트 기록 추가" button in the app.
        case manualTest
    }

    let id: UUID
    let origin: Origin
    /// When our intent (or the test button) ran, on this device's clock.
    let receivedAt: Date
    let title: String?
    let subtitle: String?
    let body: String?
    let sourceApp: String?
    /// Notification delivery time, if the automation exposes it and the tester bound it.
    let postedAt: Date?

    init(
        id: UUID = UUID(),
        origin: Origin,
        receivedAt: Date = Date(),
        title: String?,
        subtitle: String?,
        body: String?,
        sourceApp: String?,
        postedAt: Date?
    ) {
        self.id = id
        self.origin = origin
        self.receivedAt = receivedAt
        self.title = title
        self.subtitle = subtitle
        self.body = body
        self.sourceApp = sourceApp
        self.postedAt = postedAt
    }

    /// Seconds between notification delivery and our intent running, when both are known.
    var latencySeconds: Double? {
        guard let postedAt else { return nil }
        return receivedAt.timeIntervalSince(postedAt)
    }
}

/// Top-level shape of the exported JSON file.
struct NotificationExport: Codable {
    let schemaVersion: Int
    let platform: String
    let osVersion: String
    let exportedAt: Date
    let count: Int
    let records: [NotificationRecord]
}

/// Minimal on-device store backed by `UserDefaults`.
///
/// The whole list is encoded as one JSON blob under a single key. That is fine for
/// a PoC with at most a few hundred records. All access goes through a lock because
/// the App Intent may run on a background thread while the UI is reading.
final class NotificationStore: @unchecked Sendable {
    static let shared = NotificationStore()

    /// UserDefaults suite. Must NOT equal the bundle id (`UserDefaults(suiteName:)`
    /// returns nil in that case).
    /// TODO: App Group — switch to "group.com.jim.realcash" (and add the App Groups
    /// capability) once a widget or extension needs to read the same records.
    static let suiteName = "com.jim.realcash.poc.notifications"

    /// Posted on the main queue after every change.
    static let didChangeNotification = Notification.Name("com.jim.realcash.poc.NotificationStoreDidChange")

    private static let recordsKey = "records.v1"

    private let defaults: UserDefaults
    private let lock = NSLock()

    private init() {
        defaults = UserDefaults(suiteName: NotificationStore.suiteName) ?? UserDefaults.standard
    }

    // MARK: - Read

    /// All records, newest first.
    func all() -> [NotificationRecord] {
        lock.lock()
        defer { lock.unlock() }
        return loadUnlocked().sorted { $0.receivedAt > $1.receivedAt }
    }

    // MARK: - Write

    func append(_ record: NotificationRecord) {
        lock.lock()
        var records = loadUnlocked()
        records.append(record)
        saveUnlocked(records)
        lock.unlock()
        postDidChange()
    }

    func clear() {
        lock.lock()
        defaults.removeObject(forKey: NotificationStore.recordsKey)
        lock.unlock()
        postDidChange()
    }

    // MARK: - Export

    /// Pretty-printed JSON of every record (newest first) plus some device context.
    /// Dates are ISO 8601 in the device's local time zone with milliseconds.
    func exportJSON() -> Data {
        let records = all()
        let export = NotificationExport(
            schemaVersion: 1,
            platform: "ios",
            osVersion: ProcessInfo.processInfo.operatingSystemVersionString,
            exportedAt: Date(),
            count: records.count,
            records: records
        )

        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone.current
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(formatter.string(from: date))
        }

        do {
            return try encoder.encode(export)
        } catch {
            let message = "{\"error\": \"export failed: \(error.localizedDescription)\"}"
            return Data(message.utf8)
        }
    }

    // MARK: - Private (call only while holding `lock`)

    private func loadUnlocked() -> [NotificationRecord] {
        guard let data = defaults.data(forKey: NotificationStore.recordsKey) else {
            return []
        }
        do {
            return try JSONDecoder().decode([NotificationRecord].self, from: data)
        } catch {
            // Corrupt or incompatible data: start over rather than crash.
            return []
        }
    }

    private func saveUnlocked(_ records: [NotificationRecord]) {
        // Default (.deferredToDate) date encoding keeps full precision for storage.
        guard let data = try? JSONEncoder().encode(records) else { return }
        defaults.set(data, forKey: NotificationStore.recordsKey)
    }

    private func postDidChange() {
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: NotificationStore.didChangeNotification, object: nil)
        }
    }
}
