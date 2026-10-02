# SonosSpeakers

Native Android-App (Kotlin, Jetpack Compose), die sich per OAuth2 gegen die
offizielle Sonos Control API anmeldet und die vorhandenen Speaker im
Haushalt anzeigt. Sonst nichts — bewusst minimal als Ausgangsbasis.

## Voraussetzungen

- Android Studio (aktuelle Version), Android SDK 35
- Ein deployter `sonos-relay`-Worker (siehe Schwesterprojekt) mit fester
  HTTPS-URL
- Eine Integration auf https://developer.sonos.com mit Client-ID + Secret

## Konfiguration vor dem ersten Build

In `app/src/main/java/de/paul/sonoscontrol/SonosConfig.kt`:

```kotlin
const val CLIENT_ID = "..."                 // aus dem Sonos Developer Portal
const val WORKER_CALLBACK_URL = "https://sonos-relay.<dein-subdomain>.workers.dev/callback"
```

Bei Sonos muss als Redirect-URI exakt `WORKER_CALLBACK_URL` hinterlegt sein.

## Ablauf in der App

1. Start → "Mit Sonos anmelden" → öffnet Custom Tab mit der Sonos-Login-Seite
2. Nutzer loggt sich bei Sonos ein und bestätigt den Zugriff
3. Sonos redirectet zum Cloudflare Worker → Worker tauscht Code gegen Token
   → Worker redirectet zu `sonoscontrol://callback?access_token=...`
4. Android fängt das über den Intent-Filter in `AndroidManifest.xml` ab,
   `MainActivity.handleIntent()` reicht die Uri an `MainViewModel` weiter
5. Tokens werden verschlüsselt in `TokenStore` (EncryptedSharedPreferences)
   abgelegt
6. App ruft `GET /households` und `GET /households/{id}/groups` auf und
   zeigt die Namen aller gefundenen Player an

## Homescreen

- Oben ein großes Dropdown mit Icon + Name des gewählten Speakers.
- Ist in den Settings nur **ein** Speaker freigegeben, ist er automatisch
  gewählt (kein Dropdown-Pfeil). Ansonsten wird beim Start der zuletzt
  gewählte Speaker wiederhergestellt.
- Darunter die aktuelle Wiedergabe über den restlichen Bildschirm (kein
  Scrollen): Das Cover wächst mit dem freien Platz, darunter Titel +
  Interpret, Fortschrittsbalken mit Position, Lautstärke in Prozent und
  Dauer sowie große Knöpfe für vorherigen Track, Play/Pause und nächsten
  Track (`POST /groups/{id}/playback/togglePlayPause`, `skipToPreviousTrack`,
  `skipToNextTrack`). Skip-Knöpfe sind ausgegraut, wenn die Quelle es nicht
  erlaubt (z. B. Radio).
- Rechts neben dem Cover eine senkrechte Lautstärke-Leiste mit 10 großen
  Stufen (nach oben breiter werdend): Tippen auf eine Stufe oder Ziehen
  setzt die Lautstärke, + / − gehen eine Stufe weiter
  (`POST /players/{id}/playerVolume`). Die oberste Stufe ist die in den
  Settings festgelegte Maximal-Lautstärke des Speakers. Wird die Lautstärke
  woanders (Sonos-App, Tasten am Speaker) höher gestellt, regelt die App sie
  bei der nächsten Abfrage wieder auf das Maximum herunter.
- Der Hintergrund passt sich dem Cover an (wie bei Apple Music): Aus dem
  Cover werden per `androidx.palette` Farben extrahiert, daraus entsteht ein
  dunkler Verlauf mit weißer Schrift und einer hellen Akzentfarbe für
  Knöpfe und Fortschrittsbalken. Beim Track-Wechsel blenden die Farben
  sanft über. Die App zeichnet dafür bis unter die Statusleiste
  (Edge-to-Edge).
- Die Daten kommen aus `GET /households/{id}/groups` (Gruppe des Players),
  `GET /groups/{id}/playback`, `GET /groups/{id}/playbackMetadata` und
  `GET /players/{id}/playerVolume`. Abgefragt wird alle 5 s, solange die App
  im Vordergrund ist; dazwischen läuft die Position lokal weiter.

## Einstellungen

Erreichbar über das Drei-Punkte-Menü oben rechts.

- **Passwortschutz:** Ist ein Passwort hinterlegt und „Einstellungen nur mit
  Passwort öffnen“ aktiv, wird beim Öffnen danach gefragt. Ohne Passwort sind
  die Einstellungen frei erreichbar und es kann dort eins gesetzt werden
  (aktiviert den Schutz automatisch). Das Passwort wird nur als
  PBKDF2-Hash mit Salt gespeichert.
- **Speaker:** Pro Speaker ein Schalter, ob er auf dem Homescreen auswählbar
  ist, ein Regler für die maximale Lautstärke (5–100 % in 5er-Schritten)
  und ein frei wählbares Icon (Material Symbols Rounded, z. B. Küche,
  Wohnzimmer, Kinderzimmer, Rakete, Teddy …). Beim ersten Abgleich sind alle
  Speaker freigegeben, später neu gefundene Speaker müssen erst freigegeben
  werden.
- **Abmelden** vom Sonos-Konto.

## Datenbank

Lokale Room-Datenbank `sound_buddy.db` (`AppDatabase.kt`):

- `speaker_config` — playerId, Name, freigegeben, Icon-Schlüssel,
  maximale Lautstärke (seit DB-Version 2, Migration in `AppDatabase.kt`)
- `app_setting` — Key-Value: Passwort-Hash/-Salt, Passwortschutz an/aus,
  zuletzt gewählter Speaker

## Anmeldung erneuern

Sonos-Access-Tokens laufen nach 24 h ab. `SonosApiClient` erneuert sie
selbstständig über den Worker (`POST /refresh`, siehe SonosSoundBuddyRelay),
weil dafür das Client-Secret nötig ist, das nicht in die App gehört:

- vorab, wenn die Ablaufzeit (`expires_in`) bekannt ist und in < 5 min erreicht wird
- sonst bei einer 401-Antwort — danach wird die Anfrage einmal wiederholt
- parallele Anfragen lösen nur einen Refresh aus
- lehnt Sonos den Refresh-Token ab (Zugriff widerrufen o.ä.), zeigt die App
  den Login mit einem Hinweis; bei Netz- oder Worker-Fehlern bleibt die
  Anmeldung erhalten und es wird beim nächsten Öffnen erneut versucht

## Noch nicht enthalten (bewusst, für den nächsten Schritt)

- Mehrere Households

## Bekannte Stolpersteine

- `minSdk = 26` — Custom Tabs + EncryptedSharedPreferences setzen das
  faktisch voraus.
- Falls `HorizontalDivider` in deiner Compose-BOM-Version anders heißt: BOM
  in `app/build.gradle.kts` aktualisieren.
- Der Worker MUSS exakt dieselbe `redirect_uri` in seiner eigenen
  `wrangler.toml` verwenden wie die App beim Auth-Request sendet — sonst
  lehnt Sonos den Token-Exchange ab.
