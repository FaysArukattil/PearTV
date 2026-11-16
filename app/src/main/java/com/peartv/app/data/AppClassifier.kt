package com.peartv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.peartv.app.storage.AppStorageDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The four buckets shown inside Storage -> Apps. */
enum class AppGroup(val title: String) {
    INSTALLED("Installed"),   // apps the user added (not part of the ROM)
    SYSTEM("System"),         // protected: the TV needs these to run - cannot be removed
    BLOAT("Bloat"),           // ROM apps that are NOT needed -> can be removed / disabled
    REMOVED("Removed")        // disabled or uninstalled by CleanTV -> can be restored
}

data class ClassifiedApp(
    val pkg: String,
    val label: String,
    val group: AppGroup,
    val tier: Tier?,              // only meaningful for BLOAT
    val reason: String,           // one-line explanation shown in the inspector
    val isSystemFlag: Boolean,    // came with the ROM (even if the user updated it)
    val installed: Boolean,       // still present on the device (false = uninstalled for user 0)
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

/**
 * Decides which tab every package belongs to.
 *
 * SAFETY MODEL (bias = "when in doubt, protect"):
 *  - Anything the TV needs to boot, show the launcher, switch inputs, use Wi-Fi / Bluetooth,
 *    cast, type with a keyboard or open Settings is SYSTEM and has NO remove button.
 *  - Only ROM apps that are provably optional (catalog / OEM / Google extras) land in BLOAT.
 *  - Disabled or removed packages always go to REMOVED so they can be restored.
 */
object AppClassifier {

    /** AOSP / Google packages that look "core" but are optional extras. Checked BEFORE the protected rules. */
    private val bloatExceptions = listOf(
        "com.android.htmlviewer", "com.android.bips", "com.android.printspooler", "com.android.printservice",
        "com.android.traceur", "com.android.dreams", "com.android.wallpaper", "com.android.wallpaperbackup",
        "com.android.egg", "com.android.development", "com.android.soundpicker", "com.android.calendar",
        "com.android.email", "com.android.gallery", "com.android.gallery3d", "com.android.music",
        "com.android.camera", "com.android.camera2", "com.android.deskclock", "com.android.browser",
        "com.android.quicksearchbox", "com.android.onetimeinitializer", "com.android.dialer",
        "com.android.mms", "com.android.messaging", "com.android.contacts", "com.android.apps.tag",
        "com.android.musicfx", "com.android.calculator2", "com.android.stk",
        "com.google.android.googlequicksearchbox", "com.google.android.katniss",
        "com.google.android.tvrecommendations", "com.google.android.backdrop", "com.google.android.feedback",
        "com.google.android.tv.bugreportsender", "com.google.android.play.games", "com.google.android.videos",
        "com.google.android.music",
        "com.google.android.printservice.recommendation", "com.google.android.partnersetup",
        "com.google.android.onetimeinitializer",
    )

    /**
     * Big user-facing apps that ship with the ROM. They are NOT bloat and NOT protected:
     * they show under "Installed" so you decide whether to keep them.
     */
    private val keepAsApps = listOf(
        "com.google.android.youtube", "com.google.android.apps.youtube",
        "com.netflix", "com.amazon.amazonvideo", "com.amazon.avod", "com.disney", "com.apple.atve",
        "com.spotify", "com.hotstar", "in.startv.hotstar", "com.jio", "com.sonyliv", "com.zee5",
        "tv.twitch", "com.plexapp",
    )

    /** Known Settings apps (exact or prefix). */
    private val settingsPackages = listOf(
        "com.android.tv.settings", "com.android.settings", "com.google.android.tvsettings",
        "com.google.android.apps.tv.settings", "com.android.providers.settings",
    )

    /** Core packages (exact or prefix). */
    private val essentialPrefixes = listOf(
        "android", "com.android.",                       // AOSP default = protected (exceptions above)
        "com.google.android.gms",                        // Play services (casting discovery, sign-in, push)
        "com.google.android.gsf",
        "com.google.android.webview", "com.google.android.trichromelibrary",
        "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.google.android.ext", "com.google.android.modulemetadata",
        "com.google.android.captiveportallogin", "com.google.android.networkstack",
        "com.google.android.documentsui", "com.google.android.configupdater",
        "com.google.android.tv.frameworkpackagestubs",
        "com.google.android.tungsten.setupwraith", "com.google.android.setupwizard",
        "com.google.android.apps.mediashell",            // Chromecast built-in (basic casting)
        "com.google.android.tv.remote.service",          // phone remote / Bluetooth remote pairing
        "com.google.android.inputmethod",                // Gboard
        "com.google.android.tts", "com.google.android.marvin.talkback", "com.google.android.accessibility",
        "com.google.android.syncadapters", "com.google.android.backuptransport",
        "com.google.android.overlay",
    )

    /** SoC / hardware vendors. Never touch. */
    private val chipsetPrefixes = listOf(
        "com.mediatek", "com.amlogic", "com.droidlogic", "com.realtek", "com.mstar", "com.broadcom",
        "com.nvidia", "com.qualcomm", "com.qti", "org.codeaurora", "com.rockchip", "com.hisilicon",
        "com.sprd", "com.unisoc", "com.novatek", "vendor",
    )

    /** Package-name fragments that mean "network / casting / input / settings / overlay" on OEM ROMs. */
    private val essentialKeywords = listOf(
        ".overlay", "overlay.", "framework", "wifi", "bluetooth", "setting", "tvservice", "keyboard",
        "inputmethod", ".ime", "miracast", "mediashell", "wifidisplay", ".wfd", "hdmi", ".cec", "cec.",
        "provision", "setupwizard", "setupwraith", "packageinstaller", "permission",
    )

    private fun matches(pkg: String, list: List<String>): Boolean =
        list.any { pkg == it || pkg.startsWith(if (it.endsWith(".")) it else "$it.") }

    private data class Verdict(val group: AppGroup, val tier: Tier?, val reason: String)

    suspend fun classify(
        ctx: Context,
        journal: Journal,
        scanner: Scanner,
        storage: Map<String, AppStorageDetail>,
    ): List<ClassifiedApp> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val own = ctx.packageName

        // ---- dynamic facts from the live device ----
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePkgs = runCatching {
            pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        val defaultHome = runCatching {
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        val ourLauncherIsHome = defaultHome == own

        val imePkgs = runCatching {
            pm.queryIntentServices(Intent("android.view.InputMethod"), 0).map { it.serviceInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        val tvInputPkgs = runCatching {
            pm.queryIntentServices(Intent("android.media.tv.TvInputService"), 0).map { it.serviceInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        val settingsPkgs = HashSet<String>()
        for (action in listOf(Settings.ACTION_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS)) {
            runCatching {
                pm.queryIntentActivities(Intent(action), 0).forEach { settingsPkgs.add(it.activityInfo.packageName) }
            }
        }

        val scanInfo: Map<String, AppItem> =
            runCatching { scanner.scan().apps.associateBy { it.pkg } }.getOrDefault(emptyMap())
        val removedByPkg = journal.removed().associateBy { it.pkg }

        val installedPkgs = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val out = ArrayList<ClassifiedApp>(installedPkgs.size + removedByPkg.size)
        val seen = HashSet<String>()

        for (pi in installedPkgs) {
            val ai = pi.applicationInfo ?: continue
            val pkg = pi.packageName
            if (pkg == own) continue
            seen.add(pkg)

            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
            val isSys = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val st = storage[pkg]

            val verdict = when {
                !ai.enabled || pkg in removedByPkg ->
                    Verdict(AppGroup.REMOVED, null, "Disabled - tap Restore to turn it back on.")
                !isSys ->
                    Verdict(AppGroup.INSTALLED, null, "Installed by you.")
                else -> systemVerdict(
                    pkg = pkg,
                    label = label,
                    sharedUid = pi.sharedUserId,
                    scan = scanInfo[pkg],
                    homePkgs = homePkgs,
                    ourLauncherIsHome = ourLauncherIsHome,
                    imePkgs = imePkgs,
                    tvInputPkgs = tvInputPkgs,
                    settingsPkgs = settingsPkgs,
                )
            }

            out.add(
                ClassifiedApp(
                    pkg = pkg, label = label, group = verdict.group, tier = verdict.tier,
                    reason = verdict.reason, isSystemFlag = isSys, installed = true,
                    appBytes = st?.appBytes ?: 0L, dataBytes = st?.dataBytes ?: 0L, cacheBytes = st?.cacheBytes ?: 0L,
                )
            )
        }

        // Packages removed with "pm uninstall -k --user 0" no longer appear in the installed list.
        for ((pkg, r) in removedByPkg) {
            if (pkg in seen) continue
            out.add(
                ClassifiedApp(
                    pkg = pkg, label = r.label, group = AppGroup.REMOVED, tier = null,
                    reason = "Removed by CleanTV - tap Restore to bring it back.",
                    isSystemFlag = true, installed = false,
                )
            )
        }
        out
    }

    private fun systemVerdict(
        pkg: String,
        label: String,
        sharedUid: String?,
        scan: AppItem?,
        homePkgs: Set<String>,
        ourLauncherIsHome: Boolean,
        imePkgs: Set<String>,
        tvInputPkgs: Set<String>,
        settingsPkgs: Set<String>,
    ): Verdict {
        fun system(why: String) = Verdict(AppGroup.SYSTEM, null, why)

        // Hard rule: the Settings app is NEVER bloat (LeanTV has no settings of its own).
        // Checked first so nothing else (e.g. a HOME intent-filter on TV Settings) can override it.
        if (pkg in settingsPkgs ||
            label.contains("settings", ignoreCase = true) ||
            matches(pkg, settingsPackages) ||
            pkg.contains("setting", ignoreCase = true)
        ) {
            return system("Android Settings - needed to configure the TV.")
        }

        // Live-device rules: keyboard and TV inputs
        if (pkg in imePkgs) return system("Keyboard / input method - needed for typing.")
        if (pkg in tvInputPkgs) return system("Provides TV inputs (HDMI, tuner, live channels).")

        // Streaming apps (YouTube, Netflix, ...) are regular apps, not bloat.
        if (matches(pkg, keepAsApps)) {
            return Verdict(AppGroup.INSTALLED, null, "Pre-installed app. Keep it or uninstall it - your choice.")
        }

        // Static protection rules (skipped for known optional extras)
        val isException = matches(pkg, bloatExceptions)
        if (!isException) {
            if (matches(pkg, chipsetPrefixes)) return system("Hardware / chipset service.")
            if (matches(pkg, essentialPrefixes)) return system("Core Android service - the TV needs it to run.")
            if (essentialKeywords.any { pkg.contains(it, ignoreCase = true) }) {
                return system("Network, casting, input or settings service.")
            }
            if (sharedUid?.startsWith("android.uid.") == true) {
                return system("Runs inside the Android system process.")
            }
        }

        // Launchers LAST: only a stock launcher that matched no protection above can be bloat.
        if (pkg in homePkgs) {
            if (!ourLauncherIsHome) return system("Current home screen - needed until LeanTV is your default launcher.")
            return Verdict(
                AppGroup.BLOAT, Tier.ADVANCED,
                "Stock launcher. Not needed while LeanTV is your home screen. Keep it if you want a fallback."
            )
        }

        // 4) Everything else that shipped with the ROM = bloat
        if (scan != null) return Verdict(AppGroup.BLOAT, scan.tier, scan.why)
        return Verdict(
            AppGroup.BLOAT, Tier.ADVANCED,
            "Pre-installed app that the TV does not need for basic use. Review before removing."
        )
    }
}