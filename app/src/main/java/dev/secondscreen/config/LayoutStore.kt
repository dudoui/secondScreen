package dev.secondscreen.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.secondscreen.widgets.PageSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import dev.secondscreen.i18n.S
import dev.secondscreen.i18n.Strings

private val Context.layoutDataStore by preferencesDataStore("layout")

/** JSON encoding of the user's pages. Invalid or missing data falls back to [DefaultLayout]. */
object LayoutCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(PageSpec.serializer())
    /** Stable ids for the default layout across emissions; rebuilt when the language changes. */
    private var cached: Pair<Strings, List<PageSpec>>? = null
    private val default: List<PageSpec>
        @Synchronized get() {
            val s = S
            cached?.takeIf { it.first === s }?.let { return it.second }
            return DefaultLayout.pages().also { cached = s to it }
        }

    fun encode(pages: List<PageSpec>): String = json.encodeToString(serializer, pages)

    fun decode(text: String?): List<PageSpec> {
        if (text == null) return default
        return try {
            sanitize(json.decodeFromString(serializer, text))
        } catch (e: Exception) {
            default
        }
    }
}

class LayoutStore(private val context: Context) {
    private val key = stringPreferencesKey("pages_json")

    val flow: Flow<List<PageSpec>> = context.layoutDataStore.data.map { LayoutCodec.decode(it[key]) }

    suspend fun save(pages: List<PageSpec>) {
        context.layoutDataStore.edit { it[key] = LayoutCodec.encode(pages) }
    }
}
