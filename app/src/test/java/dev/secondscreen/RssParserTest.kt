package dev.secondscreen

import dev.secondscreen.data.rss.RssParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RssParserTest {
    @Test
    fun parsesRss2() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Le Monde.fr - Actualités et Infos en France et dans le monde</title>
                <item>
                  <title><![CDATA[Premier titre & co]]></title>
                  <pubDate>Sat, 14 Nov 2026 15:31:04 +0100</pubDate>
                  <description><![CDATA[<p>Un résumé</p>]]></description>
                  <link>https://www.lemonde.fr/a/1.html</link>
                  <media:content url="https://img/1.jpg" />
                </item>
                <item>
                  <title>Deuxième titre</title>
                  <link>https://www.lemonde.fr/a/2.html</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val items = RssParser.parse(xml)
        assertEquals(2, items.size)
        assertEquals("Premier titre & co", items[0].title)
        assertEquals(Instant.parse("2026-11-14T14:31:04Z"), items[0].published)
        assertEquals("Un résumé", items[0].description)
        assertEquals("https://www.lemonde.fr/a/2.html", items[1].link)
        assertEquals(null, items[1].published)
    }

    @Test
    fun parsesAtom() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Atom entry</title><link href="https://x/1"/><updated>2026-11-14T10:00:00Z</updated></entry>
            </feed>
        """.trimIndent()
        val items = RssParser.parse(xml)
        assertEquals("Atom entry", items.single().title)
        assertEquals("https://x/1", items.single().link)
        assertEquals(Instant.parse("2026-11-14T10:00:00Z"), items.single().published)
    }
}
