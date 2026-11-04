package com.jim.realcash.android.samples

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import java.io.File

/** Hands the collected samples to the user: a share sheet with the JSONL file, or the clipboard. */
object SampleExporter {
    /** Most samples put on the clipboard (binder transactions are limited to about 1 MB). */
    const val CLIPBOARD_LIMIT: Int = 200

    /**
     * Copies the JSONL file to `cacheDir/export/` (replacing earlier snapshots) and returns its [FileProvider] uri,
     * or `null` when there is nothing to share. Blocking file I/O: call from a background dispatcher.
     */
    fun snapshot(context: Context, store: NotificationSampleStore): Uri? {
        val dir = File(context.cacheDir, EXPORT_DIR)
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "notification-samples-${System.currentTimeMillis()}.jsonl")
        if (!store.copyTo(file)) return null
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Opens the share sheet for a [snapshot] uri (main thread). */
    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "RealCash 알림 샘플")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to the chosen app.
        send.clipData = ClipData.newRawUri("notification-samples.jsonl", uri)
        context.startActivity(Intent.createChooser(send, "알림 샘플 내보내기"))
    }

    /** Puts the newest [CLIPBOARD_LIMIT] samples on the clipboard as a JSON array; returns how many. */
    fun copyToClipboard(context: Context, samples: List<NotificationSample>): Int {
        val chosen = samples.takeLast(CLIPBOARD_LIMIT)
        val clip = ClipData.newPlainText("RealCash 알림 샘플", NotificationSampleCodec.encodeArray(chosen))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Raw notification text may contain card details: keep it out of the clipboard preview.
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        return chosen.size
    }

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** Sub-directory of `cacheDir` exposed by the FileProvider (`res/xml/file_paths.xml`). */
    private const val EXPORT_DIR = "export"
}
