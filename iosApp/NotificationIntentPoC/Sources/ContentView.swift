import Combine
import SwiftUI
import UIKit

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase

    @State private var records: [NotificationRecord] = []
    @State private var exportURL: URL?
    @State private var showClearConfirm = false
    @State private var copiedMessageVisible = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent("수신 기록", value: "\(records.count)건")
                    LabeledContent("단축어 경유", value: "\(records.filter { $0.origin == .shortcut }.count)건")
                    LabeledContent("OS", value: ProcessInfo.processInfo.operatingSystemVersionString)
                } footer: {
                    Text("단축어 자동화가 '결제 알림 기록' 동작을 실행하면 여기에 쌓입니다. 앱을 열어 둔 상태에서도 자동으로 갱신됩니다.")
                }

                Section("도구") {
                    if let exportURL {
                        ShareLink(item: exportURL) {
                            Label("내보내기 (JSON)", systemImage: "square.and.arrow.up")
                        }
                    }
                    Button {
                        UIPasteboard.general.string = String(decoding: NotificationStore.shared.exportJSON(), as: UTF8.self)
                        copiedMessageVisible = true
                    } label: {
                        Label("JSON 클립보드에 복사", systemImage: "doc.on.doc")
                    }
                    Button {
                        addTestRecord()
                    } label: {
                        Label("테스트 기록 추가", systemImage: "plus.circle")
                    }
                    Button(role: .destructive) {
                        showClearConfirm = true
                    } label: {
                        Label("모두 삭제", systemImage: "trash")
                    }
                    .disabled(records.isEmpty)
                }

                Section("기록 (최신순)") {
                    if records.isEmpty {
                        Text("아직 기록이 없습니다.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(records) { record in
                            RecordRow(record: record)
                        }
                    }
                }
            }
            .navigationTitle("알림 수집 PoC")
            .refreshable {
                reload()
            }
        }
        .confirmationDialog("모든 기록을 삭제할까요?", isPresented: $showClearConfirm, titleVisibility: .visible) {
            Button("모두 삭제", role: .destructive) {
                NotificationStore.shared.clear()
                reload()
            }
            Button("취소", role: .cancel) {}
        } message: {
            Text("먼저 내보내기로 백업했는지 확인하세요. 되돌릴 수 없습니다.")
        }
        .alert("클립보드에 복사했습니다", isPresented: $copiedMessageVisible) {
            Button("확인", role: .cancel) {}
        }
        .onAppear {
            reload()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                reload()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NotificationStore.didChangeNotification)) { _ in
            reload()
        }
    }

    private func reload() {
        records = NotificationStore.shared.all()
        exportURL = writeExportFile()
    }

    private func addTestRecord() {
        let record = NotificationRecord(
            origin: .manualTest,
            title: "테스트 결제 알림",
            subtitle: nil,
            body: "테스트카드 12,340원 승인 테스트가맹점",
            sourceApp: "RealCash PoC (테스트)",
            postedAt: Date().addingTimeInterval(-1.5)
        )
        NotificationStore.shared.append(record)
        reload()
    }

    /// Writes the current export to a temporary .json file so ShareLink can hand
    /// a real file (with a sensible name) to AirDrop / Files / Mail.
    private func writeExportFile() -> URL? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        let name = "realcash-ios-notifications-\(formatter.string(from: Date())).json"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try NotificationStore.shared.exportJSON().write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }
}

private struct RecordRow: View {
    let record: NotificationRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(display(record.sourceApp))
                    .font(.caption.bold())
                if record.origin == .manualTest {
                    Text("테스트")
                        .font(.caption2)
                        .padding(.horizontal, 4)
                        .background(Color.orange.opacity(0.25))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
                Spacer()
                Text(record.receivedAt, format: .dateTime.month().day().hour().minute().second())
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(display(record.title))
                .font(.headline)
            if record.subtitle != nil {
                Text(display(record.subtitle))
                    .font(.subheadline)
            }
            Text(display(record.body))
                .font(.body)
            if let latency = record.latencySeconds {
                Text("지연 \(String(format: "%.1f", latency))초 (알림 시각 기준)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    /// Distinguishes "not passed" from "passed but empty" so the tester can tell them apart.
    private func display(_ value: String?) -> String {
        guard let value else { return "(없음)" }
        return value.isEmpty ? "(빈 문자열)" : value
    }
}

#Preview {
    ContentView()
}
