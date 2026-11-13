package com.leantv.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * CleanYourTV local persistent storage manager.
 * Stores app launches, recent history, hidden apps, wallpaper themes,
 * favorites, and debloat rollback ledgers securely on the TV.
 */
class Journal(ctx: Context) {
    private val sp = ctx.getSharedPreferences("leantv", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    fun baseline(): Set<String> = HashSet(sp.getStringSet("baseline", emptySet()) ?: emptySet())

    fun saveBaselineIfMissing(pkgs: Set<String>) {
        if (!sp.contains("baseline")) sp.edit().putStringSet("baseline", HashSet(pkgs)).apply()
    }

    fun removed(): List<Removed> {
        val raw = sp.getString("removed", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Removed(
                    pkg = o.getString("pkg"),
                    label = o.getString("label"),
                    method = RemovalMethod.valueOf(o.getString("method")),
                    at = o.getLong("at"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRemoved(list: List<Removed>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("pkg", it.pkg).put("label", it.label)
                    .put("method", it.method.name).put("at", it.at)
            )
        }
        sp.edit().putString("removed", arr.toString()).apply()
    }

    fun addRemoved(r: Removed) = saveRemoved(removed().filter { it.pkg != r.pkg } + r)

    fun dropRemoved(pkg: String) = saveRemoved(removed().filter { it.pkg != pkg })

    /** Original values of the system settings we change, keyed by setting name. */
    fun tweakSnapshot(): Map<String, String> {
        val raw = sp.getString("tweaks", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveTweakSnapshot(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("tweaks", o.toString()).apply()
    }

    fun clearTweakSnapshot() {
        sp.edit().remove("tweaks").apply()
    }

    // ---------- App Usage Tracking (Frequently Used & Recently Used) ----------

    fun recordAppLaunch(pkg: String) {
        val count = getLaunchCount(pkg) + 1
        val now = System.currentTimeMillis()
        sp.edit()
            .putInt("launch_count_$pkg", count)
            .putLong("last_launch_$pkg", now)
            .apply()
    }

    fun getLaunchCount(pkg: String): Int = sp.getInt("launch_count_$pkg", 0)

    fun getLastLaunchTime(pkg: String): Long = sp.getLong("last_launch_$pkg", 0L)

    // ---------- Favorites & Pinned Apps ----------

    fun favorites(): Set<String> =
        HashSet(sp.getStringSet("favorites", null) ?: emptySet())

    fun toggleFavorite(pkg: String): Boolean {
        val current = HashSet(favorites())
        val added = if (current.contains(pkg)) {
            current.remove(pkg)
            false
        } else {
            current.add(pkg)
            true
        }
        sp.edit().putStringSet("favorites", current).apply()
        return added
    }

    fun isFavorite(pkg: String): Boolean = favorites().contains(pkg)

    // ---------- Hidden Apps ----------

    fun hiddenApps(): Set<String> =
        HashSet(sp.getStringSet("hidden_apps", null) ?: emptySet())

    fun toggleHideApp(pkg: String): Boolean {
        val current = HashSet(hiddenApps())
        val hidden = if (current.contains(pkg)) {
            current.remove(pkg)
            false
        } else {
            current.add(pkg)
            true
        }
        sp.edit().putStringSet("hidden_apps", current).apply()
        return hidden
    }

    fun isAppHidden(pkg: String): Boolean = hiddenApps().contains(pkg)

    // ---------- Dock Order ----------

    fun dockApps(): List<String> {
        val raw = sp.getString("dock_apps", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDockApps(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.edit().putString("dock_apps", arr.toString()).apply()
    }

    // ---------- Personalization & Wallpaper ----------

    var wallpaperTheme: String
        get() = sp.getString("wallpaper_theme", "aurora") ?: "aurora"
        set(v) = sp.edit().putString("wallpaper_theme", v).apply()

    var customWallpaperUri: String?
        get() = sp.getString("custom_wallpaper_uri", null)
        set(v) = sp.edit().putString("custom_wallpaper_uri", v).apply()

    var clockFormat24h: Boolean
        get() = sp.getBoolean("clock_24h", false)
        set(v) = sp.edit().putBoolean("clock_24h", v).apply()

    var showGreeting: Boolean
        get() = sp.getBoolean("show_greeting", true)
        set(v) = sp.edit().putBoolean("show_greeting", v).apply()

    var gridColumns: Int
        get() = sp.getInt("grid_columns", 4)
        set(v) = sp.edit().putInt("grid_columns", v).apply()

    var defaultLauncherPromptDismissed: Boolean
        get() = sp.getBoolean("launcher_prompt_dismissed", false)
        set(v) = sp.edit().putBoolean("launcher_prompt_dismissed", v).apply()

    var debloatWizardDismissed: Boolean
        get() = sp.getBoolean("debloat_wizard_dismissed", false)
        set(v) = sp.edit().putBoolean("debloat_wizard_dismissed", v).apply()

    var appSortMode: String
        get() = sp.getString("app_sort_mode", "USAGE") ?: "USAGE"
        set(v) = sp.edit().putString("app_sort_mode", v).apply()
}
