package com.leantv.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything needed to undo what the app did. Stored privately on the TV. */
class Journal(ctx: Context) {
    private val sp = ctx.getSharedPreferences("leantv", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) {
            sp.edit().putBoolean("onboarded", v).apply()
        }

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

    // ---------- Launcher Favorites & Settings ----------

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

    var defaultLauncherPromptDismissed: Boolean
        get() = sp.getBoolean("launcher_prompt_dismissed", false)
        set(v) = sp.edit().putBoolean("launcher_prompt_dismissed", v).apply()
}
