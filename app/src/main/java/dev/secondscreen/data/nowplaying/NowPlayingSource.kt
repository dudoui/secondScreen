package dev.secondscreen.data.nowplaying

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.secondscreen.data.Loadable
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import dev.secondscreen.i18n.S

data class NowPlaying(
    val playing: Boolean,
    val paused: Boolean,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationSec: Double?,
    val positionSec: Double?,
    /** When [positionSec] was read, to advance the progress bar locally between polls. */
    val fetchedAtMs: Long,
    val art: ImageBitmap?,
    /** App it comes from, e.g. "Podcasts", "Safari" (null for older servers). */
    val source: String? = null,
) {
    val hasTrack get() = title != null && (playing || paused)
}

/** Polls the Mac helper (mac-helper/nowplaying_server.py) for what Apple Music is playing. */
class NowPlayingSource {
    private val _state = MutableStateFlow(Loadable<NowPlaying>())
    val state: StateFlow<Loadable<NowPlaying>> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Resp(
        val state: String = "stopped",
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val duration: Double? = null,
        val position: Double? = null,
        val artId: String? = null,
        val source: String? = null,
    )

    fun start(scope: CoroutineScope, settings: Flow<Settings>) {
        scope.launch(Dispatchers.IO) {
            settings.map { it.macHost.trim() }.distinctUntilChanged().collectLatest { host ->
                if (host.isEmpty()) {
                    _state.value = Loadable(error = S.macNotConfigured)
                    return@collectLatest
                }
                val base = (if (host.contains("://")) host else "http://$host").trimEnd('/')
                var artId: String? = null
                var art: ImageBitmap? = null
                var artTries = 0
                while (isActive) {
                    val playing = try {
                        val r = json.decodeFromString<Resp>(get("$base/now").decodeToString())
                        // Fetch the cover on a new item; retry a few times while it is missing, then
                        // accept that this item has none (each request runs a script on the Mac).
                        if (r.artId != artId) {
                            artId = r.artId
                            art = null
                            artTries = 0
                        }
                        if (art == null && artId != null && artTries < MAX_ART_TRIES) {
                            artTries++
                            art = runCatching { decode(get("$base/art")) }.getOrNull()
                        }
                        _state.value = Loadable(
                            NowPlaying(
                                playing = r.state == "playing",
                                paused = r.state == "paused",
                                title = r.title?.takeIf { it.isNotBlank() },
                                artist = r.artist?.takeIf { it.isNotBlank() },
                                album = r.album?.takeIf { it.isNotBlank() },
                                durationSec = r.duration,
                                positionSec = r.position,
                                fetchedAtMs = System.currentTimeMillis(),
                                art = art,
                                source = r.source?.takeIf { it.isNotBlank() },
                            ),
                            System.currentTimeMillis(),
                        )
                        r.state == "playing"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Mac asleep, off, or on another network.
                        _state.value = Loadable(error = S.macUnreachable)
                        false
                    }
                    delay(if (playing) 3_000 else 6_000)
                }
            }
        }
    }

    private companion object {
        const val MAX_ART_TRIES = 3
    }

    private fun get(url: String): ByteArray =
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.bytes() ?: throw IOException("empty")
        }

    /** Decodes the cover, downsampled to roughly 800px so huge artwork doesn't eat memory. */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 800) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }
}
