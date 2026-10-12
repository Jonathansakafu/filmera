package com.filmera.app.core.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider

/** An app captures can be sent to directly. Names only; no third-party logos are bundled. */
data class ShareTarget(val name: String, val packageName: String)

/**
 * Shares captures through Android's standard share intent. This is the supported way to
 * get FILMERA content into WhatsApp, Instagram, Telegram and others.
 */
class ShareHelper(private val context: Context) {

    private val known = listOf(
        ShareTarget("WhatsApp", "com.whatsapp"),
        ShareTarget("WhatsApp Business", "com.whatsapp.w4b"),
        ShareTarget("Instagram", "com.instagram.android"),
        ShareTarget("Telegram", "org.telegram.messenger"),
        ShareTarget("Snapchat", "com.snapchat.android"),
        ShareTarget("TikTok", "com.zhiliaoapp.musically"),
    )

    fun installedTargets(): List<ShareTarget> = known.filter { isInstalled(it.packageName) }

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens [target]'s share screen, or the system share sheet when [target] is null. */
    fun share(capture: Capture, target: ShareTarget? = null): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", capture.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = capture.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            target?.let { setPackage(it.packageName) }
        }
        val intent = if (target == null) {
            Intent.createChooser(send, "Share with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else send
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
