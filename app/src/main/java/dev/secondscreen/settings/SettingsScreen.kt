package dev.secondscreen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import dev.secondscreen.widgets.PageSpec
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.data.weather.Geocoder
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    initial: Settings,
    initialPages: List<PageSpec>,
    onSave: (Settings, List<PageSpec>) -> Unit,
    onCancel: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.General) }
    var pages by remember { mutableStateOf(initialPages) }
    var ics by remember { mutableStateOf(initial.icsUrls.joinToString("\n")) }
    var cityQuery by remember { mutableStateOf(initial.city) }
    var place by remember { mutableStateOf(Geocoder.Place(initial.city, initial.lat, initial.lon)) }
    var geoMsg by remember { mutableStateOf<String?>(null) }
    var rss by remember { mutableStateOf(initial.rssUrl) }
    var nightStart by remember { mutableStateOf(initial.nightStartHour.toString()) }
    var nightEnd by remember { mutableStateOf(initial.nightEndHour.toString()) }
    var macHost by remember { mutableStateOf(initial.macHost) }
    val scope = rememberCoroutineScope()

    fun searchCity() {
        geoMsg = "Recherche…"
        scope.launch {
            geoMsg = try {
                val p = Geocoder.search(cityQuery)
                if (p == null) "Aucun résultat" else {
                    place = p
                    "${p.label} (${"%.3f".format(p.lat)}, ${"%.3f".format(p.lon)})"
                }
            } catch (e: Exception) {
                "Erreur : ${e.message}"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .padding(horizontal = 32.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Réglages", fontSize = 22.sp, color = Ink.Fg)
            Spacer(Modifier.width(24.dp))
            SettingsTab.entries.forEach { t ->
                TabButton(t.label, selected = t == tab) { tab = t }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Annuler", color = Ink.Dim) }
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        icsUrls = ics.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        city = place.label.substringBefore(","),
                        lat = place.lat,
                        lon = place.lon,
                        rssUrl = rss.trim(),
                        nightStartHour = nightStart.toIntOrNull()?.coerceIn(0, 23) ?: initial.nightStartHour,
                        nightEndHour = nightEnd.toIntOrNull()?.coerceIn(0, 23) ?: initial.nightEndHour,
                        macHost = macHost.trim(),
                    ),
                    pages,
                )
            }) { Text("Enregistrer", color = Ink.Fg) }
        }
        Spacer(Modifier.height(8.dp))

        when (tab) {
            SettingsTab.Pages -> LayoutEditor(pages, onChange = { pages = it }, modifier = Modifier.weight(1f))
            SettingsTab.General -> Column(
                Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Field(
                    label = "Calendriers iCal supplémentaires (optionnel), un par ligne",
                    value = ics,
                    onChange = { ics = it },
                    singleLine = false,
                    keyboardType = KeyboardType.Uri,
                )
                Text(
                    "Les agendas synchronisés sur ce téléphone sont lus automatiquement. Ajoutez ici d'autres agendas via leur adresse iCal.",
                    style = Type.bodySmall,
                    color = Ink.Faint,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Ville (météo)", cityQuery, { cityQuery = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = ::searchCity) { Text("Chercher", color = Ink.Fg) }
                }
                Text(geoMsg ?: "Actuel : ${place.label}", style = Type.bodySmall, color = Ink.Faint)

                Field("Flux RSS", rss, { rss = it }, keyboardType = KeyboardType.Uri)

                Field("Mac — musique en cours (ex. 192.168.1.40:8765)", macHost, { macHost = it }, keyboardType = KeyboardType.Uri)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Nuit : début (h)", nightStart, { nightStart = it }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    Field("Nuit : fin (h)", nightEnd, { nightEnd = it }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
                Text(
                    "La luminosité baisse au minimum la nuit. Maintenir le doigt immobile ~1 s sur le tableau pour revenir ici.",
                    style = Type.bodySmall,
                    color = Ink.Faint,
                )
            }
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink.Fg,
            unfocusedTextColor = Ink.Fg,
            focusedBorderColor = Ink.Dim,
            unfocusedBorderColor = Ink.Line,
            focusedLabelColor = Ink.Dim,
            unfocusedLabelColor = Ink.Faint,
            cursorColor = Ink.Fg,
        ),
    )
}

private enum class SettingsTab(val label: String) { General("Général"), Pages("Pages") }

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) Ink.Fg else Ink.Faint,
            fontSize = 15.sp,
            textDecoration = if (selected) TextDecoration.Underline else null,
        )
    }
}
