package com.peartv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Lists system apps on this TV and decides what is safe to show.
 *
 * Safety model:
 *  1. Hard-protected packages (framework, Google core services, essential hardware HALs, this app) are never listed.
 *  2. Anything that provides live TV inputs, the single active launcher or the single active keyboard is protected.
 *  3. Ads, telemetry, recommendations, and bloatware identified by catalog or keywords are tagged.
 *  4. Unknown system apps are listed as "Advanced" and never preselected.
 */
class Scanner(private val ctx: Context, private val catalog: Catalog) {

    data class Result(
        val apps: List<AppItem>,
        val protectedCount: Int,
        val allSystem: Set<String>,
        val telemetryAndAds: List<AppItem>,
        val oemBloat: List<AppItem>,
        val streamingApps: List<AppItem>,
    )

    // Critical system infrastructure that should never be removed under any circumstance
    private val protectedPrefixes = listOf(
        "android",
        "com.android.systemui",
        "com.android.server",
        "com.android.providers.settings",
        "com.android.settings",
        "com.android.keyguard",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.defcontainer",
        "com.android.pacprocessor",
        "com.android.proxyhandler",
        "com.android.dynsystem",
        "com.android.networkstack",
        "com.android.wifi.resources",
        "com.android.internal",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.webview",
        "com.google.android.ext.services",
        "com.google.android.ext.shared",
        "com.google.android.modulemetadata",
        "com.google.android.captiveportallogin",
        "com.google.android.networkstack",
        "com.google.android.documentsui",
        "com.google.android.configupdater",
        "com.google.android.tv.frameworkpackagestubs",
        // Chipset and hardware drivers
        "com.mediatek.engineermode",
        "com.mediatek.factorymode",
        "com.amlogic.tv",
        "com.droidlogic.tv",
        "com.realtek",
        "com.mstar",
        "com.broadcom",
        "com.nvidia.shield.remote.service",
        "com.qualcomm.qti",
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
            val isProtectedPrefix = protectedPrefixes.any { pkg == it || pkg.startsWith("$it.") }
            val isVitalTvInput = pkg in tvInput && (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android."))
            val isSoleHome = pkg in home && home.size <= 1
            val isSoleIme = pkg in ime && ime.size <= 1

            if (isProtectedPrefix || isVitalTvInput || isSoleHome || isSoleIme) {
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
                pi.sharedUserId?.startsWith("android.uid.system") == true && pkg.startsWith("com.android.") -> {
                    protectedCount++
                    null
                }
                pkg in home -> AppItem(
                    pkg, label, Tier.ADVANCED,
                    "Alternative home screen. Disable only when LeanTV is working as default.",
                    !ai.enabled
                )
                else -> {
                    val hint = catalog.hints.firstOrNull { pkg.contains(it.contains, ignoreCase = true) }
                    if (hint != null) {
                        AppItem(pkg, label, Tier.RECOMMENDED, hint.why, !ai.enabled)
                    } else if (pkg.startsWith("com.xiaomi") || pkg.startsWith("com.mitv")) {
                        AppItem(pkg, label, Tier.OPTIONAL, "Xiaomi preinstalled app/service. Safe to debloat.", !ai.enabled)
                    } else if (pkg.startsWith("com.tcl.") || pkg.startsWith("com.hisense.") || pkg.startsWith("com.sony.dtv")) {
                        AppItem(pkg, label, Tier.OPTIONAL, "OEM manufacturer preinstalled service.", !ai.enabled)
                    } else {
                        AppItem(pkg, label, Tier.ADVANCED, "Unknown system app. Review before modifying.", !ai.enabled)
                    }
                }
            }
            if (item != null) apps.add(item)
        }

        apps.sortWith(compareBy<AppItem> { it.tier.ordinal }.thenBy { it.label.lowercase() })

        val telemetryAndAds = apps.filter {
            it.tier == Tier.RECOMMENDED ||
            it.pkg.contains("adservice", ignoreCase = true) ||
            it.pkg.contains("advertise", ignoreCase = true) ||
            it.pkg.contains("analytics", ignoreCase = true) ||
            it.pkg.contains("telemetry", ignoreCase = true) ||
            it.pkg.contains("tracking", ignoreCase = true) ||
            it.pkg.contains("statistic", ignoreCase = true) ||
            it.pkg.contains("recommendation", ignoreCase = true) ||
            it.pkg.contains("feedback", ignoreCase = true)
        }

        val streamingApps = apps.filter {
            it.pkg in listOf(
                "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom",
                "com.jio.media.ondemand",
                "com.hotstar.tv",
                "com.sonyliv.tv",
                "com.zee5.tv",
                "com.voot.tv",
                "com.altbalaji.tv",
                "com.hungama.tv",
                "com.erosnow.tv",
                "com.spottv",
                "com.google.android.youtube.tvmusic",
                "com.google.android.youtube.tvunplugged"
            )
        }

        val oemBloat = apps.filter {
            it.tier == Tier.OPTIONAL && it !in streamingApps && it !in telemetryAndAds
        }

        return Result(
            apps = apps,
            protectedCount = protectedCount,
            allSystem = all,
            telemetryAndAds = telemetryAndAds,
            oemBloat = oemBloat,
            streamingApps = streamingApps,
        )
    }
}
