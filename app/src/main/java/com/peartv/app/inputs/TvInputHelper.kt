package com.peartv.app.inputs

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
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
    /** True for placeholder ports shown when the TV doesn't expose its inputs to apps. */
    val statusUnknown: Boolean = false,
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
                        // Built-in antenna/cable tuner only (never an app that registers as a tuner).
                        TvInputInfo.TYPE_TUNER -> isSystemTuner(ctx, info)
                        else -> false
                    }
                }

                val listToUse = kept

                // Only one "TV" entry, even if the OEM registers several tuner services.
                var tunerSeen = false
                val unique = listToUse.filter { info ->
                    if (info.type != TvInputInfo.TYPE_TUNER) true
                    else if (tunerSeen) false
                    else { tunerSeen = true; true }
                }

                // Determine hardware HDMI port numbers via TvInputManager.getHardwareList()
                val hwPortMap = HashMap<Int, Int>() // hardwareId -> hdmiPort
                try {
                    val method = manager.javaClass.getMethod("getHardwareList")
                    val list = method.invoke(manager) as? List<*>
                    list?.forEach { item ->
                        if (item != null) {
                            val id = runCatching { item.javaClass.getMethod("getId").invoke(item) as? Int }.getOrNull()
                            val port = runCatching { item.javaClass.getMethod("getHdmiPort").invoke(item) as? Int }.getOrNull() ?: 0
                            if (id != null && port > 0) {
                                hwPortMap[id] = port
                            }
                        }
                    }
                } catch (_: Exception) {}

                val hdmiInfos = unique.filter { it.type == TvInputInfo.TYPE_HDMI }
                    .sortedBy { info ->
                        val hwId = Regex("HW(\\d+)").find(info.id)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        hwPortMap[hwId] ?: hwId
                    }
                val hdmiPortNumbers = HashMap<String, Int>()
                hdmiInfos.forEachIndexed { idx, info ->
                    val hwId = Regex("HW(\\d+)").find(info.id)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val port = hwPortMap[hwId] ?: (idx + 1)
                    hdmiPortNumbers[info.id] = port
                }

                val hasTvChannels = hasScannedChannels(ctx)

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
                        "TV" -> "Live TV"
                        "AV" -> "AV"
                        "COMPONENT" -> "Component"
                        "HDMI" -> {
                            val portNum = hdmiPortNumbers[info.id] ?: 1
                            if (!rawLabel.isNullOrBlank() &&
                                !rawLabel.equals("HDMI", ignoreCase = true) &&
                                !rawLabel.equals("HDMI Input", ignoreCase = true) &&
                                !rawLabel.startsWith("com.", ignoreCase = true)
                            ) {
                                rawLabel
                            } else {
                                "HDMI $portNum"
                            }
                        }
                        else -> if (!rawLabel.isNullOrBlank()) rawLabel else getHumanReadablePortName(info)
                    }

                    // STRICT CONNECTION STATUS:
                    // Only mark connected when there is a live active signal / physical cable connected.
                    // INPUT_STATE_CONNECTED_STANDBY (1) and INPUT_STATE_DISCONNECTED (2) are considered NOT connected (greyed out).
                    val isConnected = when (kind) {
                        "TV" -> state == TvInputManager.INPUT_STATE_CONNECTED && hasTvChannels
                        else -> state == TvInputManager.INPUT_STATE_CONNECTED
                    }

                    val subtitle = when {
                        cecNames[info.id] != null -> cecNames[info.id]
                        kind == "TV" && hasTvChannels -> "Live TV Channels"
                        kind == "TV" -> "No channels / Antenna"
                        isConnected -> "Connected"
                        else -> "Not connected"
                    }

                    TvPort(
                        id = info.id,
                        label = label,
                        typeName = getTypeName(info.type),
                        isConnected = isConnected,
                        isStandby = state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY,
                        isPassthrough = info.isPassthroughInput,
                        kind = kind,
                        subtitle = subtitle,
                    )
                }.sortedWith(
                    compareBy<TvPort> {
                        // Put HDMI ports first, then TV, AV, Component
                        when (it.kind) { "HDMI" -> 0; "TV" -> 1; "AV" -> 2; "COMPONENT" -> 3; else -> 4 }
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
            TvPort(id = "default_tv", label = "TV", typeName = "Tuner", isConnected = true, isStandby = false, isPassthrough = false, kind = "TV", subtitle = null, statusUnknown = true),
            TvPort(id = "default_hdmi1", label = "HDMI 1", typeName = "HDMI", isConnected = false, isStandby = false, isPassthrough = false, kind = "HDMI", subtitle = null, statusUnknown = true),
            TvPort(id = "default_hdmi2", label = "HDMI 2", typeName = "HDMI", isConnected = false, isStandby = false, isPassthrough = false, kind = "HDMI", subtitle = null, statusUnknown = true),
            TvPort(id = "default_hdmi3", label = "HDMI 3", typeName = "HDMI", isConnected = false, isStandby = false, isPassthrough = false, kind = "HDMI", subtitle = null, statusUnknown = true),
            TvPort(id = "default_av", label = "AV", typeName = "AV", isConnected = false, isStandby = false, isPassthrough = false, kind = "AV", subtitle = null, statusUnknown = true)
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

    private fun hasScannedChannels(ctx: Context): Boolean {
        return try {
            ctx.contentResolver.query(
                TvContract.Channels.CONTENT_URI,
                arrayOf(TvContract.Channels._ID),
                null,
                null,
                null
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (_: Exception) {
            false
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
     * Starts the first intent the TV can handle, as fast as Android allows.
     *
     * - No resolveActivity() pre-check: that was an extra blocking binder call per intent on the main
     *   thread. startActivity() itself throws ActivityNotFoundException when nothing handles the intent,
     *   so we simply try the next one.
     * - Our own transition animation is switched off so the target screen appears the moment it is ready
     *   instead of waiting for Home's fade-out on slow TV chipsets.
     * Back from the launched screen returns to our Home (TASK_ON_HOME).
     */
    fun startFirstResolvable(ctx: Context, intents: List<Intent>): Boolean {
        val options: Bundle? = try {
            ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()
        } catch (_: Throwable) { null }
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                if (options != null) ctx.startActivity(intent, options) else ctx.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Not on this TV - try the next one.
            } catch (_: SecurityException) {
                // Exists but is not exported to us - try the next one.
            } catch (_: Exception) {
            }
        }
        return false
    }

    /** Opens the Live TV (tuner) app. */
    fun openLiveTv(ctx: Context): Boolean = startFirstResolvable(
        ctx,
        listOf(
            Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
            Intent(Intent.ACTION_VIEW, Uri.parse("tv://channel")),
            Intent(Intent.ACTION_VIEW).setPackage("com.google.android.tv"),
            Intent(Intent.ACTION_VIEW).setPackage("com.android.tv")
        )
    )

    /**
     * Fallback for ports that cannot be shown in our own player (tuner, placeholder ports, or an HDMI
     * port the TV refuses to open for apps): hand over to the system Live TV / source picker.
     */
    fun switchToPort(ctx: Context, port: TvPort): Boolean {
        if (port.kind == "TV") {
            if (openLiveTv(ctx)) return true
        }
        if (!port.id.startsWith("default_")) {
            val uri = TvContract.buildChannelUriForPassthroughInput(port.id)
            if (startFirstResolvable(ctx, listOf(Intent(Intent.ACTION_VIEW, uri)))) return true
        }
        return openNativeTvInputs(ctx)
    }

    /**
     * Opens the native TV input / source selector UI across OEM TV platforms.
     */
    fun openNativeTvInputs(ctx: Context): Boolean = startFirstResolvable(
        ctx,
        listOf(
            Intent("android.media.tv.action.VIEW_INPUTS"),
            Intent("com.android.tv.action.VIEW_INPUTS"),
            Intent("com.google.android.tv.action.VIEW_INPUTS"),
            // Sony BRAVIA
            Intent().setClassName("com.sony.dtv.tvinput.sources", "com.sony.dtv.tvinput.sources.SourcesActivity"),
            Intent().setClassName("com.sony.dtv.osat.viewinputs", "com.sony.dtv.osat.viewinputs.MainActivity"),
            // Xiaomi / Mi TV
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
            // Generic TV input settings
            Intent("android.settings.TV_INPUT_SETTINGS"),
            Intent("android.media.tv.action.SETUP_INPUTS"),
            Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
        )
    )

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