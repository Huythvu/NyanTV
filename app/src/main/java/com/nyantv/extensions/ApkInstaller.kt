package com.nyantv.extensions

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Installs extension APKs using the [PackageInstaller] session API.
 *
 * The legacy flow fired an [Intent.ACTION_VIEW] with the
 * `application/vnd.android.package-archive` MIME type and relied on a system activity to
 * pick it up. Phones ship that activity, but Android TV / Google TV builds frequently omit
 * it, so the intent failed with ActivityNotFoundException and nothing installed — which is
 * why side-loading worked on phones but silently did nothing on TV.
 *
 * The session API talks to the package installer service directly and works on both form
 * factors. When the platform needs the user to confirm (the normal case for a non-system
 * app), it hands back a confirmation intent via [PackageInstaller.STATUS_PENDING_USER_ACTION]
 * which we launch.
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"
    private const val INSTALL_ACTION = "com.nyantv.extensions.APK_INSTALL_STATUS"

    @Volatile
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // The system needs the user to confirm. Launch the confirmation dialog
                    // the installer service handed back to us.
                    val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }
                        .onFailure { Log.e(TAG, "Could not launch install confirmation", it) }
                }

                PackageInstaller.STATUS_SUCCESS ->
                    Log.d(TAG, "Extension installed successfully")

                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    Log.d(TAG, "Install aborted by user")

                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.e(TAG, "Install failed (status=$status): $msg")
                }
            }
        }
    }

    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            // Explicit, package-scoped intent, so the receiver is not exported.
            ContextCompat.registerReceiver(
                context.applicationContext,
                statusReceiver,
                IntentFilter(INSTALL_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    /**
     * Streams [apkFile] into a new install session and commits it.
     *
     * @return true if the session was created and committed; false if it could not be
     * started (e.g. the package installer rejected the session).
     */
    fun install(context: Context, apkFile: File): Boolean {
        val appContext = context.applicationContext
        ensureReceiver(appContext)

        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )

        var sessionId = -1
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("extension.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val statusIntent = Intent(INSTALL_ACTION).setPackage(appContext.packageName)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val pending = PendingIntent.getBroadcast(appContext, sessionId, statusIntent, flags)
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install extension APK", e)
            if (sessionId >= 0) {
                runCatching { installer.abandonSession(sessionId) }
            }
            false
        }
    }
}
