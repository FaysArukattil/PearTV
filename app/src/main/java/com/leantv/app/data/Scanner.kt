package com.leantv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Lists system apps on this TV and decides what is safe to show.
 *
 * Safety model:
 *  1. Hard-protected packages (framework, Google core, chipset vendors, this app) are never listed.
 *  2. Anything that provides live TV inputs, the only launcher or the only keyboard is protected.
 *  3. Unknown system apps are listed as "Advanced" and never preselected.
 *  4. Only the bundled catalog can mark an app Recommended or Optional.
 */
class Scanner(private val ctx: Context, private val catalog: Catalog) {

    data class Result(val apps: List<AppItem>, val protectedCount: Int, val allSystem: Set<String>)

    private val protectedPrefixes = listOf(
        "android", "com.android.",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.tv",
        "com.google.android.leanbacklauncher", "com.google.android.inputmethod",
        "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.google.android.webview", "com.google.android.apps.mediashell",
        "com.google.android.ext", "com.google.android.modulemetadata",
        "com.google.android.overlay", "com.google.android.captiveportallogin",
        "com.google.android.networkstack", "com.google.android.cellbroadcast",
        "com.google.android.documentsui", "com.google.android.configupdater",
        "com.google.android.tungsten",
        "com.mediatek", "com.amlogic", "com.droidlogic", "com.realtek", "com.mstar",
        "com.broadcom", "com.nvidia", "com.qualcomm", "com.qti", "com.sony.",
    )

    fun installedPackages(): Set<String> =
        ctx.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()

    fun scan(): Result {
        val pm = ctx.packageManager

        val home = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()
        val ime = pm.queryIntentServices(Intent("android.view.InputMethod"), 0)
            .map { it.serviceInfo.packageName }.toSet()
        val tvInput = pm.queryIntentServices(Intent("android.media.tv.TvInputService"), 0)
            .map { it.serviceInfo.packageName }.toSet()

        val apps = ArrayList<AppItem>()
        val all = HashSet<String>()
        var protectedCount = 0

        for (pi in pm.getInstalledPackages(0)) {
            val ai = pi.applicationInfo ?: continue
            val isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (!isSystem) continue
            val pkg = pi.packageName
            if (pkg == ctx.packageName) continue
            all.add(pkg)

            val entry = catalog.entries[pkg]
            val hardProtected = protectedPrefixes.any { pkg == it || pkg.startsWith(it) } ||
                pkg in tvInput ||
                (pkg in home && home.size <= 1) ||
                (pkg in ime && ime.size <= 1)
            if (hardProtected) {
                protectedCount++
                continue
            }

            val label = try {
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                pkg
            }

            val item = when {
                entry != null -> AppItem(pkg, entry.name, entry.tier, entry.why, !ai.enabled)
                pi.sharedUserId?.startsWith("android.uid.") == true -> {
                    protectedCount++
                    null
                }
                pkg in home -> AppItem(pkg, label, Tier.ADVANCED, "Alternative home screen. Remove only if another one works.", !ai.enabled)
                else -> {
                    val hint = catalog.hints.firstOrNull { pkg.contains(it.contains, ignoreCase = true) }
                    val why = hint?.why ?: if (pkg.startsWith("com.xiaomi") || pkg.startsWith("com.mitv")) {
                        "Xiaomi system component. Remove only if you know what it does."
                    } else {
                        "Unknown system app. Remove only if you know what it does."
                    }
                    AppItem(pkg, label, Tier.ADVANCED, why, !ai.enabled)
                }
            }
            if (item != null) apps.add(item)
        }

        apps.sortWith(compareBy<AppItem> { it.tier.ordinal }.thenBy { it.label.lowercase() })
        return Result(apps, protectedCount, all)
    }
}
