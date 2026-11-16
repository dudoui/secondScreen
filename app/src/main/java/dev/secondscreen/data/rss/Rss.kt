package dev.secondscreen.data.rss

import dev.secondscreen.data.Http
import dev.secondscreen.data.PollingSource
import dev.secondscreen.settings.Settings
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import dev.secondscreen.i18n.S

data class NewsItem(
    val title: String,
    val link: String,
    val published: Instant?,
    val description: String?,
)

/** Minimal RSS 2.0 / Atom parser (DOM, so it runs the same on Android and in JVM unit tests). */
object RssParser {
    fun parse(xml: String): List<NewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t'))))

        val rss = doc.getElementsByTagName("item")
        if (rss.length > 0) {
            return (0 until rss.length).map { rss.item(it) as Element }.mapNotNull { e ->
                val title = e.text("title") ?: return@mapNotNull null
                NewsItem(
                    title = title,
                    link = e.text("link").orEmpty(),
                    published = e.text("pubDate")?.let(::parseDate),
                    description = e.text("description")?.let(::stripHtml),
                )
            }
        }
        val atom = doc.getElementsByTagName("entry")
        return (0 until atom.length).map { atom.item(it) as Element }.mapNotNull { e ->
            val title = e.text("title") ?: return@mapNotNull null
            val link = (e.getElementsByTagName("link").item(0) as? Element)?.getAttribute("href").orEmpty()
            NewsItem(
                title = title,
                link = link,
                published = (e.text("published") ?: e.text("updated"))?.let(::parseDate),
                description = (e.text("summary") ?: e.text("content"))?.let(::stripHtml),
            )
        }
    }

    private fun Element.text(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseDate(s: String): Instant? =
        runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()

    private fun stripHtml(s: String): String? =
        s.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
}

/** Polls one feed: [url], or the default feed from the settings when [url] is null. */
class RssSource(cacheDir: File, private val url: String? = null) : PollingSource<String, List<NewsItem>>(
    name = "rss",
    cacheDir = cacheDir,
    intervalMs = 10 * 60_000L,
) {
    override val notConfiguredMessage get() = S.noRssFeed
    override fun keyOf(s: Settings) = (url ?: s.rssUrl).trim().takeIf { it.isNotEmpty() }
    override suspend fun fetch(key: String) = Http.get(key)
    override fun parse(raw: String, key: String) =
        RssParser.parse(raw).sortedByDescending { it.published ?: java.time.Instant.EPOCH }
}

/** "https://www.lemonde.fr/rss/une.xml" → "lemonde.fr"; null when [url] has no host. */
fun feedHost(url: String): String? =
    runCatching { java.net.URI(url.trim()).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }

/** Trims, and assumes https when the user typed an address without a scheme. */
fun normalizeFeedUrl(input: String): String {
    val t = input.trim()
    return if (t.isEmpty() || t.contains("://")) t else "https://$t"
}
