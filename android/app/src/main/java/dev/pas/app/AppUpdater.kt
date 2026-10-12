package dev.pas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Finds a newer Pas in the data repository's releases and hands it to the Android installer. */
class AppUpdater(private val context: Context) {

    private val apkFile = File(context.cacheDir, "updates/pas.apk")

    /** Network call: run it off the main thread. Null when the installed version is the latest. */
    fun findNewerRelease(github: GitHubFiles): GitHubFiles.Release? {
        val latest = github.latestRelease() ?: return null
        return if (latest.versionCode > installedVersionCode()) latest else null
    }

    /** Android asks once whether Pas may install apps; without that the installer refuses the APK. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Network call: run it off the main thread. */
    fun download(github: GitHubFiles, release: GitHubFiles.Release) {
        github.downloadReleaseAsset(release.apkAssetId, apkFile)
    }

    fun openInstaller() {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun installedVersionCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
}
