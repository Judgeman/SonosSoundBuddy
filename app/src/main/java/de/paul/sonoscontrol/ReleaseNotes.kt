package de.paul.sonoscontrol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Eine Version der App mit ihren Neuerungen, nach Bereichen gruppiert. */
data class Release(
    val version: String,
    /** Erscheinungsdatum, so wie es angezeigt wird. */
    val date: String,
    val sections: List<ReleaseSection>
)

data class ReleaseSection(val title: String, val items: List<String>)

/**
 * Alle Versionen, die neueste zuerst. Für eine neue Version oben einen Eintrag
 * ergänzen und `versionName`/`versionCode` in `app/build.gradle.kts` hochzählen.
 */
val ReleaseHistory = listOf(
    Release(
        version = "1.0.0",
        date = "7. Oktober 2026",
        sections = listOf(
            ReleaseSection(
                "Homescreen",
                listOf(
                    "Speaker-Auswahl oben mit Icon und Namen. Ist nur ein Speaker freigegeben, ist er " +
                        "automatisch gewählt, sonst kommt beim Start der zuletzt gewählte zurück.",
                    "Profil-Auswahl daneben mit dem Tier des Kindes, nach derselben Logik.",
                    "Aktuelle Wiedergabe über den ganzen Bildschirm: großes Cover, Titel und Interpret, " +
                        "Fortschrittsbalken („Live“ bei Radio) und die Lautstärke in Prozent.",
                    "Große Knöpfe für zurück, Play/Pause und weiter – ausgegraut, wenn die Quelle kein " +
                        "Springen erlaubt.",
                    "Senkrechte Lautstärke-Leiste mit 10 großen Stufen bis zur Maximal-Lautstärke des " +
                        "Speakers: tippen, ziehen oder + / −.",
                    "Der Hintergrund nimmt die Farben des Covers an und blendet beim Titelwechsel sanft über.",
                    "Kleine Akku-Anzeige und App-Version oben rechts in der Kopfzeile. Am Strom grün mit " +
                        "Blitz, ab 20 % rot."
                )
            ),
            ReleaseSection(
                "Musik aussuchen",
                listOf(
                    "Der Knopf „Musik aussuchen“ zeigt die Musik des Profils: erst die Kategorien als " +
                        "große Bild-Kacheln, dann die Cover.",
                    "Ein Tipp spielt die Musik sofort auf dem gewählten Speaker. Solange sie startet, " +
                        "zeigt ein Popup Cover, Namen und Ladekreis.",
                    "Bei „Kinder entscheiden“ wählen die Kinder per Bild: „Der Reihe nach“ (Entenfamilie) " +
                        "oder „Durcheinander“ (Würfel)."
                )
            ),
            ReleaseSection(
                "Tiere und Figuren",
                listOf(
                    "Das Tier des Profils sitzt am Cover: Es tanzt mit bunten Noten zur Musik oder liest " +
                        "aus einem Bilderbuch vor, schläft bei Pause und hüpft beim Antippen.",
                    "Ein kleineres Tier läuft auf dem Fortschrittsbalken mit.",
                    "Tierbesuch: Alle 10–20 Minuten schaut ein Profil-Tier vorbei und winkt. Lange auf " +
                        "„SoundBuddy“ drücken holt sofort eins.",
                    "Geht etwas schief, erscheint der SoundBuddy – ein kleiner lila Lautsprecher mit " +
                        "ausgestecktem Stecker – statt einer nüchternen Fehlermeldung."
                )
            ),
            ReleaseSection(
                "Musikauswahl",
                listOf(
                    "Kategorien und Musik werden einmal zentral angelegt und den Profilen zugewiesen.",
                    "Reihenfolge von Kategorien und Musik per Drag & Drop oder mit Pfeilen ändern; langes " +
                        "Drücken auf einen Pfeil setzt ganz nach oben bzw. unten.",
                    "Pro Kategorie: Bild (gezeichnete Szene, Icon, Tier oder eigenes Foto), welche Profile " +
                        "sie sehen, Sortierung (manuell, alphabetisch oder nach Hinzufügen), " +
                        "Abspielreihenfolge, Tier am Cover (Tanzen oder Vorlesen) und wo neue Musik landet.",
                    "Eigene Bilder auch für einzelne Musik-Einträge, z. B. für Sonos-Playlisten ohne Cover.",
                    "Sonos-Katalog mit allen Favoriten und Playlisten: Suche (auch nach Künstler), Filter " +
                        "nach Art, Hinweis „Noch in keiner Kategorie“ samt Filter „Ohne Kategorie“ und " +
                        "Titelliste von Sonos-Playlisten."
                )
            ),
            ReleaseSection(
                "Speaker und Profile",
                listOf(
                    "Pro Speaker: auf dem Homescreen freigeben, Icon wählen und maximale Lautstärke " +
                        "(5–100 %) festlegen. Neu gefundene Speaker sind als „Neu“ markiert.",
                    "Wird ein Speaker woanders über sein Maximum gestellt, regelt die App ihn wieder herunter.",
                    "Kinder-Profile mit Namen und Tier-Icon (18 gezeichnete Tiere, Einhorn, Pikachu), " +
                        "pro Tablet aktivierbar."
                )
            ),
            ReleaseSection(
                "Mehrere Tablets",
                listOf(
                    "Den Stand eines Tablets auf andere übertragen: automatisch über die Cloud, im WLAN " +
                        "mit vierstelligem Code oder als ZIP-Datei.",
                    "Das empfangende Tablet wählt, was es übernimmt: Profile und Musik, " +
                        "Speaker-Einstellungen, Speaker-Freigabe und Passwort.",
                    "Die Daten bleiben je Sonos-Haushalt getrennt."
                )
            ),
            ReleaseSection(
                "Einstellungen und Sicherheit",
                listOf(
                    "Einstellungen mit Passwort schützen; gespeichert wird nur ein Hash.",
                    "Anmeldung über das Sonos-Konto; abgelaufene Zugänge werden automatisch erneuert.",
                    "Bei ausgeschaltetem Display läuft nichts im Hintergrund.",
                    "Versionsanzeige und diese Release Notes in den Einstellungen."
                )
            )
        )
    )
)

/** Unterseite der Settings: alle Versionen mit ihren Neuerungen, die neueste oben. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Release Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            ReleaseHistory.forEachIndexed { index, release ->
                item {
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ReleaseHeader(release)
                }
                release.sections.forEach { section ->
                    item { ReleaseSectionBlock(section) }
                }
            }
        }
    }
}

@Composable
private fun ReleaseHeader(release: Release) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Version ${release.version}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            // Laufen auf den Tablets verschiedene Versionen, sieht man so, welche diese ist
            if (release.version == BuildConfig.VERSION_NAME) {
                Spacer(modifier = Modifier.width(8.dp))
                InstalledBadge()
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            release.date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InstalledBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            "Installiert",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ReleaseSectionBlock(section: ReleaseSection) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        section.items.forEach { item ->
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
