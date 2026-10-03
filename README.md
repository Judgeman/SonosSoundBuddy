# Sound Buddy

Native Android-App (Kotlin, Jetpack Compose) zum kindgerechten Steuern von
Sonos-Speakern über die offizielle Sonos Control API. Gedacht für ein
Tablet, das von Kindern bedient wird: große Knöpfe, bunte Icons, eine
Lautstärke-Leiste mit Obergrenze und passwortgeschützte Einstellungen für
die Eltern.

## Zugehöriges Projekt: Relay

Die App braucht den Cloudflare-Worker
**[SonosSoundBuddyRelay](https://github.com/Judgeman/SonosSoundBuddyRelay)**.
Er hält das Sonos-Client-Secret, das nicht in die App gehört, und übernimmt
alle Token-Anfragen bei Sonos:

- `GET /callback` — tauscht beim Login den Authorization-Code gegen Tokens
  und leitet zurück in die App (`sonoscontrol://callback?…`)
- `POST /refresh` — erneuert abgelaufene Access-Tokens

Setup, Deploy (auch ohne Wrangler über das Cloudflare-Dashboard) und Tests
sind im README des Relay-Projekts beschrieben. Die Worker-URL wird in der
App in `SonosConfig.kt` eingetragen (siehe unten).

## Funktionen

- **Homescreen**
  - Großes Dropdown oben mit Icon und Namen des gewählten Speakers.
  - Ist in den Settings nur **ein** Speaker freigegeben, ist er immer
    automatisch gewählt (ohne Dropdown-Pfeil). Ansonsten wird beim Start
    der zuletzt gewählte Speaker wiederhergestellt.
  - Rechts daneben die **Profil-Auswahl**, die nur das Tier-Icon des
    gewählten Kinder-Profils zeigt. Gleiche Logik wie bei den Speakern: ein
    aktives Profil ist fest gewählt, bei mehreren wird das zuletzt gewählte
    wiederhergestellt.
  - Aktuelle Wiedergabe über den ganzen Bildschirm, ohne Scrollen: Das Cover
    wächst mit dem freien Platz, darunter Titel und Interpret,
    Fortschrittsbalken mit Position und Dauer („Live“ bei Radio) und klein
    die Lautstärke in Prozent.
  - Große Knöpfe für vorherigen Track, Play/Pause und nächsten Track.
    Skip-Knöpfe sind ausgegraut, wenn die Quelle es nicht erlaubt.
  - Ganz unten der Knopf **„Musik aussuchen“**: öffnet ein großes Popup mit
    der Musikauswahl des Profils — erst die Kategorien als große
    Bild-Kacheln (bei nur einer Kategorie direkt deren Musik), dann die
    Cover-Kacheln. Ein Tipp spielt die Musik sofort auf dem gewählten
    Speaker (die Warteschlange wird ersetzt). Steht die Kategorie auf
    „Kinder entscheiden“, fragen zwei große Bild-Knöpfe: „Der Reihe nach“
    (Entenmama mit Küken in einer Reihe) oder „Durcheinander“ (rollende
    Würfel) — gezeichnet in `OrderIcons.kt`, damit auch Kinder ohne Lesen
    wählen können.
  - Senkrechte **Lautstärke-Leiste** rechts neben dem Cover mit 10 großen,
    nach oben breiter werdenden Stufen: Tippen oder Ziehen setzt die
    Lautstärke, + / − gehen eine Stufe weiter. Die oberste Stufe ist die
    Maximal-Lautstärke des Speakers.
  - Der **Hintergrund passt sich dem Cover an** (à la Apple Music): Farben
    werden per `androidx.palette` aus dem Cover extrahiert, daraus entsteht
    ein dunkler Verlauf mit weißer Schrift und heller Akzentfarbe. Beim
    Track-Wechsel blenden die Farben sanft über. Die App zeichnet dafür bis
    unter die Statusleiste (Edge-to-Edge).
- **Einstellungen** (Drei-Punkte-Menü oben rechts)
  - **Passwortschutz:** Ist ein Passwort hinterlegt und „Einstellungen nur
    mit Passwort öffnen“ aktiv, wird beim Öffnen danach gefragt. Ohne
    Passwort sind die Einstellungen frei erreichbar und es kann dort eins
    gesetzt werden (aktiviert den Schutz automatisch). Gespeichert wird nur
    ein PBKDF2-Hash mit Salt.
  - **Speaker:** pro Speaker ein Schalter „auf dem Homescreen auswählbar“,
    ein Regler für die **maximale Lautstärke** (5–100 % in 5er-Schritten)
    und ein frei wählbares **Icon**. Beim ersten Abgleich sind alle Speaker
    freigegeben, später neu gefundene müssen erst freigegeben werden.
  - **Kinder-Profile:** Profile anlegen, pro Profil ein Schalter „auf diesem
    Tablet aktiv“. Ein Tipp öffnet die Profil-Seite mit Name, **Icon**
    (16 gezeichnete Tiere, Einhorn, Pikachu) und der **Musikauswahl**:
    Kategorien anlegen, umbenennen, sortieren und löschen. Pro Kategorie:
    - ein **Bild**: eins der Speaker-Icons oder Tiere oder ein **eigenes
      Foto** (Android-Fotoauswahl, ohne Berechtigung; wird verkleinert in den
      App-Speicher kopiert). Ohne Auswahl ein Musik-Icon.
    - pro Musik-Eintrag ebenfalls ein eigenes Bild (Tipp auf das Cover) —
      gedacht vor allem für Sonos-Playlisten, die von Sonos kein Cover
      bekommen. „Cover von Sonos“ stellt das Original wieder her.
    - die **Abspielreihenfolge**: „Der Reihe nach“, „Zufällig“ oder „Kinder
      entscheiden“. Gilt für Playlisten und Alben; Songs und Radio laufen
      einfach los. „Musik
    hinzufügen“ öffnet den **Sonos-Katalog** (alle Sonos-Favoriten und
    Sonos-Playlisten) mit Suche und Filter nach Songs, Playlisten, Alben und
    Radio; ein Tipp nimmt einen Eintrag in die Kategorie auf oder wieder
    heraus. Bei Sonos-Playlisten lässt sich die Titelliste ansehen.
  - **Abmelden** vom Sonos-Konto.
- **Lautstärke-Obergrenze:** Wird ein Speaker woanders (Sonos-App, Tasten am
  Gerät) über sein Maximum gestellt, regelt die App ihn bei der nächsten
  Abfrage wieder herunter — solange die App im Vordergrund läuft.
- **Anmeldung bleibt erhalten:** Abgelaufene Access-Tokens werden
  automatisch über den Relay-Worker erneuert (siehe unten).

### Icons

32 Material Symbols (Rounded) für Räume und verspielte Motive (Küche,
Wohnzimmer, Kinderzimmer, Rakete, Roboter, Stern, Eis …) sowie zwei selbst
gezeichnete, mehrfarbige Figuren in `CharacterIcons.kt`: **Einhorn** und
**Pikachu**. Beim ersten Abgleich schlägt die App anhand des Raumnamens ein
passendes Icon vor.

Für die Kinder-Profile gibt es eigene Icons (`ProfileIcons.kt`): 16 selbst
gezeichnete Tiergesichter in `AnimalIcons.kt` (Katze, Hund, Bär, Panda,
Fuchs, Frosch, Löwe, Schwein, Maus, Hase, Eule, Pinguin, Affe, Koala,
Küken, Marienkäfer) sowie Einhorn und Pikachu.

> Pikachu ist eine geschützte Figur von Nintendo/The Pokémon Company. Für
> den privaten Gebrauch ok — vor einer Veröffentlichung im Play Store das
> Icon entfernen.

## Voraussetzungen

- Android Studio (aktuelle Version), Android SDK 35
- Der deployte Relay-Worker aus
  [SonosSoundBuddyRelay](https://github.com/Judgeman/SonosSoundBuddyRelay)
  mit fester HTTPS-URL, inkl. Endpunkt `POST /refresh`
- Eine Integration auf https://developer.sonos.com mit Client-ID + Secret

## Konfiguration vor dem ersten Build

In `app/src/main/java/de/paul/sonoscontrol/SonosConfig.kt`:

```kotlin
const val CLIENT_ID = "..."                 // aus dem Sonos Developer Portal
const val WORKER_CALLBACK_URL = "https://sonos-relay.<dein-subdomain>.workers.dev/callback"
```

Die Refresh-URL (`…/refresh`) wird automatisch aus `WORKER_CALLBACK_URL`
abgeleitet. Bei Sonos muss als Redirect-URI exakt `WORKER_CALLBACK_URL`
hinterlegt sein.

## Anmeldung

1. „Mit Sonos anmelden“ öffnet einen Custom Tab mit der Sonos-Login-Seite.
2. Nach dem Login redirectet Sonos zum Worker, der den Code gegen Tokens
   tauscht und zu
   `sonoscontrol://callback?access_token=…&refresh_token=…&expires_in=…`
   weiterleitet.
3. Android fängt das über den Intent-Filter in `AndroidManifest.xml` ab,
   `MainActivity.handleIntent()` reicht die Uri an `MainViewModel` weiter
   (inkl. Prüfung des `state`-Parameters gegen CSRF).
4. Tokens und Ablaufzeit liegen verschlüsselt in `TokenStore`
   (EncryptedSharedPreferences).

### Anmeldung erneuern

Sonos-Access-Tokens laufen nach 24 h ab. Zum Erneuern braucht Sonos das
Client-Secret, das nicht in die App gehört — deshalb übernimmt das der
Worker (`POST /refresh`). `SonosApiClient` erneuert selbstständig:

- vorab, wenn die gespeicherte Ablaufzeit in weniger als 5 min erreicht ist,
- sonst bei einer 401-Antwort — danach wird die Anfrage einmal wiederholt,
- bei parallelen Anfragen nur einmal.

Lehnt Sonos den Refresh-Token ab (Zugriff widerrufen o. ä.), zeigt die App
den Login mit dem Hinweis „Die Anmeldung bei Sonos ist abgelaufen“. Bei
Netz- oder Worker-Fehlern bleibt die Anmeldung erhalten; beim nächsten
Öffnen versucht die App es still erneut. Im Logcat (Tag `SonosApi`) steht
nach einer erfolgreichen Erneuerung „Access-Token erneuert“.

## Verwendete Sonos-API-Aufrufe

| Zweck | Aufruf |
|---|---|
| Household | `GET /households` |
| Speaker und Gruppen | `GET /households/{id}/groups` |
| Status / Position | `GET /groups/{id}/playback` |
| Titel, Interpret, Cover | `GET /groups/{id}/playbackMetadata` |
| Lautstärke lesen / setzen | `GET` / `POST /players/{id}/playerVolume` |
| Play/Pause, Skip | `POST /groups/{id}/playback/togglePlayPause`, `skipToNextTrack`, `skipToPreviousTrack` |
| Sonos-Favoriten / -Playlisten | `GET /households/{id}/favorites`, `GET /households/{id}/playlists` |
| Titel einer Playlist | `POST /households/{id}/playlists/getPlaylist` |
| Musik abspielen | `POST /groups/{id}/favorites`, `POST /groups/{id}/playlists` (`action: REPLACE`) |
| Zufall an/aus, Start | `POST /groups/{id}/playback/playMode` (`shuffle`), `POST /groups/{id}/playback/play` |

Die Wiedergabe wird alle 5 s abgefragt, solange die App im Vordergrund ist;
dazwischen läuft die Position lokal weiter. Das Cover wird aus `imageUrl`
oder der `images`-Liste des Tracks bzw. Containers gelesen.

### Musik-Katalog

Die Control API kann keine Musikdienste durchsuchen, sondern nur
**Sonos-Favoriten** und **Sonos-Playlisten** abspielen. Das ist der Katalog
für die Musikauswahl. Einzelne Songs, Alben, Spotify-Playlisten oder
Radiosender kommen also über „Zu Sonos-Favoriten hinzufügen“ in der
Sonos-App in die Auswahl. Einzelne Titel *aus* einer Sonos-Playlist lassen
sich nicht gezielt starten.

**Cover aus dem Heimnetz:** Die Cloud-API liefert für Sonos-Playlisten nie
ein Bild und für manche Favoriten keins, das sich laden lässt. Deshalb
fragt die App beim Laden des Katalogs (und still beim App-Start) direkt
einen Speaker im WLAN — so wie die Sonos-App (`LocalSonosClient.kt`):
Speaker per mDNS (`_sonos._tcp`) finden, dann UPnP-„Browse“ auf
`http://<ip>:1400/MediaServer/ContentDirectory/Control` für `FV:2`
(Favoriten) und `SQ:` (Sonos-Playlisten; ohne eigenes Bild das Cover des
ersten Titels). Zugeordnet wird über die Id (`13` ↔ `FV:2/13` bzw.
`SQ:13`), sonst über den Namen. Diese Cover haben Vorrang; das Cover aus
der Cloud bleibt als Ersatz gespeichert (mehrere Adressen, durch
Zeilenumbruch getrennt) — lädt die erste nicht, probiert die App die
nächste. Apple-Music-Vorlagen wie `{w}x{h}{c}.{f}` werden zu
`600x600bb.jpg` ausgefüllt. Logcat-Tag:
`SonosLocal`.

**Abgelaufene Cover (Apple Music):** Apple liefert Cover als signierte
Adressen, die nur 24 h gültig sind (`X-Amz-Date` + `X-Amz-Expires`). Sonos
speichert die Adresse beim Anlegen des Favoriten und gibt danach immer
dieselbe, längst abgelaufene aus (Antwort: 400). Deshalb:
- abgelaufene Adressen werden erkannt und übersprungen,
- der Speaker liefert über `/getaa?u=<Abspiel-Adresse>` ein frisches Cover
  (die Abspiel-Adresse steht im `res` des lokalen Favoriten),
- Cover werden beim Hinzufügen zur Auswahl **auf dem Tablet gespeichert**
  (`custom_images/cover-item-…jpg`),
- beim Abspielen übernimmt die App das Cover der Playlist bzw. des Albums
  aus der Wiedergabe (`container` in `playbackMetadata`, frische Adresse)
  und ersetzt damit ein vorher gespeichertes Cover — der Speaker liefert
  über `/getaa` bei Apple-Music-Playlisten nur das Cover eines Titels.
  Solche Dateien tragen `-container` im Namen und werden nicht mehr
  überschrieben. Gibt es kein Playlist-Cover, dient das des Titels als
  Ersatz, solange noch gar keins gespeichert ist.
Nicht mehr benutzte Bilder räumt die App beim Start auf.

**Fehlersuche:** Der ⓘ-Knopf an jedem Eintrag im Katalog zeigt Quelle,
Id, woher das Cover kommt, jede bekannte Cover-URL mit Vorschau und ob sie
sich laden lässt (sonst mit Fehlermeldung) sowie die Rohdaten von Sonos.
„Kopieren“ legt alles als Text in die Zwischenablage.

Darüber hinaus sucht die App im ganzen Favoriten (`imageUrl`, `images`, auch an
`resource` oder verschachtelt, nicht aber das Logo des Musikdienstes) und
ersetzt Größen-Platzhalter wie `{w}x{h}`. Findet sie keins, steht im Logcat
(Tag `SonosApi`) „Kein Cover im Favoriten …“ mit dem Roh-JSON; Ladefehler
stehen unter Tag `Cover`. Gespeicherte Einträge bekommen ihr Cover beim
Öffnen des Katalogs und beim App-Start nachgetragen; hat ein Eintrag gar
keins, übernimmt die App beim ersten Abspielen das Cover der Wiedergabe.

Gespeichert werden Id, Name und Cover. Sonos vergibt die Ids selbst. Hat
sich die Id inzwischen geändert (Favorit gelöscht und neu angelegt), sucht
die App beim Abspielen über den Namen.

Für die Reihenfolge lädt die App die Musik erst, ohne sie zu starten, setzt
dann den Zufallsmodus ausdrücklich an oder aus (sonst bliebe er vom letzten
Mal an) und startet die Wiedergabe. Im Zufallsmodus springt sie vorher
einen Titel weiter, damit nicht immer der erste Titel zuerst läuft. Radio
wird ohne diese Schritte direkt gestartet.

## Datenbank

Lokale Room-Datenbank `sound_buddy.db` (`AppDatabase.kt`, Version 6):

- `speaker_config` — playerId, Name, freigegeben, Icon-Schlüssel,
  maximale Lautstärke (Spalte seit Version 2, Migration `MIGRATION_1_2`)
- `app_setting` — Key-Value: Passwort-Hash/-Salt, Passwortschutz an/aus,
  zuletzt gewählter Speaker und zuletzt gewähltes Profil
- `child_profile` — Name, Icon-Schlüssel, auf diesem Tablet aktiv
  (seit Version 4, Migration `MIGRATION_3_4`, ebenso die beiden folgenden)
- `music_category` — Profil, Name, Position, Bild (`icon:…`, `animal:…`,
  `file:…` oder leer = Standard-Icon), Abspielreihenfolge
  (Bild und Reihenfolge seit Version 5, Migration `MIGRATION_4_5`)
- `music_item` — Kategorie, Quelle (Favorit/Playlist), Sonos-Id, Name,
  Beschreibung, Cover-URL, Art (Song, Album, …), Position, eigenes Bild
  (seit Version 6, Migration `MIGRATION_5_6`; sie setzt außerdem früher als
  Kategorie-Bild gewählte Cover zurück)

## Projektstruktur

Alle Quellen liegen in `app/src/main/java/de/paul/sonoscontrol/`:

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Navigation Home/Settings/Profil/Katalog, Theme |
| `MainViewModel.kt` | Zustand, Speaker- und Profil-Auswahl, Polling, Befehle, Katalog, Passwort |
| `HomeScreen.kt` | Homescreen: Dropdown, Cover, Fortschritt, Knöpfe |
| `MusicPicker.kt` | Profil-Dropdown, „Musik aussuchen“-Knopf und -Popup |
| `VolumeBar.kt` | Stufen-Lautstärke-Leiste |
| `CoverColors.kt` | Farben aus dem Cover, Cover-Theme, Statusleiste |
| `SettingsScreen.kt` | Einstellungen, Icon-Auswahl, Passwort-Dialog |
| `ProfileSettingsScreens.kt` | Profil-Liste, Profil-Seite mit Kategorien, Katalog-Auswahl |
| `MusicCatalog.kt`, `MusicCover.kt` | Katalog-Einträge, Musik-Typen, Abspielreihenfolge, eigene Bilder, Cover-Kachel |
| `CustomImageStore.kt` | Eigene Bilder für Kategorien und Einträge importieren (verkleinern, drehen) und löschen |
| `SpeakerIcons.kt`, `CharacterIcons.kt` | Icon-Katalog, Einhorn und Pikachu |
| `ProfileIcons.kt`, `AnimalIcons.kt` | Profil-Icons, gezeichnete Tiere |
| `SonosApiClient.kt`, `SonosModels.kt` | Sonos-API inkl. Token-Erneuerung |
| `LocalSonosClient.kt` | Cover direkt vom Speaker im Heimnetz (mDNS + UPnP) |
| `SonosAuthManager.kt`, `TokenStore.kt`, `SonosConfig.kt` | Login und Tokens |
| `AppDatabase.kt`, `SettingsRepository.kt`, `PasswordHasher.kt` | Datenbank und Einstellungen |

Wichtige Bibliotheken: Compose Material 3 und `material-icons-extended`,
Room 2.6 (über KSP), Coil 2 für Cover, `androidx.palette`, OkHttp,
kotlinx.serialization.

## Bekannte Stolpersteine

- `minSdk = 26` — Custom Tabs + EncryptedSharedPreferences setzen das
  faktisch voraus.
- Klartext-HTTP ist erlaubt (`res/xml/network_security_config.xml`), weil
  Sonos Cover je nach Quelle (z. B. AirPlay) direkt vom Speaker im Heimnetz
  liefert (`http://<ip>:1400/getaa?…`). Die Sonos-API selbst läuft weiter
  über HTTPS.
- Die App nutzt ein eigenes Theme ohne ActionBar (`res/values/themes.xml`);
  Titel und Menü kommen aus der Compose-TopAppBar.
- Der Worker MUSS exakt dieselbe `redirect_uri` verwenden wie die App beim
  Auth-Request — sonst lehnt Sonos den Token-Exchange ab.
