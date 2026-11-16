package com.peartv.app.data

import android.content.Context
import org.json.JSONObject

data class CatalogEntry(val pkg: String, val name: String, val tier: Tier, val why: String)
data class Hint(val contains: String, val why: String)

class Catalog(val entries: Map<String, CatalogEntry>, val hints: List<Hint>) {
    companion object {
        fun load(ctx: Context): Catalog {
            val text = ctx.assets.open("catalog.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val entries = HashMap<String, CatalogEntry>()
            val arr = root.getJSONArray("entries")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val e = CatalogEntry(
                    pkg = o.getString("pkg"),
                    name = o.getString("name"),
                    tier = Tier.valueOf(o.getString("tier")),
                    why = o.getString("why"),
                )
                entries[e.pkg] = e
            }
            val hints = ArrayList<Hint>()
            val ha = root.optJSONArray("hints")
            if (ha != null) {
                for (i in 0 until ha.length()) {
                    val o = ha.getJSONObject(i)
                    hints.add(Hint(o.getString("contains"), o.getString("why")))
                }
            }
            return Catalog(entries, hints)
        }
    }
}
