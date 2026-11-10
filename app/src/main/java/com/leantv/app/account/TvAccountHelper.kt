package com.leantv.app.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

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

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
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

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }
}
