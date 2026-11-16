package dev.secondscreen.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.data.nowplaying.NowPlaying
import dev.secondscreen.ui.LocalContainer
import dev.secondscreen.ui.LocalNow
import dev.secondscreen.ui.LocalSettings
import kotlinx.coroutines.launch
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import dev.secondscreen.i18n.S

private val CoverShape = RoundedCornerShape(10.dp)

/** What the Mac helper calls Apple Music ("Musique" before it was translated); not worth showing. */
private val AppleMusicNames = setOf("Music", "Musique")

@Composable
fun NowPlayingWidget(size: WidgetSize) {
    val state by LocalContainer.current.nowPlaying.state.collectAsState()
    val np = state.data
    if (np == null || !np.hasTrack) {
        Column(Modifier.fillMaxSize()) {
            WidgetLabel(S.music)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.MusicNote, null, tint = Ink.Ghost, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(state.error ?: S.nothingPlaying, style = Type.bodySmall, color = Ink.Faint)
                }
            }
        }
        return
    }
    val dim = if (np.paused) Modifier.alpha(0.45f) else Modifier
    when (size) {
        WidgetSize.Small -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Cover(np, Modifier.weight(1f).aspectRatio(1f).then(dim))
            Spacer(Modifier.height(8.dp))
            Text(np.title.orEmpty(), style = Type.bodySmall.copy(color = Ink.Fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        WidgetSize.Medium -> Row(Modifier.fillMaxSize()) {
            Cover(np, Modifier.fillMaxHeight().aspectRatio(1f).then(dim))
            Spacer(Modifier.width(16.dp))
            TrackInfo(np, titleSize = 18, modifier = Modifier.weight(1f).fillMaxHeight())
        }

        WidgetSize.Large -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Cover(np, Modifier.weight(1f).aspectRatio(1f).then(dim))
            Spacer(Modifier.height(12.dp))
            Text(np.title.orEmpty(), style = Type.body.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(np.artist, np.album).joinToString(" — "), style = Type.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Progress(np, Modifier.fillMaxWidth())
        }

        WidgetSize.Huge -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Cover(np, Modifier.fillMaxHeight().aspectRatio(1f).then(dim))
            Spacer(Modifier.width(32.dp))
            TrackInfo(np, titleSize = 34, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun TrackInfo(np: NowPlaying, titleSize: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        WidgetLabel(listOfNotNull(if (np.paused) S.paused else S.listening, np.source?.takeIf { it !in AppleMusicNames }).joinToString(" · "))
        Spacer(Modifier.height(8.dp))
        Text(
            np.title.orEmpty(),
            style = Type.body.copy(fontSize = titleSize.sp, lineHeight = (titleSize * 1.2f).sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        np.artist?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = Type.body.copy(fontSize = (titleSize * 0.7f).sp, color = Ink.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        np.album?.let {
            Text(it, style = Type.bodySmall.copy(fontSize = (titleSize * 0.6f).coerceAtLeast(13f).sp), color = Ink.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Progress(np, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Cover(np: NowPlaying, modifier: Modifier) {
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val noRipple = remember { MutableInteractionSource() }
    // Tap a cover to see it in colour; tap again for greyscale. Saved in settings.
    val coverInColor = LocalSettings.current.coverInColor
    val store = LocalContainer.current.settingsStore
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .clip(CoverShape)
            .background(Ink.Card)
            .border(1.dp, Ink.Line, CoverShape)
            .clickable(interactionSource = noRipple, indication = null) { scope.launch { store.setCoverInColor(!coverInColor) } },
        contentAlignment = Alignment.Center,
    ) {
        val art = np.art
        if (art != null) {
            Image(
                art,
                contentDescription = np.album,
                contentScale = ContentScale.Crop,
                colorFilter = if (coverInColor) null else grey,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.MusicNote, null, tint = Ink.Ghost, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun Progress(np: NowPlaying, modifier: Modifier) {
    val dur = np.durationSec ?: return
    val pos0 = np.positionSec ?: return
    if (dur <= 0) return
    val nowMs = LocalNow.current.toInstant().toEpochMilli()
    val pos = (if (np.playing) pos0 + (nowMs - np.fetchedAtMs) / 1000.0 else pos0).coerceIn(0.0, dur)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Ink.Ghost, cornerRadius = r)
            drawRoundRect(Ink.Dim, size = Size(size.width * (pos / dur).toFloat(), size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(pos), style = Type.numSmall.copy(fontSize = 11.sp), color = Ink.Faint)
            Spacer(Modifier.weight(1f))
            Text(mmss(dur), style = Type.numSmall.copy(fontSize = 11.sp), color = Ink.Faint)
        }
    }
}

private fun mmss(s: Double): String {
    val t = s.toInt()
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}
