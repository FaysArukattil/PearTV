package com.leantv.app.inputs

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * CleanYourTV Physical Hardware TV Input representation.
 */
data class TvPort(
    val id: String,
    val label: String,
    val typeName: String,
    val isConnected: Boolean,
    val isStandby: Boolean,
    val isPassthrough: Boolean,
    /** "TV", "HDMI", "AV", "COMPONENT" or "OTHER" - used for icon and ordering. */
    val kind: String = "OTHER",
    /** e.g. the CEC device plugged into an HDMI port ("Fire TV Stick"). */
    val subtitle: String? = null,
)

object TvInputHelper {
    private const val TAG = "TvInputHelper"

    /**
     * Only real TV sources: built-in TV tuner, HDMI ports, AV and Component.
     * Apps that register themselves as TV inputs (VLC, Zee5, other streaming/IPTV apps) are dropped.
     */
    fun queryHardwareInputs(ctx: Context): List<TvPort> {
        val manager = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        val hardwarePorts: List<TvPort> = try {
            if (manager == null) emptyList()
            else {
                val all = manager.tvInputList ?: emptyList()

                // CEC devices (e.g. a Fire Stick) appear as child inputs of an HDMI port.
                // Keep the port, and show the device name as its subtitle.
                val cecNames = HashMap<String, String>()
                all.forEach { info ->
                    val parent = parentIdOf(info)
                    if (parent != null) {
                        val name = cecNameOf(ctx, info)
                        if (name != null) cecNames[parent] = name
                    }
                }
                val presentIds = all.map { it.id }.toSet()

                val kept = all.filter { info ->
                    val parent = parentIdOf(info)
                    if (parent != null && parent in presentIds) return@filter false // child of a listed port
                    when (info.type) {
                        TvInputInfo.TYPE_HDMI,
                        TvInputInfo.TYPE_COMPOSITE,
                        TvInputInfo.TYPE_COMPONENT,
                        TvInputInfo.TYPE_DISPLAY_PORT,
                        TvInputInfo.TYPE_VGA,
                        TvInputInfo.TYPE_DVI -> true
                        else -> false
                    }
                }

                val listToUse = if (kept.isNotEmpty()) kept else all.filter { parentIdOf(it) == null }

                // Only one "TV" entry, even if the OEM registers several tuner services.
                var tunerSeen = false
                val unique = listToUse.filter { info ->
                    if (info.type != TvInputInfo.TYPE_TUNER) true
                    else if (tunerSeen) false
                    else { tunerSeen = true; true }
                }

                unique.map { info ->
                    val state = try { manager.getInputState(info.id) } catch (_: Exception) { TvInputManager.INPUT_STATE_DISCONNECTED }
                    val kind = when (info.type) {
                        TvInputInfo.TYPE_TUNER -> "TV"
                        TvInputInfo.TYPE_HDMI -> "HDMI"
                        TvInputInfo.TYPE_COMPOSITE -> "AV"
                        TvInputInfo.TYPE_COMPONENT -> "COMPONENT"
                        else -> "OTHER"
                    }
                    val rawLabel = try {
                        val custom = info.loadCustomLabel(ctx)?.toString()
                        if (!custom.isNullOrBlank()) custom else info.loadLabel(ctx)?.toString()
                    } catch (_: Exception) { null }
                    val label = when (kind) {
                        "TV" -> "TV"
                        "AV" -> "AV"
                        "COMPONENT" -> "Component"
                        else -> if (!rawLabel.isNullOrBlank()) rawLabel else getHumanReadablePortName(info)
                    }
                    TvPort(
                        id = info.id,
                        label = label,
                        typeName = getTypeName(info.type),
                        isConnected = state == TvInputManager.INPUT_STATE_CONNECTED || kind == "TV",
                        isStandby = state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY,
                        isPassthrough = info.isPassthroughInput,
                        kind = kind,
                        subtitle = cecNames[info.id]?.takeIf { !it.equals(label, ignoreCase = true) },
                    )
                }.sortedWith(
                    compareBy<TvPort> {
                        when (it.kind) { "TV" -> 0; "HDMI" -> 1; "AV" -> 2; "COMPONENT" -> 3; else -> 4 }
                    }.thenBy { it.label }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying TV inputs", e)
            emptyList()
        }

        if (hardwarePorts.isNotEmpty()) {
            return hardwarePorts
        }

        // Fallback: If no ports were detected from the system TV service (e.g. TV box or custom ROM),
        // present standard ports matching our aesthetic that trigger the native input switchers
        return listOf(
            TvPort(id = "default_hdmi1", label = "HDMI 1", typeName = "HDMI", isConnected = true, isStandby = false, isPassthrough = true, kind = "HDMI", subtitle = null),
            TvPort(id = "default_hdmi2", label = "HDMI 2 (eARC)", typeName = "HDMI", isConnected = true, isStandby = false, isPassthrough = true, kind = "HDMI", subtitle = null),
            TvPort(id = "default_hdmi3", label = "HDMI 3", typeName = "HDMI", isConnected = false, isStandby = false, isPassthrough = true, kind = "HDMI", subtitle = null),
            TvPort(id = "default_av", label = "AV / Composite", typeName = "AV", isConnected = false, isStandby = false, isPassthrough = true, kind = "AV", subtitle = null)
        )
    }

    /** A real broadcast tuner is provided by the TV's own firmware, never by an installed app. */
    private fun isSystemTuner(ctx: Context, info: TvInputInfo): Boolean {
        val pkg = info.serviceInfo?.packageName ?: return false
        return try {
            val flags = ctx.packageManager.getApplicationInfo(pkg, 0).flags
            (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isHiddenInput(ctx: Context, info: TvInputInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 26) {
            try { info.isHidden(ctx) } catch (_: Exception) { false }
        } else false

    private fun parentIdOf(info: TvInputInfo): String? =
        if (Build.VERSION.SDK_INT >= 26) {
            try { info.parentId } catch (_: Exception) { null }
        } else null

    private fun cecNameOf(ctx: Context, info: TvInputInfo): String? {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                // getHdmiDeviceInfo() is @SystemApi (not in public compileSdk stubs); probe reflectively.
                val dev = TvInputInfo::class.java.getMethod("getHdmiDeviceInfo").invoke(info)
                val displayName = dev?.javaClass?.getMethod("getDisplayName")?.invoke(dev) as? String
                if (!displayName.isNullOrBlank()) return displayName
            } catch (_: Throwable) { /* @SystemApi may be restricted; fall back to child label */ }
        }
        return try {
            val custom = if (Build.VERSION.SDK_INT >= 24) info.loadCustomLabel(ctx)?.toString() else null
            val label = if (!custom.isNullOrBlank()) custom else info.loadLabel(ctx)?.toString()
            label?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Live reactive flow that emits updated port list when cables are plugged/unplugged.
     */
    fun observeInputs(ctx: Context): Flow<List<TvPort>> = callbackFlow {
        val manager = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        if (manager == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        fun sendUpdate() {
            trySend(queryHardwareInputs(ctx))
        }

        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputAdded(inputId: String?) = sendUpdate()
            override fun onInputRemoved(inputId: String?) = sendUpdate()
            override fun onInputStateChanged(inputId: String?, state: Int) = sendUpdate()
            override fun onTvInputInfoUpdated(inputInfo: TvInputInfo?) = sendUpdate()
        }

        try {
            manager.registerCallback(callback, Handler(Looper.getMainLooper()))
            sendUpdate()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register TvInputCallback", e)
            sendUpdate()
        }

        awaitClose {
            try {
                manager.unregisterCallback(callback)
            } catch (_: Exception) {}
        }
    }

    /**
     * Switch directly to the selected hardware port with fallback intents.
     */
    fun switchToPort(ctx: Context, port: TvPort): Boolean {
        if (port.kind == "TV") {
            val tvIntents = listOf(
                Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
                Intent("android.intent.action.VIEW", Uri.parse("tv://channel")),
                Intent(Intent.ACTION_VIEW).setPackage("com.google.android.tv"),
                Intent(Intent.ACTION_VIEW).setPackage("com.android.tv")
            )
            for (intent in tvIntents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    return true
                } catch (_: Exception) {}
            }
        }

        // If port has a real system input ID (not default_*)
        if (!port.id.startsWith("default_")) {
            try {
                val channelUri: Uri = TvContract.buildChannelUriForPassthroughInput(port.id)
                val intent = Intent(Intent.ACTION_VIEW, channelUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct channel switch failed for port ${port.id}, trying fallback", e)
            }
        }

        // Fallback: OEM / TV Input actions
        val fallbacks = listOf(
            Intent("android.media.tv.action.VIEW_INPUTS"),
            Intent("com.android.tv.action.VIEW_INPUTS"),
            Intent("com.google.android.tv.action.VIEW_INPUTS"),
            Intent("android.intent.action.VIEW", Uri.parse("tv://passthrough/${port.id}")),
            Intent("android.settings.TV_INPUT_SETTINGS"),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )

        for (intent in fallbacks) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        return openNativeTvInputs(ctx)
    }

    /**
     * Opens the native TV input / source selector UI across OEM TV platforms.
     */
    fun openNativeTvInputs(ctx: Context): Boolean {
        val intents = listOf(
            // Android TV standard input actions
            Intent("android.media.tv.action.VIEW_INPUTS"),
            Intent("com.android.tv.action.VIEW_INPUTS"),
            Intent("com.google.android.tv.action.VIEW_INPUTS"),
            // Live TV / tuner uri
            Intent("android.intent.action.VIEW", TvContract.Channels.CONTENT_URI),
            // Sony BRAVIA
            Intent().setClassName("com.sony.dtv.tvinput.sources", "com.sony.dtv.tvinput.sources.SourcesActivity"),
            Intent().setClassName("com.sony.dtv.osat.viewinputs", "com.sony.dtv.osat.viewinputs.MainActivity"),
            // Xiaomi / PatchWall / Mi TV
            Intent("com.xiaomi.mitv.media.INPUT_SOURCE"),
            Intent("mitv.intent.action.INPUT_SOURCE"),
            Intent().setClassName("com.mitv.tvhome", "com.mitv.tvhome.SourceActivity"),
            // TCL
            Intent("com.tcl.tvinput.action.CHOOSE_INPUT"),
            Intent().setClassName("com.tcl.tvinput", "com.tcl.tvinput.InputSelectActivity"),
            // Hisense / VIDAA
            Intent("com.hisense.intent.action.SOURCE_SELECT"),
            Intent().setClassName("com.jamdeo.tv.source", "com.jamdeo.tv.source.SourceActivity"),
            // Philips
            Intent().setClassName("org.droidtv.settings", "org.droidtv.settings.inputs.SourceSelectionActivity"),
            // TV Settings Inputs
            Intent("android.settings.TV_INPUT_SETTINGS"),
            Intent("android.media.tv.action.SETUP_INPUTS"),
            Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS)
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

    private fun getHumanReadablePortName(info: TvInputInfo): String {
        val id = info.id.uppercase()
        return when {
            id.contains("HDMI4") -> "HDMI 4"
            id.contains("HDMI3") -> "HDMI 3"
            id.contains("HDMI2") -> "HDMI 2"
            id.contains("HDMI1") -> "HDMI 1"
            id.contains("HDMI") -> "HDMI"
            id.contains("AV") || id.contains("COMPOSITE") -> "AV / Composite"
            id.contains("COMPONENT") -> "Component"
            id.contains("VGA") -> "VGA"
            id.contains("DP") || id.contains("DISPLAYPORT") -> "DisplayPort"
            info.type == TvInputInfo.TYPE_TUNER -> "TV Tuner (Antenna)"
            else -> getTypeName(info.type)
        }
    }

    private fun getTypeName(type: Int): String = when (type) {
        TvInputInfo.TYPE_TUNER -> "Tuner"
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_COMPOSITE -> "AV"
        TvInputInfo.TYPE_COMPONENT -> "Component"
        TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
        TvInputInfo.TYPE_VGA -> "VGA"
        TvInputInfo.TYPE_DVI -> "DVI"
        else -> "Hardware Port"
    }
}
