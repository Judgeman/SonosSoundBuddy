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
- Darunter die aktuelle Wiedergabe: großes Cover, Status (Läuft / Pausiert),
  Titel + Interpret, Fortschrittsbalken mit Position und Dauer, klein die
  Lautstärke in Prozent.
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
  ist, und ein frei wählbares Icon (Material Symbols Rounded, z. B. Küche,
  Wohnzimmer, Kinderzimmer, Rakete, Teddy …). Beim ersten Abgleich sind alle
  Speaker freigegeben, später neu gefundene Speaker müssen erst freigegeben
  werden.
- **Abmelden** vom Sonos-Konto.

## Datenbank

Lokale Room-Datenbank `sound_buddy.db` (`AppDatabase.kt`):

- `speaker_config` — playerId, Name, freigegeben, Icon-Schlüssel
- `app_setting` — Key-Value: Passwort-Hash/-Salt, Passwortschutz an/aus,
  zuletzt gewählter Speaker

## Noch nicht enthalten (bewusst, für den nächsten Schritt)

- Token-Refresh (aktuell: bei abgelaufenem Access-Token → Fehleranzeige →
  erneuter Login-Flow)
- Playback-Steuerung (Play/Pause/Volume ändern)
- Mehrere Households

## Bekannte Stolpersteine

- `minSdk = 26` — Custom Tabs + EncryptedSharedPreferences setzen das
  faktisch voraus.
- Falls `HorizontalDivider` in deiner Compose-BOM-Version anders heißt: BOM
  in `app/build.gradle.kts` aktualisieren.
- Der Worker MUSS exakt dieselbe `redirect_uri` in seiner eigenen
  `wrangler.toml` verwenden wie die App beim Auth-Request sendet — sonst
  lehnt Sonos den Token-Exchange ab.
