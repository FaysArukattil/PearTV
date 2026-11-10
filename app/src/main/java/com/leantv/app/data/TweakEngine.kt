package com.leantv.app.data

import com.leantv.app.adb.AdbClient

class TweakEngine(private val adb: AdbClient, private val journal: Journal) {

    private val animKeys = listOf(
        "window_animation_scale", "transition_animation_scale", "animator_duration_scale"
    )
    private val dnsKeys = listOf("private_dns_mode", "private_dns_specifier")

    private suspend fun get(key: String): String = adb.shell("settings get global $key").out.trim()

    private suspend fun put(key: String, value: String): Boolean =
        adb.shell("settings put global $key $value").ok

    private suspend fun snapshotOnce(keys: List<String>) {
        val snap = journal.tweakSnapshot().toMutableMap()
        var changed = false
        for (k in keys) {
            if (k !in snap) {
                snap[k] = get(k)
                changed = true
            }
        }
        if (changed) journal.saveTweakSnapshot(snap)
    }

    private suspend fun restore(keys: List<String>): Boolean {
        val snap = journal.tweakSnapshot()
        var ok = true
        for (k in keys) {
            val prev = snap[k] ?: continue
            val r = if (prev.isEmpty() || prev == "null") {
                adb.shell("settings delete global $k")
            } else {
                adb.shell("settings put global $k $prev")
            }
            if (!r.ok) ok = false
        }
        return ok
    }

    suspend fun read(): TweakStates {
        val anim = animKeys.all { get(it) == "0.5" }
        val dns = get("private_dns_mode") == "hostname" && get("private_dns_specifier") == DNS_HOST
        return TweakStates(animations = anim, dns = dns)
    }

    suspend fun setAnimations(on: Boolean): Boolean {
        snapshotOnce(animKeys)
        return if (on) animKeys.all { put(it, "0.5") } else restore(animKeys)
    }

    suspend fun setPrivateDns(on: Boolean): Boolean {
        snapshotOnce(dnsKeys)
        return if (on) {
            put("private_dns_specifier", DNS_HOST) && put("private_dns_mode", "hostname")
        } else {
            restore(dnsKeys)
        }
    }

    suspend fun trimCaches(): Boolean = adb.shell("pm trim-caches 9999999999").ok

    suspend fun resetAll(): Boolean {
        val ok = restore(animKeys + dnsKeys)
        journal.clearTweakSnapshot()
        return ok
    }

    companion object {
        const val DNS_HOST = "dns.adguard-dns.com"
    }
}
