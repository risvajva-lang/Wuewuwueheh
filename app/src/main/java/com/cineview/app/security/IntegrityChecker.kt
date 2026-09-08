package com.cineview.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object IntegrityChecker {
    data class Result(
        val ok: Boolean,
        val debuggable: Boolean,
        val installer: String?,
        val signingSha256: String?,
        val emulator: Boolean,
        val rooted: Boolean
    )

    fun check(context: Context): Result {
        val ai = context.applicationInfo
        val debuggable = (ai.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val installer = try {
            if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) {
            null
        }

        val digest = try {
            val info = if (Build.VERSION.SDK_INT >= 28) {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull()
            }
            info?.let {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { byte -> "%02X".format(byte) }
            }
        } catch (_: Exception) {
            null
        }

        val emulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)

        val rooted = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk",
            "/data/adb/modules"
        ).any { java.io.File(it).exists() } ||
            Build.TAGS?.contains("test-keys") == true

        return Result(
            ok = !debuggable && !emulator && !rooted,
            debuggable = debuggable,
            installer = installer,
            signingSha256 = digest,
            emulator = emulator,
            rooted = rooted
        )
    }
}
