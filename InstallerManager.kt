package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object InstallerManager {
    fun openPlayStoreSearch(context: Context, appName: String): Boolean {
        val q = Uri.encode(appName.trim())
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(market); true } catch (_: Exception) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
        }
    }
    fun openApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }
}
