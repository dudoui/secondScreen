package dev.secondscreen.i18n

import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Every piece of text the app shows. To add a language, implement this interface and list it in
 * [stringsFor]; the compiler then points at anything left untranslated.
 *
 * It is plain Kotlin rather than Android string resources because much of the text is produced
 * outside composables (data sources, formatting helpers) and by code covered by JVM unit tests.
 */
interface Strings {
    /** Used for day and month names and for capitalisation. */
    val locale: Locale

    // ---- common ----
    val ok: String
    val cancel: String
    val save: String
    val delete: String
    val settings: String
    val loading: String

    // ---- dates ----
    val today: String
    val tomorrow: String
    /** Narrow column of the event lists, before a time: "tom. 14:00". */
    val tomorrowShort: String
    /** Day column of the daily forecast. */
    val todayAbbrev: String
    val tomorrowAbbrev: String
    /** [DateTimeFormatter] patterns. */
    val longDatePattern: String
    val shortDatePattern: String
    val hourPattern: String

    // ---- dashboard ----
    val noPagesDashboard: String

    // ---- calendar ----
    val nextLabel: String
    val upcomingLabel: String
    val agendaWeekLabel: String
    val nothingPlanned: String
    val allDayShort: String
    val ongoingShort: String
    fun ongoingUntil(time: String): String
    fun inMinutes(minutes: Long): String
    val untitledEvent: String
    val noIcalFeed: String
    val calendarPermissionMissing: String

    // ---- weather ----
    val clearSky: String
    val mainlyClear: String
    val partlyCloudy: String
    val overcast: String
    val fog: String
    val drizzle: String
    val rain: String
    val freezingRain: String
    val snow: String
    val showers: String
    val snowShowers: String
    val thunderstorm: String
    val stormNow: String
    val rainWithinTheHour: String
    val stormsExpected: String
    fun rainLikely(percent: Int): String
    fun feelsLike(temperature: String): String
    fun wind(kmh: Int): String
    val nextHoursLabel: String
    val daysLabel: String
    val sevenDaysLabel: String
    val placeNotConfigured: String

    // ---- news ----
    val news: String
    fun minutesAgo(n: Long): String
    fun hoursAgo(n: Long): String
    fun daysAgo(n: Long): String
    val noRssFeed: String
    val emptyResponse: String

    // ---- now playing ----
    val music: String
    val nothingPlaying: String
    val paused: String
    val listening: String
    val macNotConfigured: String
    val macUnreachable: String

    // ---- widget catalogue ----
    val clock: String
    val nextEvent: String
    val nextEvents: String
    val agenda: String
    val currentWeather: String
    val hourlyForecast: String
    val dailyForecast: String
    val sizeSmall: String
    val sizeMedium: String
    val sizeLarge: String
    val sizeHuge: String

    // ---- default layout ----
    val pageHome: String
    val pageWeather: String
    val defaultFeedTitle: String

    // ---- settings: general ----
    val tabGeneral: String
    val tabPages: String
    val icalLabel: String
    val icalHelp: String
    val cityLabel: String
    val search: String
    val searching: String
    val noResult: String
    fun error(message: String?): String
    fun currentPlace(label: String): String
    val defaultRssLabel: String
    val macLabel: String
    val nightStartLabel: String
    val nightEndLabel: String
    val nightHelp: String

    // ---- settings: pages ----
    val untitledPage: String
    val addPage: String
    fun pageNumber(n: Int): String
    val rename: String
    val reset: String
    val noPagesEditor: String
    val editorHint: String
    val resizeHint: String
    val move: String
    val tapHighlightedCell: String
    val addRssFeed: String
    val renamePage: String
    fun deletePageConfirm(name: String): String
    val resetConfirm: String
    val addWidget: String
    val addedSmallHint: String
    val eventCount: String
    val feedUrlLabel: String
    val feedTitleLabel: String
    val feedsInUse: String
    val feedFallbackHint: String
    val nameLabel: String
}

/** The catalogue for [locale]: French for French, English for everything else. */
fun stringsFor(locale: Locale): Strings = if (locale.language == "fr") Fr else En

/** Text in the phone's current language. */
val S: Strings get() = stringsFor(Locale.getDefault())

private val formatters = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

/** A formatter for [pattern] with this language's day and month names. */
fun Strings.formatter(pattern: String): DateTimeFormatter =
    formatters.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }

/** French writes day and month names in lower case; headings want a capital. */
fun Strings.capitalize(text: String): String = text.replaceFirstChar { it.titlecase(locale) }

object En : Strings {
    /** Keeps the regional variant (en-GB, en-US…) when the phone is in English. */
    override val locale: Locale
        get() = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.ENGLISH

    override val ok = "OK"
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val settings = "Settings"
    override val loading = "Loading…"

    override val today = "Today"
    override val tomorrow = "Tomorrow"
    override val tomorrowShort = "tom."
    override val todayAbbrev = "Today"
    override val tomorrowAbbrev = "Tom."
    private val monthFirst get() = locale.country == "US"
    override val longDatePattern get() = if (monthFirst) "EEEE, MMMM d" else "EEEE d MMMM"
    override val shortDatePattern get() = if (monthFirst) "EEE, MMM d" else "EEE d MMM"
    override val hourPattern = "H'h'"

    override val noPagesDashboard = "No pages. Press and hold to set things up."

    override val nextLabel = "Next"
    override val upcomingLabel = "Upcoming"
    override val agendaWeekLabel = "Agenda · 7 days"
    override val nothingPlanned = "Nothing planned"
    override val allDayShort = "Day"
    override val ongoingShort = "now"
    override fun ongoingUntil(time: String) = "Now · until $time"
    override fun inMinutes(minutes: Long) = "In $minutes min"
    override val untitledEvent = "(untitled)"
    override val noIcalFeed = "No iCal feed"
    override val calendarPermissionMissing = "Calendar access not allowed (Android settings → Apps → Second Screen)"

    override val clearSky = "Clear sky"
    override val mainlyClear = "Mainly clear"
    override val partlyCloudy = "Partly cloudy"
    override val overcast = "Overcast"
    override val fog = "Fog"
    override val drizzle = "Drizzle"
    override val rain = "Rain"
    override val freezingRain = "Freezing rain"
    override val snow = "Snow"
    override val showers = "Showers"
    override val snowShowers = "Snow showers"
    override val thunderstorm = "Thunderstorm"
    override val stormNow = "Storm in progress"
    override val rainWithinTheHour = "Rain within the hour"
    override val stormsExpected = "Storms expected"
    override fun rainLikely(percent: Int) = "Rain likely · $percent%"
    override fun feelsLike(temperature: String) = "feels like $temperature"
    override fun wind(kmh: Int) = "wind $kmh km/h"
    override val nextHoursLabel = "Next hours"
    override val daysLabel = "Days"
    override val sevenDaysLabel = "7 days"
    override val placeNotConfigured = "No location set"

    override val news = "News"
    override fun minutesAgo(n: Long) = "$n min"
    override fun hoursAgo(n: Long) = "$n h"
    override fun daysAgo(n: Long) = "$n d"
    override val noRssFeed = "No RSS feed set"
    override val emptyResponse = "Empty response"

    override val music = "Music"
    override val nothingPlaying = "Nothing playing"
    override val paused = "Paused"
    override val listening = "Now playing"
    override val macNotConfigured = "Mac not set up"
    override val macUnreachable = "Mac unreachable"

    override val clock = "Clock"
    override val nextEvent = "Next event"
    override val nextEvents = "Upcoming events"
    override val agenda = "Agenda"
    override val currentWeather = "Current weather"
    override val hourlyForecast = "Hourly forecast"
    override val dailyForecast = "Daily forecast"
    override val sizeSmall = "Small"
    override val sizeMedium = "Medium"
    override val sizeLarge = "Large"
    override val sizeHuge = "Full page"

    override val pageHome = "Home"
    override val pageWeather = "Weather"
    override val defaultFeedTitle = "Le Monde · Front page"

    override val tabGeneral = "General"
    override val tabPages = "Pages"
    override val icalLabel = "Extra iCal calendars (optional), one per line"
    override val icalHelp = "Calendars synced on this phone are read automatically. Add other calendars here by their iCal address."
    override val cityLabel = "City (weather)"
    override val search = "Search"
    override val searching = "Searching…"
    override val noResult = "No results"
    override fun error(message: String?) = "Error: $message"
    override fun currentPlace(label: String) = "Current: $label"
    override val defaultRssLabel = "Default RSS feed"
    override val macLabel = "Mac — now playing (e.g. 192.168.1.40:8765)"
    override val nightStartLabel = "Night: start (h)"
    override val nightEndLabel = "Night: end (h)"
    override val nightHelp = "Brightness drops to minimum at night. Hold a finger still on the dashboard for about 1 s to come back here."

    override val untitledPage = "Untitled"
    override val addPage = "＋ Page"
    override fun pageNumber(n: Int) = "Page $n"
    override val rename = "Rename"
    override val reset = "Reset"
    override val noPagesEditor = "No pages. Tap “＋ Page” to get started."
    override val editorHint = "Tap an empty cell to add a widget, or a widget to change it."
    override val resizeHint = "Drag the ↘ corner to resize."
    override val move = "Move"
    override val tapHighlightedCell = "Tap a highlighted cell."
    override val addRssFeed = "Add an RSS feed"
    override val renamePage = "Rename page"
    override fun deletePageConfirm(name: String) = "Delete the page “$name” and its widgets?"
    override val resetConfirm = "Replace all pages with the default layout?"
    override val addWidget = "Add a widget"
    override val addedSmallHint = "Added at 1×1: drag its ↘ corner to make it bigger."
    override val eventCount = "Number of events"
    override val feedUrlLabel = "Feed address (URL)"
    override val feedTitleLabel = "Title (optional)"
    override val feedsInUse = "Feeds already in use"
    override val feedFallbackHint = "With no address, the widget follows the RSS feed from the general settings."
    override val nameLabel = "Name"
}

object Fr : Strings {
    override val locale: Locale
        get() = Locale.getDefault().takeIf { it.language == "fr" } ?: Locale.FRENCH

    override val ok = "OK"
    override val cancel = "Annuler"
    override val save = "Enregistrer"
    override val delete = "Supprimer"
    override val settings = "Réglages"
    override val loading = "Chargement…"

    override val today = "Aujourd'hui"
    override val tomorrow = "Demain"
    override val tomorrowShort = "dem."
    override val todayAbbrev = "Auj."
    override val tomorrowAbbrev = "Dem."
    override val longDatePattern = "EEEE d MMMM"
    override val shortDatePattern = "EEE d MMM"
    override val hourPattern = "H'h'"

    override val noPagesDashboard = "Aucune page. Maintenir le doigt appuyé pour configurer."

    override val nextLabel = "Prochain"
    override val upcomingLabel = "À venir"
    override val agendaWeekLabel = "Agenda · 7 jours"
    override val nothingPlanned = "Rien de prévu"
    override val allDayShort = "Jour"
    override val ongoingShort = "en cours"
    override fun ongoingUntil(time: String) = "En cours · jusqu'à $time"
    override fun inMinutes(minutes: Long) = "Dans $minutes min"
    override val untitledEvent = "(sans titre)"
    override val noIcalFeed = "Aucun flux iCal"
    override val calendarPermissionMissing = "Accès à l'agenda non autorisé (réglages Android → Applis → Second Screen)"

    override val clearSky = "Ciel clair"
    override val mainlyClear = "Peu nuageux"
    override val partlyCloudy = "Partiellement nuageux"
    override val overcast = "Couvert"
    override val fog = "Brouillard"
    override val drizzle = "Bruine"
    override val rain = "Pluie"
    override val freezingRain = "Pluie verglaçante"
    override val snow = "Neige"
    override val showers = "Averses"
    override val snowShowers = "Averses de neige"
    override val thunderstorm = "Orage"
    override val stormNow = "Orage en cours"
    override val rainWithinTheHour = "Pluie dans l'heure"
    override val stormsExpected = "Orages prévus"
    override fun rainLikely(percent: Int) = "Pluie probable · $percent%"
    override fun feelsLike(temperature: String) = "ressenti $temperature"
    override fun wind(kmh: Int) = "vent $kmh km/h"
    override val nextHoursLabel = "Prochaines heures"
    override val daysLabel = "Jours"
    override val sevenDaysLabel = "7 jours"
    override val placeNotConfigured = "Lieu non configuré"

    override val news = "Actualités"
    override fun minutesAgo(n: Long) = "$n min"
    override fun hoursAgo(n: Long) = "$n h"
    override fun daysAgo(n: Long) = "$n j"
    override val noRssFeed = "Aucun flux RSS configuré"
    override val emptyResponse = "Réponse vide"

    override val music = "Musique"
    override val nothingPlaying = "Rien en lecture"
    override val paused = "En pause"
    override val listening = "En écoute"
    override val macNotConfigured = "Mac non configuré"
    override val macUnreachable = "Mac injoignable"

    override val clock = "Horloge"
    override val nextEvent = "Prochain évènement"
    override val nextEvents = "Prochains évènements"
    override val agenda = "Agenda"
    override val currentWeather = "Météo actuelle"
    override val hourlyForecast = "Prévisions horaires"
    override val dailyForecast = "Prévisions jours"
    override val sizeSmall = "Petit"
    override val sizeMedium = "Moyen"
    override val sizeLarge = "Grand"
    override val sizeHuge = "Plein écran"

    override val pageHome = "Accueil"
    override val pageWeather = "Météo"
    override val defaultFeedTitle = "Le Monde · À la une"

    override val tabGeneral = "Général"
    override val tabPages = "Pages"
    override val icalLabel = "Calendriers iCal supplémentaires (optionnel), un par ligne"
    override val icalHelp = "Les agendas synchronisés sur ce téléphone sont lus automatiquement. Ajoutez ici d'autres agendas via leur adresse iCal."
    override val cityLabel = "Ville (météo)"
    override val search = "Chercher"
    override val searching = "Recherche…"
    override val noResult = "Aucun résultat"
    override fun error(message: String?) = "Erreur : $message"
    override fun currentPlace(label: String) = "Actuel : $label"
    override val defaultRssLabel = "Flux RSS par défaut"
    override val macLabel = "Mac — musique en cours (ex. 192.168.1.40:8765)"
    override val nightStartLabel = "Nuit : début (h)"
    override val nightEndLabel = "Nuit : fin (h)"
    override val nightHelp = "La luminosité baisse au minimum la nuit. Maintenir le doigt immobile ~1 s sur le tableau pour revenir ici."

    override val untitledPage = "Sans titre"
    override val addPage = "＋ Page"
    override fun pageNumber(n: Int) = "Page $n"
    override val rename = "Renommer"
    override val reset = "Réinitialiser"
    override val noPagesEditor = "Aucune page. Touchez « ＋ Page » pour commencer."
    override val editorHint = "Touchez une case vide pour ajouter un widget, ou un widget pour le modifier."
    override val resizeHint = "Étirez le coin ↘ pour changer la taille."
    override val move = "Déplacer"
    override val tapHighlightedCell = "Touchez une case en surbrillance."
    override val addRssFeed = "Ajouter un flux RSS"
    override val renamePage = "Renommer la page"
    override fun deletePageConfirm(name: String) = "Supprimer la page « $name » et ses widgets ?"
    override val resetConfirm = "Remplacer toutes les pages par la disposition par défaut ?"
    override val addWidget = "Ajouter un widget"
    override val addedSmallHint = "Ajouté en 1×1 : étirez ensuite son coin ↘ pour l'agrandir."
    override val eventCount = "Nombre d'évènements"
    override val feedUrlLabel = "Adresse du flux (URL)"
    override val feedTitleLabel = "Titre (optionnel)"
    override val feedsInUse = "Flux déjà utilisés"
    override val feedFallbackHint = "Sans adresse, le widget suit le flux RSS des réglages généraux."
    override val nameLabel = "Nom"
}
