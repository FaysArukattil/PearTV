package com.leantv.app.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.leantv.app.adb.AdbClient
import com.leantv.app.data.SystemDebloater

data class TvProfile(
    val email: String?,
    val displayName: String,
    val initial: String,
    val allAccounts: List<String>
)

object TvAccountHelper {
    private const val TAG = "TvAccountHelper"

    /**
     * Reads connected Google accounts on Android TV.
     */
    fun getConnectedProfile(ctx: Context): TvProfile {
        ensureOwnAccountPermission(ctx)
        return try {
            val am = AccountManager.get(ctx)
            val accounts: Array<Account> = am.getAccountsByType("com.google")
            if (accounts.isNotEmpty()) {
                val primary = accounts[0].name
                val namePart = primary.substringBefore("@")
                val displayName = namePart.replaceFirstChar { it.uppercase() }
                val initial = displayName.take(1).uppercase()
                TvProfile(
                    email = primary,
                    displayName = displayName,
                    initial = initial,
                    allAccounts = accounts.map { it.name }
                )
            } else {
                // Fallback: Check other account types
                val anyAccounts = am.accounts
                if (anyAccounts.isNotEmpty()) {
                    val primary = anyAccounts[0].name
                    TvProfile(
                        email = primary,
                        displayName = primary.substringBefore("@"),
                        initial = primary.take(1).uppercase(),
                        allAccounts = anyAccounts.map { it.name }
                    )
                } else {
                    TvProfile(
                        email = null,
                        displayName = "Add Account",
                        initial = "👤",
                        allAccounts = emptyList()
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching accounts", e)
            TvProfile(
                email = null,
                displayName = "Profile",
                initial = "👤",
                allAccounts = emptyList()
            )
        }
    }

    private const val GET_ACCOUNTS = "android.permission.GET_ACCOUNTS"

    fun hasAccountPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Without GET_ACCOUNTS the launcher can't see the TV's signed-in Google account, so Home showed
     * nothing. If the user hasn't granted it yet, try a silent grant (Shizuku / root); the activity
     * also asks for it on screen.
     */
    private fun ensureOwnAccountPermission(ctx: Context) {
        if (hasAccountPermission(ctx)) return
        try {
            SystemDebloater.runPrivilegedCommand("pm grant ${ctx.packageName} $GET_ACCOUNTS")
        } catch (_: Exception) {}
    }

    /**
     * Other apps (YouTube, Play Store...) can only list the TV's Google accounts when they hold
     * GET_ACCOUNTS. Grant it to every installed app that asks for it, so the system account is offered
     * there instead of a fresh sign-in. Returns how many apps were updated.
     * [allowAdbConnect] lets the user-triggered path connect to on-device ADB if it isn't already.
     */
    suspend fun grantAccountAccessToApps(ctx: Context, adb: AdbClient, allowAdbConnect: Boolean): Int {
        val pm = ctx.packageManager
        val pkgs = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .filter { pi ->
                    pi.packageName != ctx.packageName &&
                        pi.requestedPermissions?.contains(GET_ACCOUNTS) == true &&
                        pm.checkPermission(GET_ACCOUNTS, pi.packageName) != PackageManager.PERMISSION_GRANTED
                }
                .map { it.packageName }
        } catch (e: Exception) {
            Log.w(TAG, "Package scan failed", e)
            emptyList()
        }
        if (pkgs.isEmpty()) return 0

        val cmd = pkgs.joinToString("; ") { "pm grant $it $GET_ACCOUNTS" }
        var ok = false
        try {
            if (!adb.isConnected && allowAdbConnect) adb.connect()
            if (adb.isConnected) ok = adb.shell(cmd).code == 0
        } catch (_: Exception) {}
        if (!ok) ok = SystemDebloater.runPrivilegedCommand(cmd)
        return if (ok) pkgs.size else 0
    }

    private fun startNative(ctx: Context, intents: List<Intent>): Boolean {
        val pm = ctx.packageManager
        for (intent in intents) {
            try {
                if (pm.resolveActivity(intent, 0) == null) continue
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * One tap target for the Home corner account chip: the TV's own screens, no separate sign-in.
     * Signed in -> system Accounts & Sync (switch / add / remove). Not signed in -> system Add account.
     */
    fun openAccountsNative(ctx: Context, hasAccount: Boolean): Boolean {
        val add = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
            putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        }
        val manage = listOf(
            Intent(Settings.ACTION_SYNC_SETTINGS),
            Intent("android.settings.ACCOUNT_SYNC_SETTINGS"),
            add,
            Intent(Settings.ACTION_SETTINGS)
        )
        return startNative(ctx, if (hasAccount) manage else listOf(add) + manage)
    }

    /**
     * Launch native Google Account setup on TV.
     */
    fun openAddAccount(ctx: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            },
            Intent(Settings.ACTION_SYNC_SETTINGS),
            Intent("android.settings.ACCOUNT_SYNC_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )

        return startNative(ctx, intents)
    }

    /**
     * Launch Account Settings / Switcher on TV.
     */
    fun openAccountSettings(ctx: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_SYNC_SETTINGS),
            Intent("android.settings.ACCOUNT_SYNC_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )

        return startNative(ctx, intents)
    }
}
