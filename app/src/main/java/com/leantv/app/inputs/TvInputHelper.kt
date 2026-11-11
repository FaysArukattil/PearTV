package com.leantv.app.inputs

import android.content.Context
import android.content.Intent
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
    val isPassthrough: Boolean
)

object TvInputHelper {
    private const val TAG = "TvInputHelper"

    /**
     * Query only physically present hardware inputs (HDMI 1, HDMI 2, AV, Component, Tuner).
     * Filters out non-hardware junk and OTT app streaming providers.
     */
    fun queryHardwareInputs(ctx: Context): List<TvPort> {
        val manager = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()

        return try {
            val list = manager.tvInputList ?: emptyList()
            list.filter { info ->
                // Keep passthrough hardware ports (HDMI, Component, Composite, DisplayPort, Tuner)
                info.isPassthroughInput || info.type == TvInputInfo.TYPE_TUNER
            }.map { info ->
                val state = try { manager.getInputState(info.id) } catch (_: Exception) { TvInputManager.INPUT_STATE_DISCONNECTED }
                val isConnected = state == TvInputManager.INPUT_STATE_CONNECTED
                val isStandby = state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY

                val label = try {
                    val customLabel = info.loadLabel(ctx)?.toString()
                    if (!customLabel.isNullOrBlank()) customLabel else getHumanReadablePortName(info)
                } catch (_: Exception) {
                    getHumanReadablePortName(info)
                }

                TvPort(
                    id = info.id,
                    label = label,
                    typeName = getTypeName(info.type),
                    isConnected = isConnected,
                    isStandby = isStandby,
                    isPassthrough = info.isPassthroughInput
                )
            }.sortedBy { !it.isConnected } // Connected ports first
        } catch (e: Exception) {
            Log.w(TAG, "Error querying TV inputs", e)
            emptyList()
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

        // Fallback: OEM / TV Input actions
        val fallbacks = listOf(
            Intent("android.media.tv.action.VIEW_INPUTS"),
            Intent("com.android.tv.action.VIEW_INPUTS"),
            Intent("android.intent.action.VIEW", Uri.parse("tv://passthrough/${port.id}")),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )

        for (intent in fallbacks) {
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
