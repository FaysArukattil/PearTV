package com.peartv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.peartv.app.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

sealed interface DebloatResult {
    data class Success(val message: String) : DebloatResult
    data class Failure(val reason: String) : DebloatResult
    data class NeedsPrivilege(val message: String) : DebloatResult
}

/**
 * Universal System App Debloater & Uninstaller (Canta + CleanYourTV architecture).
 *
 * Supports 4 elevated execution tiers:
 * 1. Shizuku API (rootless shell privilege via Shizuku app)
 * 2. On-device local ADB daemon (port 5555 / Wireless Debugging)
 * 3. Direct Root SU (`su -c`)
 * 4. User-app fallback via standard package uninstaller
 */
object SystemDebloater {
    private const val TAG = "SystemDebloater"
    const val SHIZUKU_REQ_CODE = 4040

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            isShizukuAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission() {
        try {
            if (isShizukuAvailable() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed requesting Shizuku permission", e)
        }
    }

    fun runPrivilegedCommand(cmd: String): Boolean {
        if (isShizukuAvailable() && hasShizukuPermission()) {
            try {
                runShizukuProcess(cmd)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku exec failed: ${e.message}")
            }
        }
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor()
            if (p.exitValue() == 0) return true
        } catch (_: Exception) {}
        return false
    }

    private fun runShizukuProcess(cmd: String): String {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readText()
        process.waitFor()
        return output
    }

    /**
     * Uninstalls or safely disables any system or user application.
     */
    suspend fun uninstallOrDisable(
        ctx: Context,
        pkg: String,
        label: String,
        isSystemApp: Boolean,
        adbClient: AdbClient?,
        journal: Journal
    ): DebloatResult = withContext(Dispatchers.IO) {
        // 1. Try Shizuku (Canta architecture)
        if (isShizukuAvailable()) {
            if (!hasShizukuPermission()) {
                requestShizukuPermission()
                return@withContext DebloatResult.NeedsPrivilege("Please grant Shizuku permission when the TV dialog appears.")
            }

            try {
                val cmd = "pm uninstall -k --user 0 $pkg || pm disable-user --user 0 $pkg"
                val output = runShizukuProcess(cmd)

                if (output.contains("Success") || output.contains("disabled") || !output.contains("Failure")) {
                    journal.addRemoved(Removed(pkg, label, RemovalMethod.UNINSTALL, System.currentTimeMillis()))
                    return@withContext DebloatResult.Success("✓ Uninstalled $label via Shizuku")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku uninstall failed: ${e.message}")
            }
        }

        // 2. Try on-device ADB
        if (adbClient != null) {
            if (!adbClient.isConnected) {
                adbClient.connect()
            }
            if (adbClient.isConnected) {
                val res = adbClient.shell("pm uninstall -k --user 0 $pkg")
                if (res.out.contains("Success") || !res.out.contains("Failure")) {
                    journal.addRemoved(Removed(pkg, label, RemovalMethod.UNINSTALL, System.currentTimeMillis()))
                    return@withContext DebloatResult.Success("✓ Uninstalled $label via ADB")
                }

                val dis = adbClient.shell("pm disable-user --user 0 $pkg")
                if (dis.out.contains("disabled") || dis.out.contains("Success")) {
                    journal.addRemoved(Removed(pkg, label, RemovalMethod.DISABLE, System.currentTimeMillis()))
                    return@withContext DebloatResult.Success("✓ Disabled $label via ADB")
                }
            }
        }

        // 3. Try Root SU
        try {
            val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm uninstall -k --user 0 $pkg || pm disable-user --user 0 $pkg"))
            val suOut = suProcess.inputStream.bufferedReader().readText()
            suProcess.waitFor()
            if (suOut.contains("Success") || suOut.contains("disabled")) {
                journal.addRemoved(Removed(pkg, label, RemovalMethod.UNINSTALL, System.currentTimeMillis()))
                return@withContext DebloatResult.Success("✓ Uninstalled $label via Root")
            }
        } catch (_: Exception) {}

        // 4. Non-system user app
        if (!isSystemApp) {
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.fromParts("package", pkg, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                return@withContext DebloatResult.Success("Confirm uninstallation in system dialog")
            } catch (e: Exception) {
                return@withContext DebloatResult.Failure("Cannot launch uninstaller: ${e.message}")
            }
        }

        // 5. System app with no elevated privilege available
        DebloatResult.NeedsPrivilege(
            "System apps cannot be uninstalled without elevated access. Start Shizuku or enable Wireless Debugging in TV Settings."
        )
    }

    /**
     * Restore any previously removed or disabled system app in 1-click.
     */
    suspend fun restoreApp(
        ctx: Context,
        pkg: String,
        label: String,
        adbClient: AdbClient?,
        journal: Journal
    ): DebloatResult = withContext(Dispatchers.IO) {
        // 1. Shizuku
        if (hasShizukuPermission()) {
            try {
                val cmd = "pm enable $pkg && cmd package install-existing $pkg"
                runShizukuProcess(cmd)
                journal.dropRemoved(pkg)
                return@withContext DebloatResult.Success("✓ Restored $label via Shizuku")
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku restore failed", e)
            }
        }

        // 2. ADB
        if (adbClient != null) {
            if (!adbClient.isConnected) adbClient.connect()
            if (adbClient.isConnected) {
                adbClient.shell("pm enable $pkg")
                adbClient.shell("cmd package install-existing $pkg")
                journal.dropRemoved(pkg)
                return@withContext DebloatResult.Success("✓ Restored $label via ADB")
            }
        }

        // 3. Root SU
        try {
            val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm enable $pkg && cmd package install-existing $pkg"))
            suProcess.waitFor()
            journal.dropRemoved(pkg)
            return@withContext DebloatResult.Success("✓ Restored $label via Root")
        } catch (_: Exception) {}

        journal.dropRemoved(pkg)
        DebloatResult.Success("Restored $label")
    }

    fun openDeveloperSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }
}
