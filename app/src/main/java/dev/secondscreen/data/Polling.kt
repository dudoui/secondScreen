package dev.secondscreen.data

import dev.secondscreen.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import dev.secondscreen.i18n.S

/** Last known value of a source, plus when it was fetched and the latest error (if any). */
data class Loadable<T>(
    val data: T? = null,
    val updatedAt: Long? = null,
    val error: String? = null,
) {
    fun isStale(nowMs: Long, maxAgeMs: Long = 60 * 60_000L) =
        updatedAt != null && nowMs - updatedAt > maxAgeMs
}

object Http {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val fixed = url.trim().replaceFirst(Regex("^webcals?://", RegexOption.IGNORE_CASE), "https://")
        val req = Request.Builder().url(fixed).header("User-Agent", "SecondScreen/1.0 (Android)").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: throw IOException(S.emptyResponse)
        }
    }
}

/**
 * Fetches raw text on an interval, parses it, exposes the result as a StateFlow and caches the raw
 * text on disk so a restart shows the last data immediately. Restarts whenever the relevant part of
 * the settings ([keyOf]) changes.
 */
abstract class PollingSource<K : Any, T>(
    private val name: String,
    private val cacheDir: File,
    private val intervalMs: Long,
    private val retryMs: Long = 60_000L,
) {
    private val _state = MutableStateFlow(Loadable<T>())
    val state: StateFlow<Loadable<T>> = _state.asStateFlow()

    /** The settings this source depends on, or null when it is not configured. */
    protected abstract fun keyOf(s: Settings): K?
    protected abstract val notConfiguredMessage: String
    protected abstract suspend fun fetch(key: K): String
    protected abstract fun parse(raw: String, key: K): T

    fun start(scope: CoroutineScope, settings: Flow<Settings>) {
        scope.launch(Dispatchers.IO) {
            settings.map { keyOf(it) }.distinctUntilChanged().collectLatest { key ->
                if (key == null) {
                    _state.value = Loadable(error = notConfiguredMessage)
                    return@collectLatest
                }
                _state.value = Loadable()
                loadCache(key)
                while (isActive) {
                    val ok = refresh(key)
                    delay(if (ok) intervalMs else retryMs)
                }
            }
        }
    }

    private fun cacheFile(key: K) = File(cacheDir, "$name-${key.hashCode().toUInt()}.txt")

    private fun loadCache(key: K) {
        val f = cacheFile(key)
        if (!f.exists()) return
        runCatching {
            val raw = f.readText()
            _state.value = Loadable(parse(raw, key), f.lastModified())
            onCacheLoaded(raw, key)
        }
    }

    /** Called with the cached raw text once it has parsed, before the first fetch. */
    protected open fun onCacheLoaded(raw: String, key: K) {}

    private suspend fun refresh(key: K): Boolean = try {
        val raw = fetch(key)
        val data = parse(raw, key)
        _state.value = Loadable(data, System.currentTimeMillis())
        runCatching {
            cacheDir.mkdirs()
            cacheFile(key).writeText(raw)
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        false
    }
}
