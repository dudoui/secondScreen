package dev.secondscreen.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.data.rss.NewsItem
import dev.secondscreen.ui.LocalContainer
import dev.secondscreen.ui.LocalNow
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import java.time.Duration
import java.time.Instant

private fun ago(t: Instant?, now: Instant): String {
    if (t == null) return ""
    val m = Duration.between(t, now).toMinutes().coerceAtLeast(0)
    return when {
        m < 60 -> "$m min"
        m < 24 * 60 -> "${m / 60} h"
        else -> "${m / (24 * 60)} j"
    }
}

/** Published less than 30 minutes ago. */
private fun isFresh(t: Instant?, now: Instant) = t != null && Duration.between(t, now).toMinutes() < 30

@Composable
fun NewsWidget(title: String, url: String, size: WidgetSize) {
    val container = LocalContainer.current
    val state by remember(container, url) { container.news(url) }.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(title)
        Spacer(Modifier.height(10.dp))
        WithData(state) { items ->
            val now = LocalNow.current.toInstant()
            when (size) {
                WidgetSize.Small -> items.firstOrNull()?.let { item ->
                    Column {
                        Text(ago(item.published, now), style = Type.numSmall, color = if (isFresh(item.published, now)) Ink.Accent else Ink.Faint)
                        Spacer(Modifier.height(4.dp))
                        Text(item.title, style = Type.body, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }

                WidgetSize.Medium -> Column(Modifier.clipToBounds(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(4).forEach { NewsRow(it, now, withDescription = false) }
                }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(if (size == WidgetSize.Huge) 14.dp else 9.dp)) {
                    items(items, key = { it.link + it.title }) { NewsRow(it, now, withDescription = size == WidgetSize.Huge) }
                }
            }
        }
    }
}

@Composable
private fun NewsRow(item: NewsItem, now: Instant, withDescription: Boolean) {
    val uri = LocalUriHandler.current
    val fresh = isFresh(item.published, now)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.link.isNotEmpty()) { runCatching { uri.openUri(item.link) } },
        verticalAlignment = Alignment.Top,
    ) {
        Row(Modifier.width(54.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (fresh) {
                Box(Modifier.size(5.dp).background(Ink.Accent, CircleShape))
                Spacer(Modifier.width(5.dp))
            }
            Text(ago(item.published, now), style = Type.numSmall, color = if (fresh) Ink.Accent else Ink.Faint, maxLines = 1)
        }
        Column {
            Text(
                item.title,
                style = if (withDescription) Type.body.copy(fontSize = 18.sp, lineHeight = 23.sp) else Type.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (withDescription && item.description != null) {
                Spacer(Modifier.height(3.dp))
                Text(item.description, style = Type.bodySmall, color = Ink.Faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
