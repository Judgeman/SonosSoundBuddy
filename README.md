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

## Noch nicht enthalten (bewusst, für den nächsten Schritt)

- Token-Refresh (aktuell: bei abgelaufenem Access-Token → Fehleranzeige →
  erneuter Login-Flow)
- Playback-Steuerung (Play/Pause/Volume/Cover) — kommt als nächster Schritt
  auf Basis der bereits vorhandenen Group-IDs
- Mehrere Households

## Bekannte Stolpersteine

- `minSdk = 26` — Custom Tabs + EncryptedSharedPreferences setzen das
  faktisch voraus.
- Falls `HorizontalDivider` in deiner Compose-BOM-Version anders heißt: BOM
  in `app/build.gradle.kts` aktualisieren.
- Der Worker MUSS exakt dieselbe `redirect_uri` in seiner eigenen
  `wrangler.toml` verwenden wie die App beim Auth-Request sendet — sonst
  lehnt Sonos den Token-Exchange ab.
