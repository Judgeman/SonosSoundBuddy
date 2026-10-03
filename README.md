# SoundBuddy

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
  - Aktuelle Wiedergabe über den ganzen Bildschirm, ohne Scrollen: Das Cover
    wächst mit dem freien Platz, darunter Titel und Interpret,
    Fortschrittsbalken mit Position und Dauer („Live“ bei Radio) und klein
    die Lautstärke in Prozent.
  - Große Knöpfe für vorherigen Track, Play/Pause und nächsten Track.
    Skip-Knöpfe sind ausgegraut, wenn die Quelle es nicht erlaubt.
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

Die Wiedergabe wird alle 5 s abgefragt, solange die App im Vordergrund ist;
dazwischen läuft die Position lokal weiter. Das Cover wird aus `imageUrl`
oder der `images`-Liste des Tracks bzw. Containers gelesen.

## Datenbank

Lokale Room-Datenbank `sound_buddy.db` (`AppDatabase.kt`, Version 2):

- `speaker_config` — playerId, Name, freigegeben, Icon-Schlüssel,
  maximale Lautstärke (Spalte seit Version 2, Migration `MIGRATION_1_2`)
- `app_setting` — Key-Value: Passwort-Hash/-Salt, Passwortschutz an/aus,
  zuletzt gewählter Speaker

## Projektstruktur

Alle Quellen liegen in `app/src/main/java/de/paul/sonoscontrol/`:

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Navigation Home/Settings, Theme |
| `MainViewModel.kt` | Zustand, Speaker-Auswahl, Polling, Befehle, Passwort |
| `HomeScreen.kt` | Homescreen: Dropdown, Cover, Fortschritt, Knöpfe |
| `VolumeBar.kt` | Stufen-Lautstärke-Leiste |
| `CoverColors.kt` | Farben aus dem Cover, Cover-Theme, Statusleiste |
| `SettingsScreen.kt` | Einstellungen, Icon-Auswahl, Passwort-Dialog |
| `SpeakerIcons.kt`, `CharacterIcons.kt` | Icon-Katalog, Einhorn und Pikachu |
| `SonosApiClient.kt`, `SonosModels.kt` | Sonos-API inkl. Token-Erneuerung |
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
