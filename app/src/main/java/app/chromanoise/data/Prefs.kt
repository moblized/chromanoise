package app.chromanoise.data

import android.content.Context
import app.chromanoise.audio.Preset
import app.chromanoise.audio.SpectrumModel.BAND_COUNT
import org.json.JSONArray
import org.json.JSONObject

/** Remembers the last spectrum, volume and the user's saved presets. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("chroma_noise", Context.MODE_PRIVATE)

    var bands: List<Float>?
        get() = sp.getString(KEY_BANDS, null)?.let(::parseBands)
        set(value) = sp.edit().putString(KEY_BANDS, value?.joinToString(",")).apply()

    var presetName: String?
        get() = sp.getString(KEY_PRESET, null)
        set(value) = sp.edit().putString(KEY_PRESET, value).apply()

    var volume: Float
        get() = sp.getFloat(KEY_VOLUME, 0.7f)
        set(value) = sp.edit().putFloat(KEY_VOLUME, value).apply()

    var savedPresets: List<Preset>
        get() = runCatching {
            val arr = JSONArray(sp.getString(KEY_SAVED, "[]"))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                parseBands(o.getString("bands"))?.let { Preset(o.getString("name"), it, builtIn = false) }
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.forEach { p ->
                arr.put(JSONObject().put("name", p.name).put("bands", p.bands.joinToString(",")))
            }
            sp.edit().putString(KEY_SAVED, arr.toString()).apply()
        }

    private fun parseBands(s: String): List<Float>? =
        s.split(",").mapNotNull { it.toFloatOrNull()?.coerceIn(0f, 1f) }.takeIf { it.size == BAND_COUNT }

    private companion object {
        const val KEY_BANDS = "bands"
        const val KEY_PRESET = "preset"
        const val KEY_VOLUME = "volume"
        const val KEY_SAVED = "saved_presets"
    }
}
