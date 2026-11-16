package com.peartv.app.data

enum class Tier(val label: String) {
    RECOMMENDED("Recommended"),
    OPTIONAL("Optional"),
    ADVANCED("Advanced"),
}

enum class RemovalMethod(val label: String, val hint: String) {
    UNINSTALL("Remove", "Restorable any time from Restore."),
    DISABLE("Disable", "Turns apps off but keeps them installed."),
}

data class AppItem(
    val pkg: String,
    val label: String,
    val tier: Tier,
    val why: String,
    val disabled: Boolean,
)

data class Removed(
    val pkg: String,
    val label: String,
    val method: RemovalMethod,
    val at: Long,
)

enum class TvFamily(val label: String) {
    XIAOMI("Xiaomi Mi TV"),
    SONY("Sony Bravia"),
    TCL("TCL TV"),
    HISENSE("Hisense TV"),
    PHILIPS("Philips TV"),
    ONEPLUS("OnePlus TV"),
    REALME("Realme TV"),
    NVIDIA("Nvidia Shield"),
    FIRE_TV("Amazon Fire TV"),
    GENERIC("Android TV"),
}

data class DeviceProfile(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val hardware: String,
    val android: String,
    val sdk: Int,
    val patch: String,
    val ramTotalMb: Long,
    val ramFreeMb: Long,
    val storageTotalGb: Double,
    val storageFreeGb: Double,
    val cores: Int,
    val abi: String,
    val resolution: String,
    val uptimeHours: Long,
    val family: TvFamily,
) {
    val displayName: String
        get() = if (family == TvFamily.GENERIC) "$manufacturer $model".trim() else family.label

    val advice: String
        get() = when {
            ramTotalMb < 1300 -> "This TV has about 1 GB of memory. Removing background apps usually makes a clear difference."
            ramTotalMb < 2300 -> "This TV has about 2 GB of memory. A cleanup should make menus and app launches feel quicker."
            else -> "This TV has plenty of memory. Expect a lighter touch, mostly less clutter and fewer trackers."
        }
}

enum class Screen { Welcome, Setup, Dashboard, InstalledApps, Debloat, Tweaks, Restore }

enum class Filter(val label: String) {
    RECOMMENDED("Suggested"),
    TELEMETRY_ADS("Ads & Trackers"),
    OEM_BLOAT("OEM Bloat"),
    STREAMING("Streaming"),
    REVIEW("Review"),
    ALL("All"),
}

sealed interface Conn {
    data object Idle : Conn
    data object Checking : Conn
    data class Failed(val title: String, val detail: String) : Conn
    data class Connected(val host: String) : Conn
}

data class TweakStates(val animations: Boolean = false, val dns: Boolean = false)

data class ConfirmInfo(val title: String, val body: String, val action: String)

data class MajorDebloatOption(
    val id: String,
    val title: String,
    val description: String,
    val packages: List<String>,
    val isSelectedForRemoval: Boolean,
)

object DebloatPresets {
    fun defaultMajorOptions(): List<MajorDebloatOption> = listOf(
        MajorDebloatOption(
            id = "miracast",
            title = "Screen Mirroring / Miracast & Chromecast",
            description = "Keep if you mirror your phone, tablet, or PC screen to this TV.",
            packages = listOf(
                "com.google.android.apps.mediashell",
                "com.xiaomi.mitv.smartshare",
                "com.mitv.smartshare",
                "com.tcl.miracast",
                "com.hisense.tv.miracast",
                "com.sony.dtv.braviamiracast"
            ),
            isSelectedForRemoval = false // Keep by default
        ),
        MajorDebloatOption(
            id = "assistant",
            title = "Google Voice Assistant",
            description = "Keep if you use the microphone button on your remote for voice commands.",
            packages = listOf("com.google.android.katniss", "com.xiaomi.voicecontrol"),
            isSelectedForRemoval = false // Keep by default
        ),
        MajorDebloatOption(
            id = "netflix",
            title = "Netflix",
            description = "Preinstalled Netflix TV streaming application.",
            packages = listOf("com.netflix.ninja"),
            isSelectedForRemoval = false // Keep by default
        ),
        MajorDebloatOption(
            id = "prime",
            title = "Amazon Prime Video",
            description = "Preinstalled Prime Video streaming application.",
            packages = listOf("com.amazon.amazonvideo.livingroom"),
            isSelectedForRemoval = false // Keep by default
        ),
        MajorDebloatOption(
            id = "launcher",
            title = "Stock TV Home Launcher (PatchWall / ATV)",
            description = "Manufacturer ad-heavy home screen. Pear TV replaces this with a clean home screen.",
            packages = listOf(
                "com.mitv.tvhome.atv",
                "com.mitv.tvhome",
                "com.google.android.tvlauncher",
                "com.google.android.tvrecommendations"
            ),
            isSelectedForRemoval = true // Remove by default
        ),
        MajorDebloatOption(
            id = "oem_media",
            title = "OEM Video Player & Gallery",
            description = "Preinstalled manufacturer media players with promo recommendations.",
            packages = listOf(
                "com.mitv.videoplayer",
                "com.mitv.gallery",
                "com.xiaomi.mitv.tvplayer",
                "com.xiaomi.mitv.audioplayer"
            ),
            isSelectedForRemoval = true // Remove by default
        ),
    )
}

data class UiState(
    val screen: Screen,
    val conn: Conn = Conn.Idle,
    val device: DeviceProfile? = null,
    val apps: List<AppItem> = emptyList(),
    val protectedCount: Int = 0,
    val scanning: Boolean = false,
    val selected: Set<String> = emptySet(),
    val filter: Filter = Filter.RECOMMENDED,
    val method: RemovalMethod = RemovalMethod.UNINSTALL,
    val removed: List<Removed> = emptyList(),
    val tweaks: TweakStates = TweakStates(),
    val busy: String? = null,
    val progress: Pair<Int, Int>? = null,
    val toast: String? = null,
    val confirm: ConfirmInfo? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val installedAppsLoading: Boolean = false,
    val appTypeFilter: AppTypeFilter = AppTypeFilter.ALL,
    val appSortOrder: AppSortOrder = AppSortOrder.NAME,
    val appSearchQuery: String = "",
    val showCleanWizard: Boolean = false,
    val majorOptions: List<MajorDebloatOption> = DebloatPresets.defaultMajorOptions(),
)
